package com.example.urlshortener.infra;

import com.example.urlshortener.service.port.RateLimiter;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fixed one-minute window rate limiter on Caffeine counters keyed {@code bucket:key:minute} (design
 * D8, §3.5). If the counter store fails the request is allowed and the failure is counted.
 */
@Component
public final class CaffeineRateLimiter implements RateLimiter {

  private static final Logger LOG = LoggerFactory.getLogger(CaffeineRateLimiter.class);
  private static final long WINDOW_SECONDS = Duration.ofMinutes(1).toSeconds();
  private static final Decision ALLOWED = new Decision.Allowed();

  private final Cache<String, AtomicInteger> counters;
  private final Clock clock;
  private final MeterRegistry meters;
  private final Map<String, Counter> deniedByBucket = new ConcurrentHashMap<>();
  private final Counter errors;

  /**
   * Creates the limiter.
   *
   * @param counters {@code rateLimits} cache
   * @param clock application clock
   * @param meters meter registry
   */
  public CaffeineRateLimiter(
      Cache<String, AtomicInteger> counters, Clock clock, MeterRegistry meters) {
    this.counters = counters;
    this.clock = clock;
    this.meters = meters;
    this.errors = meters.counter("urlshortener.cache.errors");
  }

  /** URL-NFR-4.3 / URL-FR-3.7: count the request and deny it above the per-minute limit. */
  @Override
  public Decision tryAcquire(String bucketKey, int limitPerMinute) {
    long epochSecond = clock.instant().getEpochSecond();
    long window = Math.floorDiv(epochSecond, WINDOW_SECONDS);
    int count;
    try {
      count = counters.get(bucketKey + ":" + window, k -> new AtomicInteger()).incrementAndGet();
    } catch (RuntimeException e) {
      errors.increment();
      LOG.warn("Rate limiter store failed; allowing request", e);
      return ALLOWED;
    }
    if (count <= limitPerMinute) {
      return ALLOWED;
    }
    denied(bucketKey).increment();
    return new Decision.Denied(WINDOW_SECONDS - Math.floorMod(epochSecond, WINDOW_SECONDS));
  }

  private Counter denied(String bucketKey) {
    int colon = bucketKey.indexOf(':');
    String bucket = colon < 0 ? bucketKey : bucketKey.substring(0, colon);
    return deniedByBucket.computeIfAbsent(
        bucket,
        b -> Counter.builder("urlshortener.ratelimit.denied").tag("bucket", b).register(meters));
  }
}
