package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.urlshortener.config.CacheConfig;
import com.example.urlshortener.infra.CaffeineRateLimiter;
import com.example.urlshortener.service.port.RateLimiter.Decision;
import com.example.urlshortener.support.MutableClock;
import com.example.urlshortener.support.TestProperties;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CaffeineRateLimiterTest {

  /** 21:00:15 UTC, 45 s before the window ends. */
  private static final Instant T = Instant.parse("2026-10-07T21:00:15Z");

  private final MutableClock clock = new MutableClock(T);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final CaffeineRateLimiter limiter =
      new CaffeineRateLimiter(
          new CacheConfig().rateLimitCounters(TestProperties.defaults()), clock, meters);

  @Test
  void nfr4_3_allowsUpToTheLimitThenDeniesWithRetryAfterToWindowEnd() {
    for (int i = 0; i < 3; i++) {
      assertThat(limiter.tryAcquire("create:7", 3)).isInstanceOf(Decision.Allowed.class);
    }

    assertThat(limiter.tryAcquire("create:7", 3)).isEqualTo(new Decision.Denied(45));
    assertThat(meters.counter("urlshortener.ratelimit.denied", "bucket", "create").count())
        .isEqualTo(1.0);
  }

  @Test
  void nfr4_3_newWindowResetsTheCount() {
    limiter.tryAcquire("create:7", 1);
    assertThat(limiter.tryAcquire("create:7", 1)).isInstanceOf(Decision.Denied.class);

    clock.advance(Duration.ofSeconds(45));

    assertThat(limiter.tryAcquire("create:7", 1)).isInstanceOf(Decision.Allowed.class);
  }

  @Test
  void fr3_7_bucketsAreIndependent() {
    limiter.tryAcquire("redirect:aaa", 1);

    assertThat(limiter.tryAcquire("redirect:bbb", 1)).isInstanceOf(Decision.Allowed.class);
    assertThat(limiter.tryAcquire("create:aaa", 1)).isInstanceOf(Decision.Allowed.class);
    assertThat(limiter.tryAcquire("redirect:aaa", 1)).isEqualTo(new Decision.Denied(45));
    assertThat(meters.counter("urlshortener.ratelimit.denied", "bucket", "redirect").count())
        .isEqualTo(1.0);
  }

  @Test
  void retryAfterIsAtLeastOneSecondAtWindowEnd() {
    clock.set(Instant.parse("2026-10-07T21:00:59Z"));
    limiter.tryAcquire("plain", 0);

    assertThat(limiter.tryAcquire("plain", 0)).isEqualTo(new Decision.Denied(1));
  }

  @Test
  @SuppressWarnings("unchecked")
  void counterStoreFailureAllowsAndCounts() {
    Cache<String, AtomicInteger> broken = mock(Cache.class);
    when(broken.get(anyString(), any())).thenThrow(new IllegalStateException("boom"));
    CaffeineRateLimiter degraded = new CaffeineRateLimiter(broken, clock, meters);

    assertThat(degraded.tryAcquire("create:7", 0)).isInstanceOf(Decision.Allowed.class);
    assertThat(meters.counter("urlshortener.cache.errors").count()).isEqualTo(1.0);
  }
}
