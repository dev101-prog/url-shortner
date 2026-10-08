package com.example.probe;

import static org.mockito.Mockito.mock;

import com.example.urlshortener.service.domain.CachedLink;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Failure injection (design §8.2): a {@code links} cache whose every operation throws. */
@TestConfiguration(proxyBeanMethods = false)
public class FailingLinkCacheConfig {

  /**
   * Broken links cache.
   *
   * @return cache that throws on every call
   */
  @Bean
  @Primary
  @SuppressWarnings("unchecked")
  public Cache<String, CachedLink> failingLinksCache() {
    return mock(
        Cache.class,
        invocation -> {
          // invalidateAll stays usable so the IT base class can reset caches between classes
          if (invocation.getMethod().getDeclaringClass() == Cache.class
              && !"invalidateAll".equals(invocation.getMethod().getName())) {
            throw new IllegalStateException("injected cache failure");
          }
          return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
  }
}
