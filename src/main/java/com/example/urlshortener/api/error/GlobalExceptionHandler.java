package com.example.urlshortener.api.error;

import com.example.urlshortener.api.dto.ErrorResponse;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps exceptions to the §5.1 envelope (URL-NFR-4.6). Responses never contain stack traces or
 * internal messages; unexpected errors are logged with the MDC {@code request_id} and answered with
 * a generic {@code INTERNAL_ERROR}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Detail key carrying the {@code Retry-After} value of a {@code RATE_LIMITED} error. */
  public static final String RETRY_AFTER_DETAIL = "retry_after_seconds";

  private static final String EXPIRES_AT_FIELD = "expires_at";

  /**
   * Expected failures raised by services and controllers.
   *
   * @param e the exception
   * @return envelope with the code's HTTP status
   */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
    ResponseEntity.BodyBuilder response = ResponseEntity.status(e.code().httpStatus());
    if (e.code() == ErrorCode.RATE_LIMITED
        && e.details() != null
        && e.details().get(RETRY_AFTER_DETAIL) instanceof Number seconds) {
      response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds.longValue()));
    }
    return response.body(ErrorResponse.of(e.code().name(), e.getMessage(), e.details()));
  }

  /**
   * URL-NFR-4.5: malformed JSON is 400 {@code MALFORMED_REQUEST}; well-formed JSON that does not
   * fit the schema (unknown field, wrong type) is 422 {@code VALIDATION_FAILED}.
   *
   * @param e the exception
   * @return envelope
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
    Throwable cause = e.getCause();
    if (cause instanceof UnrecognizedPropertyException unknown) {
      return envelope(
          ErrorCode.VALIDATION_FAILED,
          "Unknown field '" + unknown.getPropertyName() + "'.",
          Map.of("field", unknown.getPropertyName()));
    }
    if (cause instanceof JsonMappingException mapping) {
      String field = fieldPath(mapping);
      if (EXPIRES_AT_FIELD.equals(field)) {
        // URL-FR-4.2 / design §3.3: expires_at must be ISO-8601 with an offset
        return envelope(
            ErrorCode.INVALID_EXPIRY, "expires_at must be ISO-8601 with an offset.", null);
      }
      return envelope(
          ErrorCode.VALIDATION_FAILED,
          ErrorCode.VALIDATION_FAILED.defaultMessage(),
          Map.of("field", field));
    }
    return envelope(
        ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.defaultMessage(), null);
  }

  /**
   * Bean-validation failures on request bodies.
   *
   * @param e the exception
   * @return 422 {@code VALIDATION_FAILED} listing the invalid fields
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e) {
    List<String> fields =
        e.getBindingResult().getFieldErrors().stream().map(f -> f.getField()).distinct().toList();
    return envelope(
        ErrorCode.VALIDATION_FAILED,
        ErrorCode.VALIDATION_FAILED.defaultMessage(),
        Map.of("fields", fields));
  }

  /**
   * Query or path parameter of the wrong type.
   *
   * @param e the exception
   * @return 422 {@code VALIDATION_FAILED}
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    return badParameter(e.getName());
  }

  /**
   * Missing required query parameter.
   *
   * @param e the exception
   * @return 422 {@code VALIDATION_FAILED}
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException e) {
    return badParameter(e.getParameterName());
  }

  private static ResponseEntity<ErrorResponse> badParameter(String name) {
    return envelope(
        ErrorCode.VALIDATION_FAILED,
        ErrorCode.VALIDATION_FAILED.defaultMessage(),
        Map.of("parameter", name));
  }

  /**
   * Unknown routes and static resources.
   *
   * @return 404 {@code NOT_FOUND}
   */
  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<ErrorResponse> handleNoRoute() {
    return envelope(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), null);
  }

  /**
   * Wrong method or content type. §5.2 has no code for these, so the real status (405/415) is kept
   * with {@code MALFORMED_REQUEST}.
   *
   * @param e the exception
   * @return envelope with the framework's status
   */
  @ExceptionHandler({
    HttpRequestMethodNotSupportedException.class,
    HttpMediaTypeNotSupportedException.class
  })
  public ResponseEntity<ErrorResponse> handleUnsupported(org.springframework.web.ErrorResponse e) {
    HttpStatusCode status = e.getStatusCode();
    return ResponseEntity.status(status)
        .body(
            ErrorResponse.of(
                ErrorCode.MALFORMED_REQUEST.name(),
                "Method or media type is not supported for this resource.",
                null));
  }

  /**
   * Anything else: generic 500; details go to the log only (tagged with {@code request_id}).
   *
   * @param e the exception
   * @return 500 {@code INTERNAL_ERROR}
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    LOG.error("Unhandled exception", e);
    return envelope(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), null);
  }

  private static ResponseEntity<ErrorResponse> envelope(
      ErrorCode code, String message, Map<String, Object> details) {
    return ResponseEntity.status(HttpStatus.valueOf(code.httpStatus()))
        .body(ErrorResponse.of(code.name(), message, details));
  }

  private static String fieldPath(JsonMappingException e) {
    StringBuilder path = new StringBuilder();
    for (JsonMappingException.Reference ref : e.getPath()) {
      if (ref.getFieldName() != null) {
        if (!path.isEmpty()) {
          path.append('.');
        }
        path.append(ref.getFieldName());
      }
    }
    return path.isEmpty() ? "(body)" : path.toString();
  }
}
