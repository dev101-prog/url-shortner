package com.example.urlshortener.config;

import com.example.urlshortener.service.domain.CachedLink;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Per-entry TTL for the {@code links} cache (design §3.5, URL-FR-3.4): {@code min(app.cache.ttl,
 * expiresAt - now)} while the link's expiry is still in the future, so a cached active link is
 * never served past its expiry without re-evaluation. Entries whose expiry has already passed (or
 * that never expire) use {@code app.cache.ttl}: status is always evaluated at request time, and an
 * expired link can never become active again.
 */
public final class LinkCacheExpiry implements Expiry<String, CachedLink> {

  private final Duration ttl;
  private final Clock clock;

  /**
   * Creates the expiry policy.
   *
   * @param ttl maximum TTL ({@code app.cache.ttl})
   * @param clock application clock
   */
  public LinkCacheExpiry(Duration ttl, Clock clock) {
    this.ttl = ttl;
    this.clock = clock;
  }

  /**
   * TTL for one entry.
   *
   * @param link cached link
   * @return time to live
   */
  public Duration ttlFor(CachedLink link) {
    Instant expiresAt = link.expiresAt();
    Instant now = clock.instant();
    if (expiresAt != null && now.isBefore(expiresAt)) {
      Duration untilExpiry = Duration.between(now, expiresAt);
      return untilExpiry.compareTo(ttl) < 0 ? untilExpiry : ttl;
    }
    return ttl;
  }

  @Override
  public long expireAfterCreate(String code, CachedLink link, long currentTime) {
    return ttlFor(link).toNanos();
  }

  @Override
  public long expireAfterUpdate(
      String code, CachedLink link, long currentTime, long currentDuration) {
    return ttlFor(link).toNanos();
  }

  @Override
  public long expireAfterRead(
      String code, CachedLink link, long currentTime, long currentDuration) {
    return currentDuration;
  }
}
