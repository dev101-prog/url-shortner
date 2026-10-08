package com.example.urlshortener.it;

import static com.example.urlshortener.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.ApiClient;
import com.example.urlshortener.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

/** CUJ-1 and CUJ-3 (minus stats) end to end on Tomcat + Postgres. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LinkApiIT extends AbstractPostgresIT {

  private static final String ALICE = "test-key-linkapi-alice";
  private static final String BOB = "test-key-linkapi-bob";
  private static final String REVOKED = "test-key-linkapi-revoked";

  private final ApiClient api;
  private final JdbcClient jdbc;
  private TestData data;

  @Autowired
  LinkApiIT(@LocalServerPort int port, JdbcClient jdbc) {
    this.api = new ApiClient(port);
    this.jdbc = jdbc;
  }

  @BeforeAll
  void owners() {
    data = new TestData(jdbc);
    long alice = data.owner("alice");
    long bob = data.owner("bob");
    data.apiKey(alice, ALICE, false);
    data.apiKey(bob, BOB, false);
    data.apiKey(alice, REVOKED, true);
  }

  private HttpResponse<String> create(String key, String body) {
    return api.post("/api/v1/links", key, body);
  }

  private static String errorCode(HttpResponse<String> response) {
    return json(response).path("error").path("code").asText();
  }

  @Test
  void fr1_1_createReturns201With7CharCode() {
    HttpResponse<String> response =
        create(ALICE, "{\"url\":\"https://example.org/a/very/long/path?x=1\"}");

    assertThat(response.statusCode()).isEqualTo(201);
    JsonNode body = json(response);
    String code = body.get("code").asText();
    assertThat(code).matches("^[A-Za-z0-9]{7}$");
    assertThat(response.headers().firstValue("Location")).hasValue("/api/v1/links/" + code);
    assertThat(body.get("short_url").asText()).isEqualTo("http://localhost:8080/" + code);
    assertThat(body.get("target").asText()).isEqualTo("https://example.org/a/very/long/path?x=1");
    assertThat(body.get("status").asText()).isEqualTo("active");
    assertThat(body.get("expires_at").isNull()).isTrue();
    assertThat(Instant.parse(body.get("created_at").asText())).isNotNull();
  }

  @Test
  void fr1_3_sameUrlTwiceGivesTwoCodes() {
    String a = json(create(ALICE, "{\"url\":\"https://example.org/same\"}")).get("code").asText();
    String b = json(create(ALICE, "{\"url\":\"https://example.org/same\"}")).get("code").asText();

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void fr1_4_dedupeReturnsExisting200() {
    String first =
        json(create(ALICE, "{\"url\":\"https://Example.org:443/dedupe\",\"dedupe\":true}"))
            .get("code")
            .asText();

    HttpResponse<String> again =
        create(ALICE, "{\"url\":\"https://example.org/dedupe\",\"dedupe\":true}");

    assertThat(again.statusCode()).isEqualTo(200);
    assertThat(json(again).get("code").asText()).isEqualTo(first);
    assertThat(again.headers().firstValue("Location")).isEmpty();
  }

  @Test
  void fr1_4_dedupeReturnsExisting200_neverAcrossOwners() {
    String alices =
        json(create(ALICE, "{\"url\":\"https://example.org/shared\",\"dedupe\":true}"))
            .get("code")
            .asText();

    HttpResponse<String> bobs =
        create(BOB, "{\"url\":\"https://example.org/shared\",\"dedupe\":true}");

    assertThat(bobs.statusCode()).isEqualTo(201);
    assertThat(json(bobs).get("code").asText()).isNotEqualTo(alices);
  }

  @Test
  void fr1_6_linkReadableImmediatelyAfter201() {
    String code =
        json(create(ALICE, "{\"url\":\"https://example.org/durable\"}")).get("code").asText();

    Map<String, Object> row =
        jdbc.sql("SELECT target_url, status, is_custom_alias FROM links WHERE code = :c")
            .param("c", code)
            .query()
            .singleRow();
    assertThat(row)
        .containsEntry("target_url", "https://example.org/durable")
        .containsEntry("status", "ACTIVE")
        .containsEntry("is_custom_alias", false);
  }

  @Test
  void fr2_1_aliasAndExpiryAreStored() {
    Instant expiry = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    HttpResponse<String> response =
        create(
            ALICE,
            "{\"url\":\"https://example.org/promo\",\"alias\":\"fall-promo\",\"expires_at\":\""
                + expiry.atOffset(java.time.ZoneOffset.ofHours(2))
                + "\"}");

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(json(response).get("code").asText()).isEqualTo("fall-promo");
    assertThat(Instant.parse(json(response).get("expires_at").asText())).isEqualTo(expiry);
  }

  @Test
  void fr2_3_aliasConflict409EvenWhenInactive() {
    assertThat(create(ALICE, "{\"url\":\"https://a.io\",\"alias\":\"taken-alias\"}").statusCode())
        .isEqualTo(201);

    HttpResponse<String> conflict =
        create(BOB, "{\"url\":\"https://b.io\",\"alias\":\"taken-alias\"}");
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(json(conflict).path("error").path("details").path("alias").asText())
        .isEqualTo("taken-alias");
    assertThat(json(conflict).path("error").path("message").asText())
        .isEqualTo("Alias 'taken-alias' is already in use.");

    assertThat(api.delete("/api/v1/links/taken-alias", ALICE).statusCode()).isEqualTo(204);
    HttpResponse<String> afterDelete =
        create(ALICE, "{\"url\":\"https://a.io\",\"alias\":\"taken-alias\"}");
    assertThat(afterDelete.statusCode()).isEqualTo(409);
    assertThat(errorCode(afterDelete)).isEqualTo("ALIAS_CONFLICT");
  }

  @Test
  void fr2_3_concurrentCreatesOfTheSameAliasYieldExactlyOne201() throws Exception {
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      long owner = data.owner("racer-" + i);
      String key = "test-key-linkapi-racer-" + i;
      data.apiKey(owner, key, false);
      keys.add(key);
    }
    List<Callable<Integer>> calls = new ArrayList<>();
    for (String key : keys) {
      calls.add(
          () -> create(key, "{\"url\":\"https://race.io\",\"alias\":\"race-alias\"}").statusCode());
    }

    List<Integer> statuses = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
      for (Future<Integer> f : pool.invokeAll(calls)) {
        statuses.add(f.get());
      }
    }

    assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
    assertThat(statuses).filteredOn(s -> s == 409).hasSize(19);
  }

  @Test
  void fr2_2_reservedAliasIs422CaseInsensitive() {
    HttpResponse<String> response =
        create(ALICE, "{\"url\":\"https://example.org/x\",\"alias\":\"Docs\"}");

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(errorCode(response)).isEqualTo("RESERVED_ALIAS");
  }

  @Test
  void fr2_1_invalidAliasIs422() {
    HttpResponse<String> response =
        create(ALICE, "{\"url\":\"https://example.org/x\",\"alias\":\"a b\"}");

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(errorCode(response)).isEqualTo("INVALID_ALIAS");
  }

  @Test
  void fr1_5_badSchemeIs422InvalidUrl() {
    HttpResponse<String> response = create(ALICE, "{\"url\":\"javascript:alert(1)\"}");

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(errorCode(response)).isEqualTo("INVALID_URL");
  }

  @Test
  void fr1_5_selfReferentialUrlIs422() {
    assertThat(errorCode(create(ALICE, "{\"url\":\"http://localhost:8080/aB3dE7x\"}")))
        .isEqualTo("INVALID_URL");
  }

  @Test
  void fr4_2_expiryRules() {
    assertThat(
            errorCode(
                create(
                    ALICE, "{\"url\":\"https://a.io\",\"expires_at\":\"2020-01-01T00:00:00Z\"}")))
        .isEqualTo("INVALID_EXPIRY");
    String tooFar = Instant.now().plus(366, ChronoUnit.DAYS).toString();
    assertThat(
            errorCode(
                create(ALICE, "{\"url\":\"https://a.io\",\"expires_at\":\"" + tooFar + "\"}")))
        .isEqualTo("INVALID_EXPIRY");
    HttpResponse<String> noOffset =
        create(ALICE, "{\"url\":\"https://a.io\",\"expires_at\":\"2030-01-01T00:00:00\"}");
    assertThat(noOffset.statusCode()).isEqualTo(422);
    assertThat(errorCode(noOffset)).isEqualTo("INVALID_EXPIRY");
  }

  @Test
  void nfr4_5_unknownField422() {
    HttpResponse<String> response = create(ALICE, "{\"url\":\"https://a.io\",\"owner_id\":2}");

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(errorCode(response)).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  void malformedJson400() {
    HttpResponse<String> response = create(ALICE, "{\"url\":");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(errorCode(response)).isEqualTo("MALFORMED_REQUEST");
  }

  @Test
  void nfr4_2_revokedKeyCannotCreate() {
    assertThat(create(REVOKED, "{\"url\":\"https://a.io\"}").statusCode()).isEqualTo(401);
  }

  @Test
  void fr5_1_metadata() {
    String code =
        json(create(ALICE, "{\"url\":\"https://spring.io/projects/spring-boot\"}"))
            .get("code")
            .asText();
    jdbc.sql("UPDATE links SET click_count = 6 WHERE code = :c").param("c", code).update();

    HttpResponse<String> response = api.get("/api/v1/links/" + code, ALICE);

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode body = json(response);
    assertThat(body.get("code").asText()).isEqualTo(code);
    assertThat(body.get("short_url").asText()).isEqualTo("http://localhost:8080/" + code);
    assertThat(body.get("target").asText()).isEqualTo("https://spring.io/projects/spring-boot");
    assertThat(body.get("status").asText()).isEqualTo("active");
    assertThat(body.get("total_clicks").asLong()).isEqualTo(6L);
    assertThat(body.has("created_at")).isTrue();
    assertThat(body.has("expires_at")).isTrue();
  }

  @Test
  void fr4_3_metadataReportsExpiredAndInactive() {
    long alice = jdbc.sql("SELECT id FROM owners WHERE name = 'alice'").query(Long.class).single();
    jdbc.sql(
            "INSERT INTO links (code, owner_id, target_url, normalized_url, created_at, expires_at)"
                + " VALUES ('expired1', :o, 'https://a.io', 'https://a.io',"
                + " now() - interval '2 days', now() - interval '1 day')")
        .param("o", alice)
        .update();
    String code = json(create(ALICE, "{\"url\":\"https://a.io/inactive\"}")).get("code").asText();
    api.delete("/api/v1/links/" + code, ALICE);

    assertThat(json(api.get("/api/v1/links/expired1", ALICE)).get("status").asText())
        .isEqualTo("expired");
    assertThat(json(api.get("/api/v1/links/" + code, ALICE)).get("status").asText())
        .isEqualTo("inactive");
  }

  @Test
  void fr5_2_nonOwner404_missingKey401() {
    String code =
        json(create(ALICE, "{\"url\":\"https://example.org/private\"}")).get("code").asText();

    HttpResponse<String> asBob = api.get("/api/v1/links/" + code, BOB);
    assertThat(asBob.statusCode()).isEqualTo(404);
    assertThat(errorCode(asBob)).isEqualTo("NOT_FOUND");
    assertThat(asBob.body()).doesNotContain("example.org");
    assertThat(api.get("/api/v1/links/" + code, null).statusCode()).isEqualTo(401);
    assertThat(api.get("/api/v1/links/doesNotExist", ALICE).statusCode()).isEqualTo(404);
  }

  @Test
  void fr6_1_softDeleteKeepsRow() {
    String code =
        json(create(ALICE, "{\"url\":\"https://example.org/retire\"}")).get("code").asText();

    assertThat(api.delete("/api/v1/links/" + code, ALICE).statusCode()).isEqualTo(204);

    Map<String, Object> row =
        jdbc.sql("SELECT status, deactivated_at IS NOT NULL AS has_ts FROM links WHERE code = :c")
            .param("c", code)
            .query()
            .singleRow();
    assertThat(row).containsEntry("status", "INACTIVE").containsEntry("has_ts", true);
  }

  @Test
  void fr6_2_idempotent204() {
    String code =
        json(create(ALICE, "{\"url\":\"https://example.org/twice\"}")).get("code").asText();

    assertThat(api.delete("/api/v1/links/" + code, ALICE).statusCode()).isEqualTo(204);
    assertThat(api.delete("/api/v1/links/" + code, ALICE).statusCode()).isEqualTo(204);
  }

  @Test
  void fr6_2_nonOwnerDelete404_missingKey401() {
    String code =
        json(create(ALICE, "{\"url\":\"https://example.org/keep\"}")).get("code").asText();

    assertThat(api.delete("/api/v1/links/" + code, BOB).statusCode()).isEqualTo(404);
    assertThat(api.delete("/api/v1/links/" + code, null).statusCode()).isEqualTo(401);
    assertThat(json(api.get("/api/v1/links/" + code, ALICE)).get("status").asText())
        .isEqualTo("active");
  }
}
