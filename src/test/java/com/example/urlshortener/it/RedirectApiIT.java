package com.example.urlshortener.it;

import static com.example.urlshortener.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.probe.TestClockConfig;
import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.ApiClient;
import com.example.urlshortener.support.MutableClock;
import com.example.urlshortener.support.TestData;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** CUJ-2 end to end: 302 / 404 / 410 / 429, no-store headers, cache behaviour. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.rate-limit.redirect-per-minute=5")
@Import(TestClockConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedirectApiIT extends AbstractPostgresIT {

  private static final String KEY = "test-key-redirect-owner";

  private final ApiClient api;
  private final JdbcClient jdbc;
  private final MutableClock clock;
  private final MeterRegistry meters;

  @Autowired
  RedirectApiIT(
      @LocalServerPort int port, JdbcClient jdbc, MutableClock clock, MeterRegistry meters) {
    this.api = new ApiClient(port);
    this.jdbc = jdbc;
    this.clock = clock;
    this.meters = meters;
  }

  @BeforeAll
  void owner() {
    TestData data = new TestData(jdbc);
    data.apiKey(data.owner("redirect-owner"), KEY, false);
  }

  /** Each test starts in a fresh one-minute rate-limit window (limit is 5/min here). */
  @BeforeEach
  void nextWindow() {
    clock.advance(Duration.ofMinutes(1));
  }

  private String create(String body) {
    HttpResponse<String> response = api.post("/api/v1/links", KEY, body);
    assertThat(response.statusCode()).isEqualTo(201);
    return json(response).get("code").asText();
  }

  private double cacheHits() {
    return meters.counter("urlshortener.cache.requests", "cache", "links", "result", "hit").count();
  }

  @Test
  void fr3_1_redirect302() {
    String code = create("{\"url\":\"https://spring.io/projects/spring-boot\"}");

    HttpResponse<String> response = api.get("/" + code, null);

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location"))
        .hasValue("https://spring.io/projects/spring-boot");
  }

  @Test
  void fr3_3_noStoreHeaders() {
    String code = create("{\"url\":\"https://example.org/no-store\"}");

    HttpResponse<String> response = api.get("/" + code, null);

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Cache-Control"))
        .hasValue("private, no-cache, no-store, max-age=0");
    assertThat(response.headers().firstValue("Pragma")).hasValue("no-cache");
  }

  @Test
  void fr3_2_unknown404_inactive404_expired410() {
    long owner =
        jdbc.sql("SELECT id FROM owners WHERE name = 'redirect-owner'").query(Long.class).single();
    jdbc.sql(
            "INSERT INTO links (code, owner_id, target_url, normalized_url, status, created_at,"
                + " expires_at, deactivated_at) VALUES"
                + " ('Xy9Kp2Q', :o, 'https://www.postgresql.org/docs/', 'x', 'ACTIVE',"
                + "  now() - interval '10 days', now() - interval '1 day', NULL),"
                + " ('Qm4Rt8Z', :o, 'https://github.com/', 'x', 'INACTIVE',"
                + "  now() - interval '5 days', NULL, now() - interval '2 days')")
        .param("o", owner)
        .update();

    HttpResponse<String> unknown = api.get("/noSuchCode1", null);
    HttpResponse<String> inactive = api.get("/Qm4Rt8Z", null);
    HttpResponse<String> expired = api.get("/Xy9Kp2Q", null);

    assertThat(unknown.statusCode()).isEqualTo(404);
    assertThat(json(unknown).path("error").path("code").asText()).isEqualTo("NOT_FOUND");
    assertThat(inactive.statusCode()).isEqualTo(404);
    assertThat(json(inactive).path("error").path("code").asText()).isEqualTo("NOT_FOUND");
    assertThat(inactive.body()).doesNotContain("github").doesNotContain("redirect-owner");
    assertThat(expired.statusCode()).isEqualTo(410);
    assertThat(json(expired).path("error").path("code").asText()).isEqualTo("LINK_EXPIRED");
    assertThat(expired.body()).doesNotContain("postgresql");
  }

  @Test
  void fr4_3_expiredLinkServedFromCacheReturns410() {
    Instant expiry = clock.instant().plus(Duration.ofHours(1));
    String code =
        create("{\"url\":\"https://example.org/soon\",\"expires_at\":\"" + expiry + "\"}");
    assertThat(api.get("/" + code, null).statusCode()).isEqualTo(302);

    clock.advance(Duration.ofHours(2));
    double hitsBefore = cacheHits();
    HttpResponse<String> afterExpiry = api.get("/" + code, null);

    assertThat(afterExpiry.statusCode()).isEqualTo(410);
    assertThat(json(afterExpiry).path("error").path("code").asText()).isEqualTo("LINK_EXPIRED");
    assertThat(cacheHits()).as("served from the cache, not the DB").isEqualTo(hitsBefore + 1);
  }

  @Test
  void fr6_3_redirect404ImmediatelyAfterDelete() {
    String code = create("{\"url\":\"https://example.org/retire-me\"}");
    assertThat(api.get("/" + code, null).statusCode()).isEqualTo(302);

    assertThat(api.delete("/api/v1/links/" + code, KEY).statusCode()).isEqualTo(204);

    assertThat(api.get("/" + code, null).statusCode()).isEqualTo(404);
  }

  @Test
  void fr3_6_negativeCacheIsClearedWhenTheCodeIsCreated() {
    assertThat(api.get("/later-alias", null).statusCode()).isEqualTo(404);
    assertThat(api.get("/later-alias", null).statusCode()).isEqualTo(404);
    assertThat(meters.counter("urlshortener.negative_cache.hits").count()).isPositive();

    create("{\"url\":\"https://example.org/later\",\"alias\":\"later-alias\"}");

    assertThat(api.get("/later-alias", null).statusCode()).isEqualTo(302);
  }

  @Test
  void fr2_4_codesAreCaseSensitiveAndNonCodePathsAre404() {
    create("{\"url\":\"https://example.org/case\",\"alias\":\"CaseAlias\"}");

    assertThat(api.get("/CaseAlias", null).statusCode()).isEqualTo(302);
    assertThat(api.get("/casealias", null).statusCode()).isEqualTo(404);
    assertThat(api.get("/a.b", null).statusCode()).isEqualTo(404);
    assertThat(api.get("/abc", null).statusCode()).isEqualTo(404);
  }

  @Test
  void r11_systemRoutesWinOverTheCodePattern() {
    assertThat(api.get("/actuator/health", null).statusCode()).isEqualTo(200);
    assertThat(api.get("/actuator", null).statusCode()).isEqualTo(200);
    assertThat(api.get("/openapi.json", null).statusCode()).isEqualTo(200);
  }

  @Test
  void fr3_7_rateLimited429WithRetryAfter() {
    String code = create("{\"url\":\"https://example.org/hot\"}");
    for (int i = 0; i < 5; i++) {
      assertThat(api.get("/" + code, null).statusCode()).isEqualTo(302);
    }

    HttpResponse<String> limited = api.get("/" + code, null);

    assertThat(limited.statusCode()).isEqualTo(429);
    assertThat(json(limited).path("error").path("code").asText()).isEqualTo("RATE_LIMITED");
    assertThat(limited.headers().firstValue("Retry-After"))
        .hasValueSatisfying(v -> assertThat(Long.parseLong(v)).isBetween(1L, 60L));
  }
}
