package com.example.urlshortener.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.SqlParameterValue;

/** Instant to/from TIMESTAMPTZ conversion; every timestamp is UTC (PRD A10, R12). */
final class SqlTypes {

  private SqlTypes() {}

  /**
   * Typed TIMESTAMPTZ parameter (typed so that {@code null} binds correctly).
   *
   * @param instant value or {@code null}
   * @return JDBC parameter value
   */
  static SqlParameterValue timestamptz(Instant instant) {
    return new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE, toOffsetDateTime(instant));
  }

  /**
   * Converts an instant to a UTC {@link OffsetDateTime}, the java.time type PgJDBC accepts.
   *
   * @param instant value or {@code null}
   * @return UTC offset date-time or {@code null}
   */
  static OffsetDateTime toOffsetDateTime(Instant instant) {
    return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  /**
   * Reads a nullable TIMESTAMPTZ column.
   *
   * @param rs result set
   * @param column column label
   * @return instant or {@code null}
   * @throws SQLException on JDBC errors
   */
  static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
