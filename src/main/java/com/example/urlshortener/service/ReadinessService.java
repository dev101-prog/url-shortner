package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.repository.DatabaseHealthRepository;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * URL-FR-8.2 readiness policy (design §6.8, ADR 0009): ready if and only if Postgres answers within
 * {@code app.health.db-timeout}. Cache failures degrade to the DB and do not affect readiness.
 */
@Service
public final class ReadinessService {

  private final DatabaseHealthRepository database;
  private final Duration timeout;

  /**
   * Creates the service.
   *
   * @param database database health repository
   * @param props application properties
   */
  public ReadinessService(DatabaseHealthRepository database, AppProperties props) {
    this.database = database;
    this.timeout = props.health().dbTimeout();
  }

  /**
   * Checks the only hard dependency.
   *
   * @return true when the database is reachable
   */
  public boolean isDatabaseUp() {
    return database.isReachable(timeout);
  }
}
