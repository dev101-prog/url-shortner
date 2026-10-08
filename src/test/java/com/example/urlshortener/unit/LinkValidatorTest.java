package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.service.LinkValidator;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import com.example.urlshortener.support.TestProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LinkValidatorTest {

  private static final Instant NOW = Instant.parse("2026-10-07T21:00:00Z");

  private final LinkValidator validator = new LinkValidator(TestProperties.defaults());

  private static void assertError(ThrowingCallable call, ErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  static Stream<Arguments> invalidUrls() {
    String tooLong = "https://example.org/" + "a".repeat(2049 - "https://example.org/".length());
    return Stream.of(
        Arguments.of("ftp scheme", "ftp://example.org/file"),
        Arguments.of("javascript scheme", "javascript:alert(1)"),
        Arguments.of("data scheme", "data:text/html,hi"),
        Arguments.of("no scheme", "example.org/path"),
        Arguments.of("no host", "https:///path"),
        Arguments.of("no authority", "http:/path"),
        Arguments.of("userinfo", "https://user:pass@example.org/"),
        Arguments.of("userinfo without password", "https://user@example.org/"),
        Arguments.of("self host", "http://localhost:8080/aB3dE7x"),
        Arguments.of("self host other case and port", "https://LOCALHOST:9999/x"),
        Arguments.of("unparseable", "https://exa mple.org/"),
        Arguments.of("2049 chars", tooLong),
        Arguments.of("blank", "   "));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidUrls")
  void fr1_5_rejectsInvalidUrl(String description, String url) {
    assertError(() -> validator.validateUrl(url), ErrorCode.INVALID_URL);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void fr1_5_urlIsRequired(String url) {
    assertError(() -> validator.validateUrl(url), ErrorCode.INVALID_URL);
  }

  @Test
  void fr1_5_acceptsHttpAndHttpsUpTo2048Chars() {
    String exactly2048 =
        "https://example.org/" + "a".repeat(2048 - "https://example.org/".length());

    assertThat(validator.validateUrl("https://example.org/a/very/long/path?x=1").getHost())
        .isEqualTo("example.org");
    assertThat(validator.validateUrl("HTTP://Example.org").getScheme()).isEqualTo("HTTP");
    assertThatCode(() -> validator.validateUrl(exactly2048)).doesNotThrowAnyException();
  }

  @Test
  void fr1_5_tooLongReportsLimitInDetails() {
    assertThatThrownBy(() -> validator.validateUrl("https://a.io/" + "x".repeat(2100)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.details()).containsEntry("max_length", 2048));
  }

  @Test
  void fr1_5_badSchemeListsAllowedSchemes() {
    assertThatThrownBy(() -> validator.validateUrl("ftp://example.org"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.details()).containsEntry("allowed_schemes", List.of("http", "https")));
  }

  @Test
  void nfr4_7_blockedDomainIsTargetBlocked() {
    LinkValidator withDenylist =
        new LinkValidator(TestProperties.withBlockedDomains(List.of("Evil.example")));

    assertError(
        () -> withDenylist.validateUrl("https://evil.EXAMPLE/phish"), ErrorCode.TARGET_BLOCKED);
    assertThatCode(() -> withDenylist.validateUrl("https://good.example/"))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"abcd", "my-promo", "A_b-9", "spring-docs", "abcdefghijklmnopqrstuvwxyz012345"})
  void fr2_1_acceptsValidAlias(String alias) {
    assertThatCode(() -> validator.validateAlias(alias)).doesNotThrowAnyException();
  }

  @Test
  void fr2_1_aliasIsOptional() {
    assertThatCode(() -> validator.validateAlias(null)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "", "has space", "dot.ted", "slash/x", "ümlaut", "emoji😀"})
  void fr2_1_rejectsInvalidAliasFormat(String alias) {
    assertThatThrownBy(() -> validator.validateAlias(alias))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ALIAS);
              assertThat(e.details()).containsEntry("alias", alias);
            });
  }

  @Test
  void fr2_1_rejects33Chars() {
    assertError(() -> validator.validateAlias("x".repeat(33)), ErrorCode.INVALID_ALIAS);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"Docs", "HEALTHZ", "readyz", "admin", "Metrics", "actuator", "swagger-ui"})
  void fr2_2_rejectsReservedWordsCaseInsensitively(String alias) {
    assertError(() -> validator.validateAlias(alias), ErrorCode.RESERVED_ALIAS);
  }

  @Test
  void fr2_2_reservedWordsThatCannotMatchTheAliasFormatAreInvalidAlias() {
    // "api", "v3", "openapi.json" fail the format rule first (§3.3 order)
    assertError(() -> validator.validateAlias("api"), ErrorCode.INVALID_ALIAS);
    assertError(() -> validator.validateAlias("openapi.json"), ErrorCode.INVALID_ALIAS);
  }

  @Test
  void fr4_2_rejectsPastExpiry() {
    assertError(() -> validator.validateExpiry(NOW.minusSeconds(1), NOW), ErrorCode.INVALID_EXPIRY);
  }

  @Test
  void fr4_2_rejectsExpiryEqualToNow() {
    assertError(() -> validator.validateExpiry(NOW, NOW), ErrorCode.INVALID_EXPIRY);
  }

  @Test
  void fr4_2_rejectsMoreThan365Days() {
    assertError(
        () -> validator.validateExpiry(NOW.plus(Duration.ofDays(366)), NOW),
        ErrorCode.INVALID_EXPIRY);
    assertError(
        () -> validator.validateExpiry(NOW.plus(Duration.ofDays(365)).plusSeconds(1), NOW),
        ErrorCode.INVALID_EXPIRY);
  }

  @Test
  void fr4_2_acceptsFutureUpTo365DaysAndAbsent() {
    assertThatCode(() -> validator.validateExpiry(NOW.plusSeconds(1), NOW))
        .doesNotThrowAnyException();
    assertThatCode(() -> validator.validateExpiry(NOW.plus(Duration.ofDays(365)), NOW))
        .doesNotThrowAnyException();
    assertThatCode(() -> validator.validateExpiry(null, NOW)).doesNotThrowAnyException();
  }
}
