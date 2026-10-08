package com.example.urlshortener.infra;

import com.example.urlshortener.service.domain.CachedLink;
import com.example.urlshortener.service.port.LinkCache;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Caffeine implementation of {@link LinkCache} (design §3.5). Every call is wrapped so that a cache
 * failure is counted in {@code urlshortener.cache.errors} and behaves as a miss (URL-FR-3.5).
 */
@Component
public final class CaffeineLinkCache implements LinkCache {

  private static final Logger LOG = LoggerFactory.getLogger(CaffeineLinkCache.class);

  private final Cache<String, CachedLink> links;
  private final Cache<String, Boolean> missing;
  private final Counter hits;
  private final Counter misses;
  private final Counter negativeHits;
  private final Counter errors;

  /**
   * Creates the cache adapter.
   *
   * @param links {@code links} cache
   * @param missing {@code missing} negative cache
   * @param meters meter registry
   */
  public CaffeineLinkCache(
      Cache<String, CachedLink> links, Cache<String, Boolean> missing, MeterRegistry meters) {
    this.links = links;
    this.missing = missing;
    this.hits = requests(meters, "hit");
    this.misses = requests(meters, "miss");
    this.negativeHits = meters.counter("urlshortener.negative_cache.hits");
    this.errors = meters.counter("urlshortener.cache.errors");
  }

  private static Counter requests(MeterRegistry meters, String result) {
    return Counter.builder("urlshortener.cache.requests")
        .tag("cache", "links")
        .tag("result", result)
        .register(meters);
  }

  /** URL-FR-3.4: cache-first lookup; errors behave as a miss. */
  @Override
  public Optional<CachedLink> get(String code) {
    try {
      CachedLink link = links.getIfPresent(code);
      (link == null ? misses : hits).increment();
      return Optional.ofNullable(link);
    } catch (RuntimeException e) {
      failed("get", code, e);
      return Optional.empty();
    }
  }

  @Override
  public void put(String code, CachedLink link) {
    try {
      links.put(code, link);
    } catch (RuntimeException e) {
      failed("put", code, e);
    }
  }

  /** URL-FR-6.3: on failure the bounded TTL caps staleness; the failure is logged and metered. */
  @Override
  public void evict(String code) {
    try {
      links.invalidate(code);
    } catch (RuntimeException e) {
      failed("evict", code, e);
    }
  }

  /** URL-FR-3.6: remember unknown codes for {@code app.cache.negative-ttl}. */
  @Override
  public void markMissing(String code) {
    try {
      missing.put(code, Boolean.TRUE);
    } catch (RuntimeException e) {
      failed("markMissing", code, e);
    }
  }

  @Override
  public boolean isKnownMissing(String code) {
    try {
      boolean known = missing.getIfPresent(code) != null;
      if (known) {
        negativeHits.increment();
      }
      return known;
    } catch (RuntimeException e) {
      failed("isKnownMissing", code, e);
      return false;
    }
  }

  @Override
  public void clearMissing(String code) {
    try {
      missing.invalidate(code);
    } catch (RuntimeException e) {
      failed("clearMissing", code, e);
    }
  }

  private void failed(String operation, String code, RuntimeException e) {
    errors.increment();
    LOG.warn("Link cache {} failed for code {}; degrading to database", operation, code, e);
  }
}
