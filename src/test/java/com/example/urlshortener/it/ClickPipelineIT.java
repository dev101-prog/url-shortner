package com.example.urlshortener.it;

import static com.example.urlshortener.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.ApiClient;
import com.example.urlshortener.support.TestData;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

/** URL-FR-7.1 / 7.2: the asynchronous click pipeline end to end with the real scheduler. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClickPipelineIT extends AbstractPostgresIT {

  private static final String KEY = "test-key-pipeline";
  private static final Duration VISIBLE_WITHIN = Duration.ofSeconds(2);

  private final ApiClient api;
  private final JdbcClient jdbc;

  @Autowired
  ClickPipelineIT(@LocalServerPort int port, JdbcClient jdbc) {
    this.api = new ApiClient(port);
    this.jdbc = jdbc;
  }

  @BeforeAll
  void owner() {
    TestData data = new TestData(jdbc);
    data.apiKey(data.owner("pipeline"), KEY, false);
  }

  private String create(String body) {
    HttpResponse<String> response = api.post("/api/v1/links", KEY, body);
    assertThat(response.statusCode()).isEqualTo(201);
    return json(response).get("code").asText();
  }

  private Map<String, Object> counts(String code) {
    return jdbc.sql(
            "SELECT l.click_count, (SELECT count(*) FROM click_events e WHERE e.link_id = l.id)"
                + " AS events FROM links l WHERE l.code = :c")
        .param("c", code)
        .query()
        .singleRow();
  }

  /** Polls until the condition holds; returns the elapsed time, or fails after the limit. */
  private static Duration awaitWithin(Duration limit, BooleanSupplier condition)
      throws InterruptedException {
    long start = System.nanoTime();
    while (!condition.getAsBoolean()) {
      Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
      assertThat(elapsed).as("condition not met within %s", limit).isLessThan(limit);
      Thread.sleep(25);
    }
    return Duration.ofNanos(System.nanoTime() - start);
  }

  @Test
  void fr7_2_clickVisibleInDbWithin2s() throws Exception {
    String code = create("{\"url\":\"https://example.org/tracked\"}");

    HttpResponse<String> redirect =
        api.getWithHeaders(
            "/" + code,
            "Referer",
            "https://www.news.example.com/post/1",
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0");
    assertThat(redirect.statusCode()).isEqualTo(302);

    Duration elapsed =
        awaitWithin(
            VISIBLE_WITHIN,
            () ->
                ((Number) counts(code).get("events")).longValue() == 1L
                    && ((Number) counts(code).get("click_count")).longValue() == 1L);

    assertThat(elapsed).isLessThan(VISIBLE_WITHIN);
    Map<String, Object> event =
        jdbc.sql(
                "SELECT e.referrer_host, e.user_agent, e.is_bot, e.ip_hash, e.country"
                    + " FROM click_events e JOIN links l ON l.id = e.link_id WHERE l.code = :c")
            .param("c", code)
            .query()
            .singleRow();
    assertThat(event)
        .containsEntry("referrer_host", "news.example.com")
        .containsEntry("is_bot", false)
        .containsEntry("country", null);
    assertThat((String) event.get("ip_hash")).matches("^[0-9a-f]{64}$").doesNotContain("127.0.0.1");
  }

  @Test
  void fr7_1_oneEventPer302_noneFor404() throws Exception {
    String active = create("{\"url\":\"https://example.org/one-each\"}");
    String retired = create("{\"url\":\"https://example.org/retired\"}");
    assertThat(api.delete("/api/v1/links/" + retired, KEY).statusCode()).isEqualTo(204);

    for (int i = 0; i < 3; i++) {
      assertThat(api.get("/" + active, null).statusCode()).isEqualTo(302);
    }
    assertThat(api.getWithHeaders("/" + active, "User-Agent", "curl/8.4.0").statusCode())
        .isEqualTo(302);
    assertThat(api.get("/" + retired, null).statusCode()).isEqualTo(404);
    assertThat(api.get("/neverCreated1", null).statusCode()).isEqualTo(404);

    awaitWithin(VISIBLE_WITHIN, () -> ((Number) counts(active).get("events")).longValue() == 4L);
    Thread.sleep(1500); // at least one more flush interval: nothing else may arrive

    assertThat(counts(active)).containsEntry("click_count", 4L).containsEntry("events", 4L);
    assertThat(counts(retired)).containsEntry("click_count", 0L).containsEntry("events", 0L);
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM click_events e JOIN links l ON l.id = e.link_id"
                        + " WHERE l.code = :c AND e.is_bot")
                .param("c", active)
                .query(Long.class)
                .single())
        .as("bots are flagged, not dropped")
        .isEqualTo(1L);
  }
}
