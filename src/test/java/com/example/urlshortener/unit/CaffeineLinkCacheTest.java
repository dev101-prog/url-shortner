package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.urlshortener.config.CacheConfig;
import com.example.urlshortener.config.LinkCacheExpiry;
import com.example.urlshortener.infra.CaffeineLinkCache;
import com.example.urlshortener.service.domain.CachedLink;
import com.example.urlshortener.service.domain.LinkStatus;
import com.example.urlshortener.support.MutableClock;
import com.example.urlshortener.support.TestProperties;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CaffeineLinkCacheTest {

  private static final Instant NOW = Instant.parse("2026-10-07T21:00:00Z");
  private static final CachedLink ACTIVE =
      new CachedLink(1L, "https://a.io", LinkStatus.ACTIVE, null);

  private final MutableClock clock = new MutableClock(NOW);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final CacheConfig config = new CacheConfig();
  private final CaffeineLinkCache cache =
      new CaffeineLinkCache(
          config.linksCache(TestProperties.defaults(), clock),
          config.missingLinksCache(TestProperties.defaults()),
          meters);

  private double requests(String result) {
    return meters
        .counter("urlshortener.cache.requests", "cache", "links", "result", result)
        .count();
  }

  @Test
  void fr3_4_putGetEvictAndHitMissMetrics() {
    assertThat(cache.get("aB3dE7x")).isEmpty();
    cache.put("aB3dE7x", ACTIVE);
    assertThat(cache.get("aB3dE7x")).contains(ACTIVE);
    assertThat(cache.get("AB3DE7X")).isEmpty();
    cache.evict("aB3dE7x");
    assertThat(cache.get("aB3dE7x")).isEmpty();

    assertThat(requests("hit")).isEqualTo(1.0);
    assertThat(requests("miss")).isEqualTo(3.0);
  }

  @Test
  void fr3_6_negativeCacheMarkAndClear() {
    assertThat(cache.isKnownMissing("nope1234")).isFalse();
    cache.markMissing("nope1234");
    assertThat(cache.isKnownMissing("nope1234")).isTrue();
    cache.clearMissing("nope1234");
    assertThat(cache.isKnownMissing("nope1234")).isFalse();
    assertThat(meters.counter("urlshortener.negative_cache.hits").count()).isEqualTo(1.0);
  }

  @Test
  void fr3_4_ttlNeverExceedsAFutureExpiry() {
    LinkCacheExpiry expiry = new LinkCacheExpiry(Duration.ofMinutes(10), clock);

    assertThat(expiry.ttlFor(ACTIVE)).isEqualTo(Duration.ofMinutes(10));
    assertThat(expiry.ttlFor(withExpiry(NOW.plusSeconds(90)))).isEqualTo(Duration.ofSeconds(90));
    assertThat(expiry.ttlFor(withExpiry(NOW.plus(Duration.ofHours(1)))))
        .isEqualTo(Duration.ofMinutes(10));
    // already expired: cached for the normal TTL; status is evaluated per request
    assertThat(expiry.ttlFor(withExpiry(NOW.minusSeconds(1)))).isEqualTo(Duration.ofMinutes(10));
    assertThat(expiry.expireAfterCreate("c", withExpiry(NOW.plusSeconds(5)), 0L))
        .isEqualTo(Duration.ofSeconds(5).toNanos());
    assertThat(expiry.expireAfterUpdate("c", ACTIVE, 0L, 1L))
        .isEqualTo(Duration.ofMinutes(10).toNanos());
    assertThat(expiry.expireAfterRead("c", ACTIVE, 0L, 123L)).isEqualTo(123L);
  }

  @Test
  @SuppressWarnings("unchecked")
  void fr3_5_cacheErrorsDegradeToMissAndAreCounted() {
    Cache<String, CachedLink> brokenLinks = mock(Cache.class);
    Cache<String, Boolean> brokenMissing = mock(Cache.class);
    RuntimeException boom = new IllegalStateException("cache down");
    when(brokenLinks.getIfPresent(any())).thenThrow(boom);
    doThrow(boom).when(brokenLinks).put(any(), any());
    doThrow(boom).when(brokenLinks).invalidate(any());
    when(brokenMissing.getIfPresent(any())).thenThrow(boom);
    doThrow(boom).when(brokenMissing).put(any(), any());
    doThrow(boom).when(brokenMissing).invalidate(any());
    CaffeineLinkCache degraded = new CaffeineLinkCache(brokenLinks, brokenMissing, meters);

    assertThat(degraded.get("aB3dE7x")).isEmpty();
    degraded.put("aB3dE7x", ACTIVE);
    degraded.evict("aB3dE7x");
    degraded.markMissing("aB3dE7x");
    assertThat(degraded.isKnownMissing("aB3dE7x")).isFalse();
    degraded.clearMissing("aB3dE7x");

    assertThat(meters.counter("urlshortener.cache.errors").count()).isEqualTo(6.0);
  }

  private static CachedLink withExpiry(Instant expiresAt) {
    return new CachedLink(2L, "https://b.io", LinkStatus.ACTIVE, expiresAt);
  }
}
