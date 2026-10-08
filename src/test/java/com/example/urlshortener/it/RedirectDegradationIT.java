package com.example.urlshortener.it;

import static com.example.urlshortener.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.probe.FailingLinkCacheConfig;
import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.ApiClient;
import com.example.urlshortener.support.TestData;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * URL-FR-3.5 failure injection: the links cache throws on every call and the click buffer holds a
 * single event (flushing effectively disabled), yet redirects keep working.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.analytics.buffer-capacity=1", "app.analytics.flush-interval=PT1H"})
@Import(FailingLinkCacheConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedirectDegradationIT extends AbstractPostgresIT {

  private static final String KEY = "test-key-degradation";

  private final ApiClient api;
  private final JdbcClient jdbc;
  private final MeterRegistry meters;

  @Autowired
  RedirectDegradationIT(@LocalServerPort int port, JdbcClient jdbc, MeterRegistry meters) {
    this.api = new ApiClient(port);
    this.jdbc = jdbc;
    this.meters = meters;
  }

  @BeforeAll
  void owner() {
    TestData data = new TestData(jdbc);
    data.apiKey(data.owner("degraded"), KEY, false);
  }

  private String create(String url) {
    HttpResponse<String> response = api.post("/api/v1/links", KEY, "{\"url\":\"" + url + "\"}");
    assertThat(response.statusCode()).isEqualTo(201);
    return json(response).get("code").asText();
  }

  @Test
  void fr3_5_cacheErrorFallsBackToDb() {
    double errorsBefore = meters.counter("urlshortener.cache.errors").count();
    String code = create("https://example.org/degraded");

    HttpResponse<String> response = api.get("/" + code, null);

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location")).hasValue("https://example.org/degraded");
    assertThat(meters.counter("urlshortener.cache.errors").count()).isGreaterThan(errorsBefore);
    assertThat(api.get("/unknownCode9", null).statusCode()).isEqualTo(404);
  }

  @Test
  void fr3_5_fullBufferStillRedirects() {
    String code = create("https://example.org/full-buffer");

    for (int i = 0; i < 4; i++) {
      assertThat(api.get("/" + code, null).statusCode()).isEqualTo(302);
    }

    assertThat(meters.counter("urlshortener.clicks.dropped", "reason", "buffer_full").count())
        .isGreaterThanOrEqualTo(3.0);
  }
}
