package com.example.urlshortener.support;

import com.example.urlshortener.config.AppProperties;
import java.time.Duration;
import java.util.List;

/** {@link AppProperties} mirroring the design §7.3 defaults, for unit tests. */
public final class TestProperties {

  /** Reserved words from §7.3. */
  public static final List<String> RESERVED_WORDS =
      List.of(
          "api",
          "healthz",
          "readyz",
          "docs",
          "redoc",
          "openapi",
          "openapi.json",
          "static",
          "admin",
          "metrics",
          "favicon.ico",
          "robots.txt",
          "actuator",
          "swagger-ui",
          "v3",
          "error");

  /** Bot patterns from §7.3. */
  public static final List<String> BOT_PATTERNS =
      List.of(
          "(?i)bot",
          "(?i)crawler",
          "(?i)spider",
          "(?i)curl/",
          "(?i)wget/",
          "(?i)facebookexternalhit",
          "(?i)slurp",
          "(?i)python-requests",
          "(?i)headless");

  private TestProperties() {}

  /**
   * Design defaults with the given salt.
   *
   * @param ipSalt HMAC salt
   * @return properties
   */
  public static AppProperties withSalt(String ipSalt) {
    return create(List.of(), ipSalt);
  }

  /**
   * Design defaults with a target-domain denylist.
   *
   * @param blockedDomains denylist
   * @return properties
   */
  public static AppProperties withBlockedDomains(List<String> blockedDomains) {
    return create(blockedDomains, "test-salt");
  }

  /**
   * Design defaults.
   *
   * @return properties
   */
  public static AppProperties defaults() {
    return create(List.of(), "test-salt");
  }

  private static AppProperties create(List<String> blockedDomains, String ipSalt) {
    return new AppProperties(
        "http://localhost:8080",
        new AppProperties.Links(
            7,
            5,
            List.of("http", "https"),
            2048,
            Duration.ofDays(365),
            blockedDomains,
            RESERVED_WORDS),
        new AppProperties.Cache(
            Duration.ofMinutes(10), Duration.ofSeconds(60), 100_000L, Duration.ofSeconds(30)),
        new AppProperties.RateLimit(60, 600),
        new AppProperties.Analytics(
            10_000, Duration.ofSeconds(1), 500, ipSalt, "2026-10-01", BOT_PATTERNS));
  }
}
