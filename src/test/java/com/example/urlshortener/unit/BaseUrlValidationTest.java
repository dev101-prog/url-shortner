package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.example.urlshortener.service.LinkValidator;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import com.example.urlshortener.support.TestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Finding F1: a base URL without a host must fail startup, not disable the self-redirect check. */
class BaseUrlValidationTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "localhost:8080",
        "ftp://sho.rt",
        "/relative",
        "",
        "http://",
        "https://sho.rt/prefix",
        "https://sho.rt/?q=1",
        "https://sho.rt/#frag",
        "https://user@sho.rt",
        "not a url"
      })
  void nfr4_7_invalidBaseUrlFailsStartup(String baseUrl) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TestProperties.withBaseUrl(baseUrl))
        .withMessageContaining("app.base-url");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://localhost:8080",
        "https://sho.rt",
        "https://sho.rt/",
        "HTTPS://Sho.Rt:8443"
      })
  void nfr4_7_validBaseUrlIsAccepted(String baseUrl) {
    assertThatCode(() -> TestProperties.withBaseUrl(baseUrl)).doesNotThrowAnyException();
  }

  @Test
  void nfr4_7_selfRedirectBlockedForConfiguredHost() {
    LinkValidator validator = new LinkValidator(TestProperties.withBaseUrl("https://sho.rt/"));
    assertThatCode(() -> validator.validateUrl("https://SHO.RT/abc1234"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_URL));
    assertThatCode(() -> validator.validateUrl("https://example.org/x")).doesNotThrowAnyException();
  }
}
