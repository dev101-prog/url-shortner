package com.example.urlshortener.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.ApiClient;
import com.example.urlshortener.support.TestData;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

/** URL-NFR-4.3 with the create limit lowered to 3/min in test config. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.rate-limit.create-per-minute=3")
class CreateRateLimitIT extends AbstractPostgresIT {

  @Test
  void nfr4_3_createOverLimit429WithRetryAfter(
      @LocalServerPort int port, @Autowired JdbcClient jdbc) {
    TestData data = new TestData(jdbc);
    data.apiKey(data.owner("limited"), "test-key-limited", false);
    data.apiKey(data.owner("other"), "test-key-other", false);
    ApiClient api = new ApiClient(port);

    for (int i = 0; i < 3; i++) {
      assertThat(
              api.post("/api/v1/links", "test-key-limited", "{\"url\":\"https://a.io\"}")
                  .statusCode())
          .isEqualTo(201);
    }
    HttpResponse<String> limited =
        api.post("/api/v1/links", "test-key-limited", "{\"url\":\"https://a.io\"}");

    assertThat(limited.statusCode()).isEqualTo(429);
    assertThat(ApiClient.json(limited).path("error").path("code").asText())
        .isEqualTo("RATE_LIMITED");
    assertThat(limited.headers().firstValue("Retry-After"))
        .hasValueSatisfying(v -> assertThat(Long.parseLong(v)).isBetween(1L, 60L));
    // limits are per API key
    assertThat(
            api.post("/api/v1/links", "test-key-other", "{\"url\":\"https://a.io\"}").statusCode())
        .isEqualTo(201);
  }
}
