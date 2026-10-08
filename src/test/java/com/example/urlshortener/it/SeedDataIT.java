package com.example.urlshortener.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.support.PostgresContainerSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Build step 2 "done when": Flyway applies V1 + V1_1 under the {@code local} profile and the seed
 * invariant holds ({@code links.click_count = count(click_events)} per link: 6, 3, 1, 0, 2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
class SeedDataIT {

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url", () -> PostgresContainerSupport.jdbcUrlForDatabase("seed_it"));
    registry.add(
        "spring.datasource.username", () -> PostgresContainerSupport.postgres().getUsername());
    registry.add(
        "spring.datasource.password", () -> PostgresContainerSupport.postgres().getPassword());
  }

  @Test
  void flywayAppliedSchemaAndSeedUnderLocalProfile(@Autowired JdbcClient jdbc) {
    List<String> versions =
        jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
            .query(String.class)
            .list();

    assertThat(versions).containsExactly("1", "1.1", "2");
  }

  @Test
  void seedInvariant_clickCountMatchesClickEventsPerLink(@Autowired JdbcClient jdbc) {
    Map<String, long[]> rows = new LinkedHashMap<>();
    jdbc.sql(
            """
            SELECT l.code, l.click_count, count(e.id) AS events
            FROM links l LEFT JOIN click_events e ON e.link_id = l.id
            GROUP BY l.id, l.code, l.click_count
            ORDER BY l.id
            """)
        .query(
            (rs, n) ->
                rows.put(
                    rs.getString("code"),
                    new long[] {rs.getLong("click_count"), rs.getLong("events")}))
        .list();

    assertThat(rows.keySet())
        .containsExactly("aB3dE7x", "spring-docs", "Xy9Kp2Q", "Qm4Rt8Z", "bob-blog");
    assertThat(rows.values()).allSatisfy(r -> assertThat(r[0]).isEqualTo(r[1]));
    assertThat(rows.values().stream().map(r -> r[0]).toList()).containsExactly(6L, 3L, 1L, 0L, 2L);
  }

  @Test
  void seedScenarios_statusExpiryAndOwnership(@Autowired JdbcClient jdbc) {
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM links WHERE code = 'Xy9Kp2Q' AND expires_at < now()"
                        + " AND status = 'ACTIVE'")
                .query(Long.class)
                .single())
        .isEqualTo(1L);
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM links WHERE code = 'Qm4Rt8Z' AND status = 'INACTIVE'"
                        + " AND deactivated_at IS NOT NULL")
                .query(Long.class)
                .single())
        .isEqualTo(1L);
    assertThat(
            jdbc.sql(
                    "SELECT o.name FROM links l JOIN owners o ON o.id = l.owner_id"
                        + " WHERE l.code = 'bob-blog'")
                .query(String.class)
                .single())
        .isEqualTo("bob");
    // setval() keeps new inserts from colliding with seeded ids (other tests may advance it
    // further)
    assertThat(
            jdbc.sql(
                    "SELECT (SELECT last_value FROM links_id_seq) >= (SELECT max(id) FROM links)"
                        + " AND (SELECT last_value FROM owners_id_seq) >= 2")
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  @Test
  void seededApiKeysAreStoredOnlyAsSha256OfTheDemoKeys(@Autowired JdbcClient jdbc) {
    Map<String, Boolean> revokedByHash = new LinkedHashMap<>();
    jdbc.sql("SELECT key_hash, revoked_at IS NOT NULL AS revoked FROM api_keys ORDER BY id")
        .query((rs, n) -> revokedByHash.put(rs.getString(1), rs.getBoolean(2)))
        .list();

    assertThat(revokedByHash)
        .containsExactly(
            Map.entry(sha256Hex("demo-key-alice-0001"), false),
            Map.entry(sha256Hex("demo-key-bob-0002"), false),
            Map.entry(sha256Hex("demo-key-revoked-0003"), true));
  }

  @Test
  void schemaConstraintsRejectRowsThatBreakCriticalRules(@Autowired JdbcClient jdbc) {
    String insert =
        "INSERT INTO links (code, owner_id, target_url, normalized_url, status, deactivated_at)"
            + " VALUES (:code, 1, 'https://a.io', 'https://a.io', :status,"
            + " CASE WHEN :deactivated THEN now() END)";

    // code format (chk_links_code)
    assertThatThrownBy(
            () ->
                jdbc.sql(insert)
                    .param("code", "ab")
                    .param("status", "ACTIVE")
                    .param("deactivated", false)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
    // one namespace, case-sensitive unique code (uq_links_code, URL-FR-2.4)
    assertThatThrownBy(
            () ->
                jdbc.sql(insert)
                    .param("code", "spring-docs")
                    .param("status", "ACTIVE")
                    .param("deactivated", false)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
    // INACTIVE <=> deactivated_at NOT NULL (chk_links_deactivated)
    assertThatThrownBy(
            () ->
                jdbc.sql(insert)
                    .param("code", "zzzz")
                    .param("status", "INACTIVE")
                    .param("deactivated", false)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
    // unknown status (chk_links_status)
    assertThatThrownBy(
            () ->
                jdbc.sql(insert)
                    .param("code", "zzzz")
                    .param("status", "DELETED")
                    .param("deactivated", true)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static String sha256Hex(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
