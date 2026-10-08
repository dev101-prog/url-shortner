package com.example.urlshortener.repository;

import com.example.urlshortener.service.domain.LinkStats;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Click aggregates for one link over {@code [from, to)} in UTC (design §4.5); all four queries use
 * {@code idx_click_events_link_time}.
 */
@Repository
public class StatsRepository {

  static final String TOTALS =
      """
      SELECT count(*)                                   AS total,
             count(*) FILTER (WHERE is_bot)             AS bots,
             count(DISTINCT ip_hash)                    AS unique_visitors
      FROM click_events WHERE link_id = :linkId AND clicked_at >= :from AND clicked_at < :to;
      """;

  static final String PER_DAY =
      """
      SELECT (clicked_at AT TIME ZONE 'UTC')::date      AS day,
             count(*)                                   AS clicks,
             count(*) FILTER (WHERE NOT is_bot)         AS human_clicks,
             count(DISTINCT ip_hash)                    AS unique_visitors
      FROM click_events WHERE link_id = :linkId AND clicked_at >= :from AND clicked_at < :to
      GROUP BY 1 ORDER BY 1;
      """;

  static final String TOP_REFERRERS =
      """
      SELECT COALESCE(referrer_host, '(direct)') AS referrer, count(*) AS clicks
      FROM click_events WHERE link_id = :linkId AND clicked_at >= :from AND clicked_at < :to
      GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 10;
      """;

  static final String TOP_COUNTRIES =
      """
      SELECT country, count(*) AS clicks
      FROM click_events WHERE link_id = :linkId AND clicked_at >= :from AND clicked_at < :to
        AND country IS NOT NULL
      GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 10;
      """;

  // URL-FR-7.9 (scenario A3): the same four §4.5 queries restricted to human clicks. The default
  // queries above are untouched, so responses without exclude_bots stay byte-identical.

  static final String HUMAN_TOTALS =
      """
      SELECT count(*)                                   AS total,
             count(*) FILTER (WHERE is_bot)             AS bots,
             count(DISTINCT ip_hash)                    AS unique_visitors
      FROM click_events WHERE link_id = :linkId AND clicked_at >= :from AND clicked_at < :to
        AND NOT is_bot;
      """;

  static final String HUMAN_PER_DAY =
      """
      SELECT (clicked_at AT TIME ZONE 'UTC')::date      AS day,
             count(*)                                   AS clicks,
             count(*) FILTER (WHERE NOT is_bot)         AS human_clicks,
             count(DISTINCT ip_hash)                    AS unique_visitors
      FROM click_events WHERE link_id = :linkId AND clicked_at >= :from AND clicked_at < :to
        AND NOT is_bot
      GROUP BY 1 ORDER BY 1;
      """;

  static final String HUMAN_TOP_REFERRERS =
      """
      SELECT COALESCE(referrer_host, '(direct)') AS referrer, count(*) AS clicks
      FROM click_events WHERE link_id = :linkId AND clicked_at >= :from AND clicked_at < :to
        AND NOT is_bot
      GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 10;
      """;

  static final String HUMAN_TOP_COUNTRIES =
      """
      SELECT country, count(*) AS clicks
      FROM click_events WHERE link_id = :linkId AND clicked_at >= :from AND clicked_at < :to
        AND country IS NOT NULL AND NOT is_bot
      GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 10;
      """;

  private final JdbcClient jdbc;

  /**
   * Creates the repository.
   *
   * @param jdbc JDBC client
   */
  public StatsRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * URL-FR-7.5 / 7.7: totals, bot count and distinct IP hashes.
   *
   * @param linkId link id
   * @param from inclusive start (UTC)
   * @param to exclusive end (UTC)
   * @return totals
   */
  public LinkStats.ClickTotals totals(long linkId, Instant from, Instant to) {
    return totals(TOTALS, linkId, from, to);
  }

  /**
   * URL-FR-7.9: totals over human clicks only ({@code bots} is always 0).
   *
   * @param linkId link id
   * @param from inclusive start (UTC)
   * @param to exclusive end (UTC)
   * @return totals
   */
  public LinkStats.ClickTotals humanTotals(long linkId, Instant from, Instant to) {
    return totals(HUMAN_TOTALS, linkId, from, to);
  }

