package com.example.urlshortener.it;

import static com.example.urlshortener.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.support.ApiClient;
import com.example.urlshortener.support.PostgresContainerSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * URL-FR-7.5 / 7.7 against the design §4.4 demo seed under the {@code local} profile, on its own
 * database (design §8.5: only SeedDataIT and this test use the seed).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class StatsApiIT {

  private static final String ALICE = "demo-key-alice-0001";
  private static final String BOB = "demo-key-bob-0002";

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url", () -> PostgresContainerSupport.jdbcUrlForDatabase("stats_it"));
    registry.add(
        "spring.datasource.username", () -> PostgresContainerSupport.postgres().getUsername());
    registry.add(
        "spring.datasource.password", () -> PostgresContainerSupport.postgres().getPassword());
  }

  private HttpResponse<String> stats(String path, String key) {
    return new ApiClient(port).get(path, key);
  }

  private static List<String> pairs(JsonNode array, String name) {
    List<String> out = new ArrayList<>();
    array.forEach(n -> out.add(n.get(name).asText() + "=" + n.get("clicks").asLong()));
    return out;
  }

  @Test
  void fr7_5_statsMatchSeed() {
    HttpResponse<String> response = stats("/api/v1/links/aB3dE7x/stats", ALICE);

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode body = json(response);
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    assertThat(body.get("code").asText()).isEqualTo("aB3dE7x");
    assertThat(body.get("to").asText()).isEqualTo(today.toString());
    assertThat(body.get("from").asText()).isEqualTo(today.minusDays(29).toString());
    assertThat(body.get("total_clicks").asLong()).isEqualTo(6);
    assertThat(body.get("human_clicks").asLong()).isEqualTo(4);
    assertThat(body.get("bot_clicks").asLong()).isEqualTo(2);
    assertThat(body.get("unique_visitors_estimate").asLong()).isEqualTo(3);
    assertThat(pairs(body.get("top_referrers"), "referrer"))
        .containsExactly("(direct)=2", "twitter.com=2", "linkedin.com=1", "news.ycombinator.com=1");
    assertThat(pairs(body.get("top_countries"), "country")).containsExactly("US=3", "GB=2");

    JsonNode days = body.get("clicks_per_day");
    assertThat(days).hasSize(30);
    long sum = 0;
    long humans = 0;
    for (JsonNode d : days) {
      sum += d.get("clicks").asLong();
      humans += d.get("human_clicks").asLong();
      assertThat(d.has("date") && d.has("unique_visitors")).isTrue();
    }
    assertThat(sum).isEqualTo(6);
    assertThat(humans).isEqualTo(4);
    assertThat(days.get(0).get("date").asText()).isEqualTo(today.minusDays(29).toString());
    assertThat(days.get(29).get("date").asText()).isEqualTo(today.toString());
  }

  @Test
  void fr7_5_otherSeededLinks() {
    JsonNode springDocs = json(stats("/api/v1/links/spring-docs/stats", ALICE));
    assertThat(springDocs.get("total_clicks").asLong()).isEqualTo(3);
    assertThat(springDocs.get("bot_clicks").asLong()).isZero();
    assertThat(springDocs.get("unique_visitors_estimate").asLong()).isEqualTo(2);
    assertThat(pairs(springDocs.get("top_countries"), "country")).containsExactly("US=2", "DE=1");

    JsonNode inactive = json(stats("/api/v1/links/Qm4Rt8Z/stats", BOB));
    assertThat(inactive.get("total_clicks").asLong()).isZero();
    assertThat(inactive.get("top_referrers")).isEmpty();
    assertThat(inactive.get("top_countries").isNull()).isTrue();
  }

  @Test
  void fr7_5_nonOwner404_missingKey401() {
    assertThat(stats("/api/v1/links/bob-blog/stats", ALICE).statusCode()).isEqualTo(404);
    assertThat(stats("/api/v1/links/aB3dE7x/stats", null).statusCode()).isEqualTo(401);
  }

  @Test
  void fr7_5_invalidRanges422() {
    HttpResponse<String> reversed =
        stats("/api/v1/links/aB3dE7x/stats?from=2026-10-07&to=2026-10-01", ALICE);
    HttpResponse<String> tooLong =
        stats("/api/v1/links/aB3dE7x/stats?from=2024-01-01&to=2026-01-01", ALICE);
    HttpResponse<String> badDate = stats("/api/v1/links/aB3dE7x/stats?from=yesterday", ALICE);

    assertThat(reversed.statusCode()).isEqualTo(422);
    assertThat(json(reversed).path("error").path("code").asText()).isEqualTo("INVALID_DATE_RANGE");
    assertThat(tooLong.statusCode()).isEqualTo(422);
    assertThat(json(tooLong).path("error").path("code").asText()).isEqualTo("INVALID_DATE_RANGE");
    assertThat(badDate.statusCode()).isEqualTo(422);
    assertThat(json(badDate).path("error").path("code").asText()).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  void fr7_5_explicitRangeNarrowsTheResult() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    JsonNode oneDay =
        json(
            stats(
                "/api/v1/links/aB3dE7x/stats?from="
                    + today.minusDays(400)
                    + "&to="
                    + today.minusDays(100),
                ALICE));

    assertThat(oneDay.get("total_clicks").asLong()).isZero();
    assertThat(oneDay.get("clicks_per_day")).hasSize(301);
  }
}
