package com.example.urlshortener.config;

import com.example.urlshortener.infra.RandomBase62CodeGenerator;
import com.example.urlshortener.service.port.CodeGenerator;
import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** CSPRNG and code generator for short codes (URL-FR-1.2, scenario B3). */
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

  /**
   * Random base62 code generator (design §3.4) behind the {@link CodeGenerator} port.
   *
   * @param random CSPRNG
   * @param props application properties ({@code app.links.code-length})
   * @return generator
   */
  @Bean
  public CodeGenerator codeGenerator(SecureRandom random, AppProperties props) {
    return new RandomBase62CodeGenerator(random, props.links().codeLength());
  }
}
