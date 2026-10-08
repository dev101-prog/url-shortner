package com.example.urlshortener.service.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Aggregated click statistics for one link over an inclusive UTC date range (URL-FR-7.5, 7.7).
 *
 * @param code short code
 * @param from first day of the range (inclusive, UTC)
 * @param to last day of the range (inclusive, UTC)
 * @param totalClicks clicks in range
 * @param humanClicks clicks not flagged as bot
 * @param botClicks clicks flagged as bot
 * @param uniqueVisitorsEstimate distinct IP hashes in range
 * @param clicksPerDay one entry per day in range, zero-filled
 * @param topReferrers up to 10 referrers
 * @param topCountries up to 10 countries, or {@code null} when no event has a country
 */
public record LinkStats(
    String code,
    LocalDate from,
    LocalDate to,
    long totalClicks,
    long humanClicks,
    long botClicks,
    long uniqueVisitorsEstimate,
    List<DailyClicks> clicksPerDay,
    List<ReferrerClicks> topReferrers,
    List<CountryClicks> topCountries) {

  /** Copies the lists so the record stays immutable; {@code topCountries} may stay null. */
  public LinkStats {
    clicksPerDay = List.copyOf(clicksPerDay);
    topReferrers = List.copyOf(topReferrers);
    topCountries = topCountries == null ? null : List.copyOf(topCountries);
  }

  /**
   * Clicks on one UTC day.
   *
   * @param date the day
   * @param clicks all clicks
   * @param humanClicks clicks not flagged as bot
   * @param uniqueVisitors distinct IP hashes that day
   */
  public record DailyClicks(LocalDate date, long clicks, long humanClicks, long uniqueVisitors) {}

  /**
   * Clicks per referrer host ({@code (direct)} when absent).
   *
   * @param referrer referrer host
   * @param clicks clicks
   */
  public record ReferrerClicks(String referrer, long clicks) {}

  /**
   * Range-level aggregates.
   *
   * @param total all clicks
   * @param bots clicks flagged as bot
   * @param uniqueVisitors distinct IP hashes over the whole range
   */
  public record ClickTotals(long total, long bots, long uniqueVisitors) {}

  /**
   * Clicks per country.
   *
   * @param country ISO-3166 alpha-2 code
   * @param clicks clicks
   */
  public record CountryClicks(String country, long clicks) {}
}
