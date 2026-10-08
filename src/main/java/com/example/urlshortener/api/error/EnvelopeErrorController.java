package com.example.urlshortener.api.error;

import com.example.urlshortener.api.dto.ErrorResponse;
import com.example.urlshortener.service.error.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's {@code BasicErrorController}: errors that reach the servlet container's
 * {@code /error} page (outside {@link GlobalExceptionHandler}, e.g. exceptions thrown in filters)
 * also get the §5.1 envelope, never a stack trace or exception message (URL-NFR-4.6).
 */
@RestController
@Hidden
public class EnvelopeErrorController implements ErrorController {

  /** Error path, as configured for Spring Boot's error page registration. */
  static final String ERROR_PATH = "${server.error.path:${error.path:/error}}";

  /**
   * GET error dispatch. Error dispatches keep the original HTTP method, so each method has its own
   * read-only handler (no handler changes state).
   *
   * @param request the error dispatch request
   * @return envelope with the original status
   */
  @GetMapping(ERROR_PATH)
  public ResponseEntity<ErrorResponse> errorForGet(HttpServletRequest request) {
    return envelope(request);
  }

  /**
   * POST error dispatch.
   *
   * @param request the error dispatch request
   * @return envelope with the original status
   */
  @PostMapping(ERROR_PATH)
  public ResponseEntity<ErrorResponse> errorForPost(HttpServletRequest request) {
    return envelope(request);
  }

  /**
   * PUT error dispatch.
   *
   * @param request the error dispatch request
   * @return envelope with the original status
   */
  @PutMapping(ERROR_PATH)
  public ResponseEntity<ErrorResponse> errorForPut(HttpServletRequest request) {
    return envelope(request);
  }

  /**
   * PATCH error dispatch.
   *
   * @param request the error dispatch request
   * @return envelope with the original status
   */
  @PatchMapping(ERROR_PATH)
  public ResponseEntity<ErrorResponse> errorForPatch(HttpServletRequest request) {
    return envelope(request);
  }

  /**
   * DELETE error dispatch.
   *
   * @param request the error dispatch request
   * @return envelope with the original status
   */
  @DeleteMapping(ERROR_PATH)
  public ResponseEntity<ErrorResponse> errorForDelete(HttpServletRequest request) {
    return envelope(request);
  }

  private static ResponseEntity<ErrorResponse> envelope(HttpServletRequest request) {
    HttpStatus status = statusOf(request);
    ErrorCode code = codeFor(status);
    return ResponseEntity.status(status)
        .body(ErrorResponse.of(code.name(), code.defaultMessage(), null));
  }

  private static HttpStatus statusOf(HttpServletRequest request) {
    Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    if (raw instanceof Integer value) {
      HttpStatus resolved = HttpStatus.resolve(value);
      if (resolved != null && resolved.isError()) {
        return resolved;
      }
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }
    // a direct request to /error is not an error dispatch: there is no such resource
    return HttpStatus.NOT_FOUND;
  }

  /** Same code mapping as {@link GlobalExceptionHandler}; no new codes beyond §5.2. */
  static ErrorCode codeFor(HttpStatus status) {
    return switch (status) {
      case BAD_REQUEST, METHOD_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE -> ErrorCode.MALFORMED_REQUEST;
      case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
      case NOT_FOUND -> ErrorCode.NOT_FOUND;
      case LENGTH_REQUIRED -> ErrorCode.LENGTH_REQUIRED;
      case PAYLOAD_TOO_LARGE -> ErrorCode.PAYLOAD_TOO_LARGE;
      case UNPROCESSABLE_ENTITY -> ErrorCode.VALIDATION_FAILED;
      case TOO_MANY_REQUESTS -> ErrorCode.RATE_LIMITED;
      case SERVICE_UNAVAILABLE -> ErrorCode.NOT_READY;
      default -> status.is4xxClientError() ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
    };
  }
}
