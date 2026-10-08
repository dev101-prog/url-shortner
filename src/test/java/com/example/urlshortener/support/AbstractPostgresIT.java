package com.example.urlshortener.support;

import com.github.benmanes.caffeine.cache.Cache;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for {@code @SpringBootTest} integration tests on the shared Testcontainers Postgres. Each
 * test class starts from a clean schema (Flyway clean + migrate, design §8.5) and empty in-process
 * caches (Spring reuses contexts across classes); the demo seed is never loaded here.
 */
@ActiveProfiles("test")
public abstract class AbstractPostgresIT {

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> PostgresContainerSupport.postgres().getJdbcUrl());
    registry.add(
        "spring.datasource.username", () -> PostgresContainerSupport.postgres().getUsername());
    registry.add(
        "spring.datasource.password", () -> PostgresContainerSupport.postgres().getPassword());
  }

  @BeforeAll
  static void cleanSchema(@Autowired Flyway flyway, @Autowired ApplicationContext context) {
    flyway.clean();
    flyway.migrate();
    for (String name : context.getBeanNamesForType(Cache.class)) {
      ((Cache<?, ?>) context.getBean(name)).invalidateAll();
    }
  }
}
