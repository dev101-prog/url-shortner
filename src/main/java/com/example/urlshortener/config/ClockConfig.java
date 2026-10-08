package com.example.urlshortener.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The only source of time in the application (design D9, §8.4 rule 5). Everything else injects
 * {@link Clock} so expiry and stats logic can be tested with a fixed clock.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

  /**
   * UTC system clock (PRD A10, R12).
   *
   * @return the application clock
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
