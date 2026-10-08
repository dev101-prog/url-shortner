package com.example.urlshortener.service.port;

import com.example.urlshortener.service.domain.CachedLink;
import java.util.Optional;

/**
 * Hot-path link cache with a negative cache (design §3.5; URL-FR-3.4, 3.5, 3.6). Implementations
 * swallow internal errors, record a metric and behave as a miss (degrade, don't fail).
 */
public interface LinkCache {

  /**
   * Looks up a code.
   *
   * @param code short code (case-sensitive)
   * @return the cached entry, or empty on miss or cache error
   */
  Optional<CachedLink> get(String code);

  /**
   * Stores an entry.
   *
   * @param code short code
   * @param link entry to cache
   */
  void put(String code, CachedLink link);

  /**
   * Removes an entry (URL-FR-6.3).
   *
   * @param code short code
   */
  void evict(String code);

  /**
   * Records that a code does not exist (URL-FR-3.6).
   *
   * @param code short code
   */
  void markMissing(String code);

  /**
   * Checks the negative cache.
   *
   * @param code short code
   * @return true if the code is known not to exist
   */
  boolean isKnownMissing(String code);

  /**
   * Clears a negative-cache entry when the code is created.
   *
   * @param code short code
   */
  void clearMissing(String code);
}
