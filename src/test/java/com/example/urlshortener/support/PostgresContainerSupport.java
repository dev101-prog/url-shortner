package com.example.urlshortener.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Single Postgres container shared by every integration test in the JVM (design §8.2:
 * Testcontainers {@code postgres:16.4-alpine}). Testcontainers' Ryuk sidecar removes it when the
 * JVM exits.
 *
 * <p>Tests that need the demo seed use their own database inside the container so the seeded Flyway
 * history never collides with the unseeded schema used by the other integration tests (§8.5).
 */
public final class PostgresContainerSupport {

  /** Image required by the design. */
  public static final DockerImageName IMAGE = DockerImageName.parse("postgres:16.4-alpine");

  private static final Pattern DB_NAME = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(IMAGE)
          .withDatabaseName("urlshortener")
          .withCommand("postgres", "-c", "max_connections=300");

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

  /**
   * Returns a JDBC URL for a dedicated database in the shared container, creating it if needed.
   *
   * @param databaseName lowercase database name
   * @return JDBC URL of that database
   */
  public static synchronized String jdbcUrlForDatabase(String databaseName) {
    if (!DB_NAME.matcher(databaseName).matches()) {
      throw new IllegalArgumentException("invalid database name: " + databaseName);
    }
    PostgreSQLContainer<?> pg = postgres();
    try (Connection c =
            DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        PreparedStatement exists =
            c.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
      exists.setString(1, databaseName);
      try (ResultSet rs = exists.executeQuery()) {
        if (!rs.next()) {
          try (Statement create = c.createStatement()) {
            create.execute("CREATE DATABASE " + databaseName);
          }
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("could not create test database " + databaseName, e);
    }
    return pg.getJdbcUrl().replace("/urlshortener", "/" + databaseName);
  }
}
