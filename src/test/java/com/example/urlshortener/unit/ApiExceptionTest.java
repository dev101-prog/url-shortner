package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ApiExceptionTest {

  @Test
  void defaultsToTheCodeMessageWithoutDetailsOrStackTrace() {
    ApiException e = new ApiException(ErrorCode.NOT_FOUND);

    assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND);
    assertThat(e.getMessage()).isEqualTo("Not found.");
    assertThat(e.details()).isNull();
    assertThat(e.getStackTrace()).isEmpty();
  }

  @Test
  void detailsAreCopied() {
    Map<String, Object> details = new HashMap<>();
    details.put("alias", "spring-docs");
    ApiException e =
        new ApiException(ErrorCode.ALIAS_CONFLICT, "Alias 'spring-docs' is in use.", details);
    details.clear();

    assertThat(e.details()).containsExactly(Map.entry("alias", "spring-docs"));
    assertThat(new ApiException(ErrorCode.INVALID_URL, "bad").details()).isNull();
  }

  @Test
  void statusesMatchDesignTable() {
    assertThat(ErrorCode.MALFORMED_REQUEST.httpStatus()).isEqualTo(400);
    assertThat(ErrorCode.UNAUTHORIZED.httpStatus()).isEqualTo(401);
    assertThat(ErrorCode.NOT_FOUND.httpStatus()).isEqualTo(404);
    assertThat(ErrorCode.ALIAS_CONFLICT.httpStatus()).isEqualTo(409);
    assertThat(ErrorCode.LINK_EXPIRED.httpStatus()).isEqualTo(410);
    assertThat(ErrorCode.LENGTH_REQUIRED.httpStatus()).isEqualTo(411);
    assertThat(ErrorCode.PAYLOAD_TOO_LARGE.httpStatus()).isEqualTo(413);
    assertThat(ErrorCode.RATE_LIMITED.httpStatus()).isEqualTo(429);
    assertThat(ErrorCode.INTERNAL_ERROR.httpStatus()).isEqualTo(500);
    assertThat(ErrorCode.CODE_GENERATION_EXHAUSTED.httpStatus()).isEqualTo(503);
    assertThat(ErrorCode.NOT_READY.httpStatus()).isEqualTo(503);
  }

  @ParameterizedTest
  @EnumSource(
      value = ErrorCode.class,
      names = {
        "INVALID_URL",
        "TARGET_BLOCKED",
        "INVALID_ALIAS",
        "RESERVED_ALIAS",
        "INVALID_EXPIRY",
        "INVALID_DATE_RANGE",
        "VALIDATION_FAILED"
      })
  void validationCodesAre422(ErrorCode code) {
    assertThat(code.httpStatus()).isEqualTo(422);
    assertThat(code.defaultMessage()).isNotBlank();
  }
}
