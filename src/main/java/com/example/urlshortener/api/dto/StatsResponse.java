package com.example.urlshortener.api.dto;

import com.example.urlshortener.service.domain.LinkStats;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/v1/links/{code}/stats} response (design §5.3).
 *
 * @param code short code
 * @param from first day (inclusive, UTC)
 * @param to last day (inclusive, UTC)
 * @param totalClicks clicks in range
 * @param humanClicks clicks not flagged as bot
 * @param botClicks clicks flagged as bot
 * @param uniqueVisitorsEstimate distinct IP hashes in range
 * @param clicksPerDay one entry per day, zero-filled
 * @param topReferrers up to 10 referrers
 * @param topCountries up to 10 countries, or {@code null}
 */
public record StatsResponse(
    String code,
    LocalDate from,
    LocalDate to,
    long totalClicks,
    long humanClicks,
    long botClicks,
    long uniqueVisitorsEstimate,
    List<Day> clicksPerDay,
    List<Referrer> topReferrers,
    List<Country> topCountries) {

  /** Copies the lists; {@code topCountries} may stay null. */
  public StatsResponse {
    clicksPerDay = List.copyOf(clicksPerDay);
    topReferrers = List.copyOf(topReferrers);
    topCountries = topCountries == null ? null : List.copyOf(topCountries);
  }

  /**
   * Maps the domain statistics.
   *
   * @param s statistics
   * @return response
   */
  public static StatsResponse from(LinkStats s) {
    return new StatsResponse(
        s.code(),
        s.from(),
        s.to(),
        s.totalClicks(),
        s.humanClicks(),
        s.botClicks(),
        s.uniqueVisitorsEstimate(),
        s.clicksPerDay().stream()
            .map(d -> new Day(d.date(), d.clicks(), d.humanClicks(), d.uniqueVisitors()))
            .toList(),
        s.topReferrers().stream().map(r -> new Referrer(r.referrer(), r.clicks())).toList(),
        s.topCountries() == null
            ? null
            : s.topCountries().stream().map(c -> new Country(c.country(), c.clicks())).toList());
  }

  /**
   * One UTC day.
   *
   * @param date day
   * @param clicks clicks
   * @param humanClicks non-bot clicks
   * @param uniqueVisitors distinct IP hashes that day
   */
  public record Day(LocalDate date, long clicks, long humanClicks, long uniqueVisitors) {}

  /**
   * Referrer count.
   *
   * @param referrer host or {@code (direct)}
   * @param clicks clicks
   */
  public record Referrer(String referrer, long clicks) {}

  /**
   * Country count.
   *
   * @param country ISO-3166 alpha-2
   * @param clicks clicks
   */
  public record Country(String country, long clicks) {}
}
