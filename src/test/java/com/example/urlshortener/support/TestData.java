package com.example.urlshortener.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Builders for integration-test rows (design §8.5: each test creates its own data). */
public final class TestData {

  private final JdbcClient jdbc;

  /**
   * Creates the helper.
   *
   * @param jdbc JDBC client of the test context
   */
  public TestData(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Inserts an owner.
   *
   * @param name unique owner name
   * @return owner id
   */
  public long owner(String name) {
    return jdbc.sql("INSERT INTO owners (name) VALUES (:name) RETURNING id")
        .param("name", name)
        .query(Long.class)
        .single();
  }

  /**
   * Inserts an API key, storing only its SHA-256 hash.
   *
   * @param ownerId owner id
   * @param rawKey raw key (test value only)
   * @param revoked whether the key is revoked
   * @return API key id
   */
  public long apiKey(long ownerId, String rawKey, boolean revoked) {
    return jdbc.sql(
            "INSERT INTO api_keys (owner_id, key_hash, key_prefix, label, revoked_at)"
                + " VALUES (:ownerId, :hash, :prefix, 'test key',"
                + " CASE WHEN :revoked THEN now() END) RETURNING id")
        .param("ownerId", ownerId)
        .param("hash", sha256Hex(rawKey))
        .param("prefix", rawKey.substring(0, Math.min(12, rawKey.length())))
        .param("revoked", revoked)
        .query(Long.class)
        .single();
  }

  /**
   * Lowercase SHA-256 hex of a string.
   *
   * @param raw input
   * @return 64-char hex
   */
  public static String sha256Hex(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
