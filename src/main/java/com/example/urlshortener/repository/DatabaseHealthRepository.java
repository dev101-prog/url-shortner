package com.example.urlshortener.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Database reachability for the readiness probe (design §6.8). */
@Repository
public class DatabaseHealthRepository {

  private static final Logger LOG = LoggerFactory.getLogger(DatabaseHealthRepository.class);

  private final DataSource dataSource;

  /**
   * Creates the repository.
   *
   * @param dataSource application data source
   */
  public DatabaseHealthRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * URL-FR-8.2: borrows a connection and validates it (the driver runs a trivial query) within the
   * timeout. Never throws.
   *
   * @param timeout validation timeout (rounded up to whole seconds, at least 1 s)
   * @return true when the database answered in time
   */
  public boolean isReachable(Duration timeout) {
    int seconds = (int) Math.max(1, (timeout.toMillis() + 999) / 1000);
    try (Connection connection = dataSource.getConnection()) {
      return connection.isValid(seconds);
    } catch (SQLException | RuntimeException e) {
      LOG.warn("Readiness database check failed: {}", e.getClass().getSimpleName());
      return false;
    }
  }
}
