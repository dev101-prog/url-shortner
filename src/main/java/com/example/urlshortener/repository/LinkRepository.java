package com.example.urlshortener.repository;

import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.domain.LinkStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Persistence for {@code links}; parameterised SQL from design §4.5 only (URL-NFR-4.5). */
@Repository
public class LinkRepository {

  static final String INSERT_IF_CODE_FREE =
      """
      INSERT INTO links (code, owner_id, target_url, normalized_url, is_custom_alias, expires_at)
      VALUES (:code, :ownerId, :targetUrl, :normalizedUrl, :customAlias, :expiresAt)
      ON CONFLICT (code) DO NOTHING
      RETURNING id, code, owner_id, target_url, normalized_url, is_custom_alias, status,
                created_at, expires_at, deactivated_at, click_count;
      """;

  static final String FIND_DEDUPE_CANDIDATE =
      """
      SELECT * FROM links
      WHERE owner_id = :ownerId AND normalized_url = :normalizedUrl AND status = 'ACTIVE'
        AND (expires_at IS NULL OR expires_at > :now)
        AND expires_at IS NOT DISTINCT FROM :expiresAt
        AND is_custom_alias = FALSE
      ORDER BY created_at DESC LIMIT 1;
      """;

  static final String FIND_BY_CODE =
      """
      SELECT * FROM links WHERE code = :code;
      """;

  static final String DEACTIVATE =
      """
      UPDATE links SET status = 'INACTIVE', deactivated_at = :now
      WHERE code = :code AND owner_id = :ownerId AND status = 'ACTIVE';
      """;

  static final String OWNER_DEDUPE_DEFAULT =
      """
      SELECT dedupe_default FROM owners WHERE id = :ownerId;
      """;

  private static final RowMapper<Link> LINK_MAPPER = LinkRepository::mapLink;

  private final JdbcClient jdbc;

  /**
   * Creates the repository.
   *
   * @param jdbc JDBC client
   */
  public LinkRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * URL-FR-1.2, 2.3, 1.6: inserts a link unless the code exists in any status. The row is committed
   * when this returns (auto-commit), so a 201 is only sent after a durable write.
   *
   * @param code generated code or alias
   * @param ownerId owner id
   * @param targetUrl target URL as submitted
   * @param normalizedUrl normalised URL for dedupe
   * @param customAlias whether {@code code} is a caller-supplied alias
   * @param expiresAt expiry or {@code null}
   * @return the inserted row, or empty when the code is already taken
   */
  public Optional<Link> insertIfCodeFree(
      String code,
      long ownerId,
      String targetUrl,
      String normalizedUrl,
      boolean customAlias,
      Instant expiresAt) {
    return jdbc.sql(INSERT_IF_CODE_FREE)
        .param("code", code)
        .param("ownerId", ownerId)
        .param("targetUrl", targetUrl)
        .param("normalizedUrl", normalizedUrl)
        .param("customAlias", customAlias)
        .param("expiresAt", SqlTypes.timestamptz(expiresAt))
        .query(LINK_MAPPER)
        .optional();
  }

  /**
   * URL-FR-1.4: newest active, unexpired, non-alias link of the same owner with the same normalised
   * URL and the same expiry. Never matches across owners.
   *
   * @param ownerId owner id
   * @param normalizedUrl normalised URL
   * @param expiresAt requested expiry or {@code null}
   * @param now current instant from the injected clock
   * @return the candidate, if any
   */
  public Optional<Link> findDedupeCandidate(
      long ownerId, String normalizedUrl, Instant expiresAt, Instant now) {
    return jdbc.sql(FIND_DEDUPE_CANDIDATE)
        .param("ownerId", ownerId)
        .param("normalizedUrl", normalizedUrl)
        .param("now", SqlTypes.timestamptz(now))
        .param("expiresAt", SqlTypes.timestamptz(expiresAt))
        .query(LINK_MAPPER)
        .optional();
  }

  /**
   * Looks up a link by code (case-sensitive) for redirect misses and metadata.
   *
   * @param code short code
   * @return the link, if any
   */
  public Optional<Link> findByCode(String code) {
    return jdbc.sql(FIND_BY_CODE).param("code", code).query(LINK_MAPPER).optional();
  }

  /**
   * URL-FR-6.1: soft delete, owner-scoped and idempotent.
   *
   * @param code short code
   * @param ownerId owner id
   * @param now deactivation time from the injected clock
   * @return 1 if the link was active and is now inactive, otherwise 0
   */
  public int deactivate(String code, long ownerId, Instant now) {
    return jdbc.sql(DEACTIVATE)
        .param("code", code)
        .param("ownerId", ownerId)
        .param("now", SqlTypes.timestamptz(now))
        .update();
  }

  /**
   * URL-FR-1.4 / scenario B1: the owner's default dedupe setting ({@code owners.dedupe_default}).
   *
   * @param ownerId owner id
   * @return the owner's default, {@code false} for an unknown owner
   */
  public boolean ownerDedupeDefault(long ownerId) {
    return jdbc.sql(OWNER_DEDUPE_DEFAULT)
        .param("ownerId", ownerId)
        .query(Boolean.class)
        .optional()
        .orElse(Boolean.FALSE);
  }

  private static Link mapLink(ResultSet rs, int rowNum) throws SQLException {
    return new Link(
        rs.getLong("id"),
        rs.getString("code"),
        rs.getLong("owner_id"),
        rs.getString("target_url"),
        rs.getString("normalized_url"),
        rs.getBoolean("is_custom_alias"),
        LinkStatus.valueOf(rs.getString("status")),
        SqlTypes.instant(rs, "created_at"),
        SqlTypes.instant(rs, "expires_at"),
        SqlTypes.instant(rs, "deactivated_at"),
        rs.getLong("click_count"));
  }
}
