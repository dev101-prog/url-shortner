package com.example.probe;

import com.example.urlshortener.support.MutableClock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application clock with a {@link MutableClock} that starts at the real current time
 * (so DB defaults such as {@code created_at = now()} stay consistent). Lives outside the scanned
 * package so the real application never picks it up.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

  /**
   * Test clock.
   *
   * @return mutable clock
   */
  @Bean
  @Primary
  public MutableClock testClock() {
    return new MutableClock(Instant.now());
  }
}