  private LinkStats.ClickTotals totals(String sql, long linkId, Instant from, Instant to) {
    return query(sql, linkId, from, to)
        .query(
            (rs, n) ->
                new LinkStats.ClickTotals(
                    rs.getLong("total"), rs.getLong("bots"), rs.getLong("unique_visitors")))
        .single();
  }

  /**
   * URL-FR-7.5: per UTC day, only days that have clicks.
   *
   * @param linkId link id
   * @param from inclusive start (UTC)
   * @param to exclusive end (UTC)
   * @return days in ascending order
   */
  public List<LinkStats.DailyClicks> clicksPerDay(long linkId, Instant from, Instant to) {
    return clicksPerDay(PER_DAY, linkId, from, to);
  }

  /**
   * URL-FR-7.9: per-day counts over human clicks only.
   *
   * @param linkId link id
   * @param from inclusive start (UTC)
   * @param to exclusive end (UTC)
   * @return days in ascending order
   */
  public List<LinkStats.DailyClicks> humanClicksPerDay(long linkId, Instant from, Instant to) {
    return clicksPerDay(HUMAN_PER_DAY, linkId, from, to);
  }

  private List<LinkStats.DailyClicks> clicksPerDay(
      String sql, long linkId, Instant from, Instant to) {
    return query(sql, linkId, from, to)
        .query(
            (rs, n) ->
                new LinkStats.DailyClicks(
                    rs.getObject("day", LocalDate.class),
                    rs.getLong("clicks"),
                    rs.getLong("human_clicks"),
                    rs.getLong("unique_visitors")))
        .list();
  }

  /**
   * URL-FR-7.5: up to 10 referrer hosts, {@code (direct)} when absent.
   *
   * @param linkId link id
   * @param from inclusive start (UTC)
   * @param to exclusive end (UTC)
   * @return referrers by clicks descending, then name
   */
  public List<LinkStats.ReferrerClicks> topReferrers(long linkId, Instant from, Instant to) {
    return topReferrers(TOP_REFERRERS, linkId, from, to);
  }

  /**
   * URL-FR-7.9: top referrers over human clicks only.
   *
   * @param linkId link id
   * @param from inclusive start (UTC)
   * @param to exclusive end (UTC)
   * @return referrers by clicks descending, then name
   */
  public List<LinkStats.ReferrerClicks> humanTopReferrers(long linkId, Instant from, Instant to) {
    return topReferrers(HUMAN_TOP_REFERRERS, linkId, from, to);
  }

  private List<LinkStats.ReferrerClicks> topReferrers(
      String sql, long linkId, Instant from, Instant to) {
    return query(sql, linkId, from, to)
        .query(
            (rs, n) -> new LinkStats.ReferrerClicks(rs.getString("referrer"), rs.getLong("clicks")))
        .list();
  }

  /**
   * URL-FR-7.5: up to 10 countries (events without a country are ignored).
   *
   * @param linkId link id
   * @param from inclusive start (UTC)
   * @param to exclusive end (UTC)
   * @return countries by clicks descending, then code
   */
  public List<LinkStats.CountryClicks> topCountries(long linkId, Instant from, Instant to) {
    return topCountries(TOP_COUNTRIES, linkId, from, to);
  }

  /**
   * URL-FR-7.9: top countries over human clicks only.
   *
   * @param linkId link id
   * @param from inclusive start (UTC)
   * @param to exclusive end (UTC)
   * @return countries by clicks descending, then code
   */
  public List<LinkStats.CountryClicks> humanTopCountries(long linkId, Instant from, Instant to) {
    return topCountries(HUMAN_TOP_COUNTRIES, linkId, from, to);
  }

  private List<LinkStats.CountryClicks> topCountries(
      String sql, long linkId, Instant from, Instant to) {
    return query(sql, linkId, from, to)
        .query(
            (rs, n) -> new LinkStats.CountryClicks(rs.getString("country"), rs.getLong("clicks")))
        .list();
  }

  private JdbcClient.StatementSpec query(String sql, long linkId, Instant from, Instant to) {
    return jdbc.sql(sql)
        .param("linkId", linkId)
        .param("from", SqlTypes.timestamptz(from))
        .param("to", SqlTypes.timestamptz(to));
  }
}
