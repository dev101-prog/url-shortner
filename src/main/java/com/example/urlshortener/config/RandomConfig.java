package com.example.urlshortener.config;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** CSPRNG for short-code generation (URL-FR-1.2); a bean so tests can inject their own. */
@Configuration(proxyBeanMethods = false)
public class RandomConfig {

  /**
   * Code-generation randomness.
   *
   * @return a new {@link SecureRandom}
   */
  @Bean
  public SecureRandom secureRandom() {
    return new SecureRandom();
  }
}
