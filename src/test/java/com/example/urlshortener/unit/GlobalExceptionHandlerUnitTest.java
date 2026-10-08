package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.api.dto.ErrorResponse;
import com.example.urlshortener.api.error.GlobalExceptionHandler;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

/** B4: branches of the handler that the slice tests did not reach. */
class GlobalExceptionHandlerUnitTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void b4_rateLimitedWithoutNumericRetryAfterHasNoHeader() {
    ResponseEntity<ErrorResponse> noDetails =
        handler.handleApi(new ApiException(ErrorCode.RATE_LIMITED));
    ResponseEntity<ErrorResponse> textDetail =
        handler.handleApi(
            new ApiException(
                ErrorCode.RATE_LIMITED, "slow down", Map.of("retry_after_seconds", "soon")));
    ResponseEntity<ErrorResponse> numeric =
        handler.handleApi(
            new ApiException(
                ErrorCode.RATE_LIMITED, "slow down", Map.of("retry_after_seconds", 7L)));
    ResponseEntity<ErrorResponse> otherCode =
        handler.handleApi(
            new ApiException(ErrorCode.NOT_FOUND, "x", Map.of("retry_after_seconds", 7L)));

    assertThat(noDetails.getStatusCode().value()).isEqualTo(429);
    assertThat(noDetails.getHeaders().containsKey(HttpHeaders.RETRY_AFTER)).isFalse();
    assertThat(textDetail.getHeaders().containsKey(HttpHeaders.RETRY_AFTER)).isFalse();
    assertThat(numeric.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("7");
    assertThat(otherCode.getHeaders().containsKey(HttpHeaders.RETRY_AFTER)).isFalse();
  }

  private static HttpMessageNotReadableException unreadable(JsonMappingException cause) {
    return new HttpMessageNotReadableException("bad", cause, new MockHttpInputMessage(new byte[0]));
  }

  @Test
  void b4_fieldPathSkipsArrayIndexesAndFallsBackToBody() {
    JsonMappingException nested =
        JsonMappingException.from((com.fasterxml.jackson.core.JsonParser) null, "x");
    nested.prependPath(new JsonMappingException.Reference(null, 0));
    nested.prependPath(new JsonMappingException.Reference(null, "items"));
    nested.prependPath(new JsonMappingException.Reference(null, "order"));
    JsonMappingException root =
        JsonMappingException.from((com.fasterxml.jackson.core.JsonParser) null, "y");

    ResponseEntity<ErrorResponse> nestedResponse = handler.handleUnreadable(unreadable(nested));
    ResponseEntity<ErrorResponse> rootResponse = handler.handleUnreadable(unreadable(root));

    assertThat(nestedResponse.getStatusCode().value()).isEqualTo(422);
    assertThat(nestedResponse.getBody().error().details()).containsEntry("field", "order.items");
    assertThat(rootResponse.getBody().error().details()).containsEntry("field", "(body)");
  }
}
