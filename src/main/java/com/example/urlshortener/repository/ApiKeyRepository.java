package com.example.urlshortener.repository;

import com.example.urlshortener.service.domain.AuthenticatedOwner;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Persistence for {@code api_keys}; keys are looked up by SHA-256 hash only (design D7). */
@Repository
public class ApiKeyRepository {

  static final String FIND_ACTIVE_BY_HASH =
      """
      SELECT id, owner_id FROM api_keys WHERE key_hash = :hash AND revoked_at IS NULL;
      """;

  private final JdbcClient jdbc;

  /**
   * Creates the repository.
   *
   * @param jdbc JDBC client
   */
  public ApiKeyRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * URL-NFR-4.1, 4.2: resolves a non-revoked key by its hash.
   *
   * @param keyHash lowercase SHA-256 hex of the raw key
   * @return the owner and key id, or empty for unknown or revoked keys
   */
  public Optional<AuthenticatedOwner> findActiveByHash(String keyHash) {
    return jdbc.sql(FIND_ACTIVE_BY_HASH)
        .param("hash", keyHash)
        .query((rs, n) -> new AuthenticatedOwner(rs.getLong("owner_id"), rs.getLong("id")))
        .optional();
  }
}
