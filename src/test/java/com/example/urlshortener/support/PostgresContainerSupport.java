package com.example.urlshortener.support;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Single Postgres container shared by every integration test in the JVM (design §8.2:
 * Testcontainers {@code postgres:16.4-alpine}). Testcontainers' Ryuk sidecar removes it when the
 * JVM exits.
 */
public final class PostgresContainerSupport {

  /** Image required by the design. */
  public static final DockerImageName IMAGE = DockerImageName.parse("postgres:16.4-alpine");

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(IMAGE).withDatabaseName("urlshortener");

  private PostgresContainerSupport() {}

  /**
   * Returns the shared container, starting it on first use.
   *
   * @return the running container
   */
  public static synchronized PostgreSQLContainer<?> postgres() {
    if (!POSTGRES.isRunning()) {
      POSTGRES.start();
    }
    return POSTGRES;
  }
}
