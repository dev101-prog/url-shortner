package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Binds the real application*.yml and checks the §7.3 defaults and validation. */
class AppPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(PropsConfig.class);

  @Test
  void defaultsMatchDesign() {
    runner.run(
        ctx -> {
          AppProperties p = ctx.getBean(AppProperties.class);
          assertThat(p.baseUrl()).isEqualTo("http://localhost:8080");
          assertThat(p.links().codeLength()).isEqualTo(7);
          assertThat(p.links().maxCodegenAttempts()).isEqualTo(5);
          assertThat(p.links().allowedSchemes()).containsExactly("http", "https");
          assertThat(p.links().maxUrlLength()).isEqualTo(2048);
          assertThat(p.links().maxExpiry()).isEqualTo(Duration.ofDays(365));
          assertThat(p.links().blockedDomains()).isEmpty();
          assertThat(p.links().reservedWords())
              .containsExactly(
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
          assertThat(p.cache().ttl()).isEqualTo(Duration.ofMinutes(10));
          assertThat(p.cache().negativeTtl()).isEqualTo(Duration.ofSeconds(60));
          assertThat(p.cache().maxSize()).isEqualTo(100_000L);
          assertThat(p.cache().apiKeyTtl()).isEqualTo(Duration.ofSeconds(30));
          assertThat(p.rateLimit().createPerMinute()).isEqualTo(60);
          assertThat(p.rateLimit().redirectPerMinute()).isEqualTo(600);
          assertThat(p.analytics().bufferCapacity()).isEqualTo(10_000);
          assertThat(p.analytics().flushInterval()).isEqualTo(Duration.ofSeconds(1));
          assertThat(p.analytics().flushBatchSize()).isEqualTo(500);
          assertThat(p.analytics().ipSalt()).isEqualTo("change-me");
          assertThat(p.analytics().botPatternsVersion()).isEqualTo("2026-10-01");
          assertThat(p.analytics().botPatterns())
              .hasSize(9)
              .contains("(?i)bot", "(?i)curl/", "(?i)facebookexternalhit");
        });
  }

  @Test
  void localProfileUsesTheLocalDevSalt() {
    runner
        .withPropertyValues("spring.profiles.active=local")
        .run(
            ctx ->
                assertThat(ctx.getBean(AppProperties.class).analytics().ipSalt())
                    .isEqualTo("local-dev-salt"));
  }

  @Test
  void listsAreImmutable() {
    runner.run(
        ctx -> {
          AppProperties p = ctx.getBean(AppProperties.class);
          assertThat(p.links().reservedWords()).isUnmodifiable();
          assertThat(p.analytics().botPatterns()).isUnmodifiable();
        });
  }

  @Test
  void invalidValuesFailFastAtStartup() {
    runner
        .withPropertyValues("app.links.code-length=0")
        .run(
            ctx ->
                assertThat(ctx)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(BindValidationException.class)
                    .hasMessageContaining("codeLength"));
    runner
        .withPropertyValues("app.analytics.ip-salt=")
        .run(
            ctx ->
                assertThat(ctx)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(BindValidationException.class)
                    .hasMessageContaining("ipSalt"));
  }

  @Test
  void absentListsBindAsEmpty() {
    AppProperties.Links links =
        new AppProperties.Links(7, 5, null, 2048, Duration.ofDays(1), null, null);
    AppProperties.Analytics analytics =
        new AppProperties.Analytics(1, Duration.ofSeconds(1), 1, "s", "v", null);

    assertThat(links.allowedSchemes()).isEmpty();
    assertThat(links.blockedDomains()).isEmpty();
    assertThat(links.reservedWords()).isEmpty();
    assertThat(analytics.botPatterns()).isEmpty();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AppProperties.class)
  static class PropsConfig {}
}
