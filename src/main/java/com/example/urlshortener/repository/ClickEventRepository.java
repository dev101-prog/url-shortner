package com.example.urlshortener.repository;

import com.example.urlshortener.service.domain.ClickEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Batch persistence for click events (design §3.6, §4.5). Uses {@link JdbcOperations} because a
 * JDBC batch is required and {@code JdbcClient} has no batch API.
 */
@Repository
public class ClickEventRepository {

  static final String INSERT_CLICK_EVENT =
      """
      INSERT INTO click_events (link_id, clicked_at, referrer_host, user_agent, is_bot, ip_hash, country)
      VALUES (?, ?, ?, ?, ?, ?, ?);
      """;

  static final String INCREMENT_CLICK_COUNT =
      """
      UPDATE links SET click_count = click_count + ? WHERE id = ?;
      """;

  private final JdbcOperations jdbc;
  private final TransactionOperations tx;

  /**
   * Creates the repository.
   *
   * @param jdbc JDBC operations (batch support)
   * @param tx transaction operations
   */
  public ClickEventRepository(JdbcOperations jdbc, TransactionOperations tx) {
    this.jdbc = jdbc;
    this.tx = tx;
  }

  /**
   * URL-FR-7.2, 7.6: inserts the events and increments {@code links.click_count} per link in one
   * transaction, so the count and the events never drift. All or nothing.
   *
   * @param events events to persist (may be empty)
   */
  public void saveBatch(List<ClickEvent> events) {
    if (events.isEmpty()) {
      return;
    }
    List<ClickEvent> batch = List.copyOf(events);
    Map<Long, Long> perLink = new LinkedHashMap<>();
    for (ClickEvent e : batch) {
      perLink.merge(e.linkId(), 1L, Long::sum);
    }
    List<Object[]> increments = new ArrayList<>(perLink.size());
    perLink.forEach((linkId, n) -> increments.add(new Object[] {n, linkId}));

    tx.executeWithoutResult(
        status -> {
          jdbc.batchUpdate(
              INSERT_CLICK_EVENT,
              batch,
              batch.size(),
              (ps, e) -> {
                ps.setLong(1, e.linkId());
                ps.setObject(2, SqlTypes.toOffsetDateTime(e.clickedAt()));
                ps.setString(3, e.referrerHost());
                ps.setString(4, e.userAgent());
                ps.setBoolean(5, e.bot());
                ps.setString(6, e.ipHash());
                ps.setString(7, e.country());
              });
          jdbc.batchUpdate(INCREMENT_CLICK_COUNT, increments);
        });
  }
}
