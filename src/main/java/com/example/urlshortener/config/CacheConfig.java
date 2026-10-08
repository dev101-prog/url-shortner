package com.example.urlshortener.config;

import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.domain.CachedLink;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The four in-process Caffeine caches from design §3.5, sized and timed from {@code app.cache}. */
@Configuration(proxyBeanMethods = false)
public class CacheConfig {

  /** A fixed window is one minute; counters live for two windows (design §3.5: PT2M). */
  static final Duration RATE_LIMIT_COUNTER_TTL = Duration.ofMinutes(2);

  /**
   * {@code links}: code to {@link CachedLink} (URL-FR-3.4).
   *
   * @param props application properties
   * @param clock application clock
   * @return cache
   */
  @Bean
  public Cache<String, CachedLink> linksCache(AppProperties props, Clock clock) {
    return Caffeine.newBuilder()
        .maximumSize(props.cache().maxSize())
        .expireAfter(new LinkCacheExpiry(props.cache().ttl(), clock))
        .build();
  }

  /**
   * {@code missing}: negative cache of unknown codes (URL-FR-3.6).
   *
   * @param props application properties
   * @return cache
   */
  @Bean
  public Cache<String, Boolean> missingLinksCache(AppProperties props) {
    return Caffeine.newBuilder()
        .maximumSize(props.cache().maxSize())
        .expireAfterWrite(props.cache().negativeTtl())
        .build();
  }

  /**
   * {@code apiKeys}: key hash to owner, positive results only (URL-NFR-4.1).
   *
   * @param props application properties
   * @return cache
   */
  @Bean
  public Cache<String, AuthenticatedOwner> apiKeyCache(AppProperties props) {
    return Caffeine.newBuilder()
        .maximumSize(props.cache().apiKeyMaxSize())
        .expireAfterWrite(props.cache().apiKeyTtl())
        .build();
  }

  /**
   * {@code rateLimits}: {@code bucket:key:minute} to counter (design D8).
   *
   * @param props application properties
   * @return cache
   */
  @Bean
  public Cache<String, AtomicInteger> rateLimitCounters(AppProperties props) {
    return Caffeine.newBuilder()
        .maximumSize(props.rateLimit().maxBuckets())
        .expireAfterWrite(RATE_LIMIT_COUNTER_TTL)
        .build();
  }
}
