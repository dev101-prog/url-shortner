package com.example.urlshortener.service;

import com.example.urlshortener.repository.ApiKeyRepository;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.github.benmanes.caffeine.cache.Cache;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * URL-NFR-4.1 / 4.2: authenticates raw API keys by their SHA-256 hash (design D7). Only positive
 * results are cached, for {@code app.cache.api-key-ttl} (30 s), so a revocation takes effect within
 * that TTL and unknown keys always hit the database.
 */
@Service
public final class ApiKeyService {

  private final ApiKeyRepository repository;
  private final Cache<String, AuthenticatedOwner> cache;

  /**
   * Creates the service.
   *
   * @param repository API key repository
   * @param cache {@code apiKeys} cache (design §3.5)
   */
  public ApiKeyService(ApiKeyRepository repository, Cache<String, AuthenticatedOwner> cache) {
    this.repository = repository;
    this.cache = cache;
  }

  /**
   * URL-NFR-4.1: resolves a raw key to its owner; revoked and unknown keys are rejected.
   *
   * @param rawKey raw {@code X-API-Key} value (never logged or stored)
   * @return the owner, or empty when the key is missing, unknown or revoked
   */
  public Optional<AuthenticatedOwner> authenticate(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      return Optional.empty();
    }
    String hash = sha256Hex(rawKey);
    AuthenticatedOwner cached = cache.getIfPresent(hash);
    if (cached != null) {
      return Optional.of(cached);
    }
    Optional<AuthenticatedOwner> found = repository.findActiveByHash(hash);
    found.ifPresent(owner -> cache.put(hash, owner));
    return found;
  }

  /**
   * Lowercase SHA-256 hex, the format stored in {@code api_keys.key_hash}.
   *
   * @param rawKey raw key
   * @return 64-char hex digest
   */
  static String sha256Hex(String rawKey) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha256.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
