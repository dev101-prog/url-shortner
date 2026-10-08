package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.service.LinkValidator;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import com.example.urlshortener.support.TestProperties;
import org.junit.jupiter.api.Test;

/** B4: edge cases found by the JaCoCo gap analysis (design §11.3). */
class LinkValidatorEdgeCasesTest {

  private static AppProperties withBaseUrl(String baseUrl) {
    AppProperties d = TestProperties.defaults();
    return new AppProperties(
        baseUrl,
        d.links(),
        d.cache(),
        d.rateLimit(),
        d.analytics(),
        d.http(),
        d.stats(),
        d.health());
  }

  @Test
  void b4_baseUrlWithoutHostDisablesOnlyTheSelfHostCheck() {
    // "localhost:8080" parses as scheme "localhost" with no host, so there is no self host to
    // compare against. This documents the behaviour; see docs/scenarios/B4.md (finding F1).
    LinkValidator validator = new LinkValidator(withBaseUrl("localhost:8080"));

    assertThat(validator.validateUrl("http://localhost:8080/x").getHost()).isEqualTo("localhost");
    assertThatThrownBy(() -> validator.validateUrl("ftp://example.org"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_URL));
  }

  @Test
  void b4_emptyAuthorityIsRejectedAsMissingHost() {
    LinkValidator validator = new LinkValidator(TestProperties.defaults());

    assertThatThrownBy(() -> validator.validateUrl("http://:80/path"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.INVALID_URL);
              assertThat(e.getMessage()).isEqualTo("url must contain a host.");
            });
  }
}
