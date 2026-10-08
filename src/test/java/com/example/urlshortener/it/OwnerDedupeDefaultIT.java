package com.example.urlshortener.it;

import static com.example.urlshortener.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.ApiClient;
import com.example.urlshortener.support.TestData;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Scenario B1 end to end: V2 migration plus owner default resolution over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerDedupeDefaultIT extends AbstractPostgresIT {

  private static final String DEDUPER = "test-key-b1-deduper";
  private static final String PLAIN = "test-key-b1-plain";

  private final ApiClient api;
  private final JdbcClient jdbc;
  private final LinkRepository links;
  private long deduperId;

  @Autowired
  OwnerDedupeDefaultIT(@LocalServerPort int port, JdbcClient jdbc, LinkRepository links) {
    this.api = new ApiClient(port);
    this.jdbc = jdbc;
    this.links = links;
  }

  @BeforeAll
  void owners() {
    TestData data = new TestData(jdbc);
    deduperId = data.owner("b1-deduper");
    data.apiKey(deduperId, DEDUPER, false);
    data.apiKey(data.owner("b1-plain"), PLAIN, false);
    jdbc.sql("UPDATE owners SET dedupe_default = TRUE WHERE id = :id")
        .param("id", deduperId)
        .update();
  }

  private HttpResponse<String> create(String key, String body) {
    return api.post("/api/v1/links", key, body);
  }

  @Test
  void b1_migrationAddsColumnWithFalseDefault() {
    List<String> columns =
        jdbc.sql(
                "SELECT column_name || ':' || data_type || ':' || is_nullable || ':' || column_default"
                    + " FROM information_schema.columns WHERE table_name = 'owners'"
                    + " AND column_name = 'dedupe_default'")
            .query(String.class)
            .list();

    assertThat(columns).containsExactly("dedupe_default:boolean:NO:false");
    assertThat(links.ownerDedupeDefault(deduperId)).isTrue();
    assertThat(links.ownerDedupeDefault(-1L)).isFalse();
  }

  @Test
  void fr1_4_b1_ownerDefaultAppliesWhenRequestOmitsDedupe() {
    String first =
        json(create(DEDUPER, "{\"url\":\"https://example.org/b1/default\"}")).get("code").asText();

    HttpResponse<String> again = create(DEDUPER, "{\"url\":\"https://example.org/b1/default\"}");

    assertThat(again.statusCode()).isEqualTo(200);
    assertThat(json(again).get("code").asText()).isEqualTo(first);
  }

  @Test
  void fr1_4_b1_requestFalseOverridesOwnerDefault() {
    create(DEDUPER, "{\"url\":\"https://example.org/b1/override\"}");

    HttpResponse<String> forced =
        create(DEDUPER, "{\"url\":\"https://example.org/b1/override\",\"dedupe\":false}");

    assertThat(forced.statusCode()).isEqualTo(201);
  }

  @Test
  void fr1_4_b1_ownersWithoutDefaultKeepTheOldBehaviour() {
    create(PLAIN, "{\"url\":\"https://example.org/b1/plain\"}");

    assertThat(create(PLAIN, "{\"url\":\"https://example.org/b1/plain\"}").statusCode())
        .isEqualTo(201);
    assertThat(
            create(PLAIN, "{\"url\":\"https://example.org/b1/plain\",\"dedupe\":true}")
                .statusCode())
        .isEqualTo(200);
  }
}
