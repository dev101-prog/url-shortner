package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.repository.StatsRepository;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.domain.LinkStats;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Owner-only click statistics (design flow §6.7, URL-FR-7.5, 7.7). Days are UTC (PRD A10). */
@Service
public final class StatsService {

  private final LinkRepository links;
  private final StatsRepository stats;
  private final Clock clock;
  private final int defaultDays;
  private final int maxDays;

  /**
   * Creates the service.
   *
   * @param links link repository
   * @param stats stats repository
   * @param props application properties
   * @param clock application clock
   */
  public StatsService(
      LinkRepository links, StatsRepository stats, AppProperties props, Clock clock) {
    this.links = links;
    this.stats = stats;
    this.clock = clock;
    this.defaultDays = props.stats().defaultDays();
    this.maxDays = props.stats().maxDays();
  }

  /**
   * URL-FR-7.5 / 7.7: validates the inclusive UTC date range (default: the last 30 days ending
   * today; {@code from > to} or a span over 365 days is 422 {@code INVALID_DATE_RANGE}), checks
   * ownership (404, never 403), then aggregates with zero-filled days. {@code top_countries} is
   * {@code null} when no event in range has a country.
   *
   * @param owner authenticated caller
   * @param code short code
   * @param from first day (inclusive) or {@code null}
   * @param to last day (inclusive) or {@code null}
   * @return statistics
   */
  public LinkStats stats(AuthenticatedOwner owner, String code, LocalDate from, LocalDate to) {
    return stats(owner, code, from, to, false);
  }

  /**
   * URL-FR-7.9 (scenario A3): like {@link #stats(AuthenticatedOwner, String, LocalDate,
   * LocalDate)}; with {@code excludeBots} every number is computed over {@code is_bot = false}
   * events only. URL-FR-7.10: {@code unique_visitors_estimate} is the distinct IP hashes over the
   * whole range, never the sum of daily values.
   *
   * @param owner authenticated caller
   * @param code short code
   * @param from first day (inclusive) or {@code null}
   * @param to last day (inclusive) or {@code null}
   * @param excludeBots count human clicks only
   * @return statistics
   */
  public LinkStats stats(
      AuthenticatedOwner owner, String code, LocalDate from, LocalDate to, boolean excludeBots) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate end = to != null ? to : today;
    LocalDate start = from != null ? from : end.minusDays(defaultDays - 1L);
    validateRange(start, end);

    Link link =
        links
            .findByCode(code)
            .filter(l -> l.ownerId() == owner.ownerId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    Instant fromInclusive = start.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant toExclusive = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    long id = link.id();
    LinkStats.ClickTotals totals =
        excludeBots
            ? stats.humanTotals(id, fromInclusive, toExclusive)
            : stats.totals(id, fromInclusive, toExclusive);
    List<LinkStats.CountryClicks> countries =
        excludeBots
            ? stats.humanTopCountries(id, fromInclusive, toExclusive)
            : stats.topCountries(id, fromInclusive, toExclusive);
    List<LinkStats.DailyClicks> days =
        excludeBots
            ? stats.humanClicksPerDay(id, fromInclusive, toExclusive)
            : stats.clicksPerDay(id, fromInclusive, toExclusive);
    List<LinkStats.ReferrerClicks> referrers =
        excludeBots
            ? stats.humanTopReferrers(id, fromInclusive, toExclusive)
            : stats.topReferrers(id, fromInclusive, toExclusive);

    return new LinkStats(
        link.code(),
        start,
        end,
        totals.total(),
        totals.total() - totals.bots(),
        totals.bots(),
        totals.uniqueVisitors(),
        zeroFill(start, end, days),
        referrers,
        countries.isEmpty() ? null : countries);
  }

  private void validateRange(LocalDate start, LocalDate end) {
    if (start.isAfter(end)) {
      throw new ApiException(
          ErrorCode.INVALID_DATE_RANGE,
          "from must not be after to.",
          Map.of("from", start.toString(), "to", end.toString()));
    }
    long days = ChronoUnit.DAYS.between(start, end) + 1;
    if (days > maxDays) {
      throw new ApiException(
          ErrorCode.INVALID_DATE_RANGE,
          "Date range must span at most " + maxDays + " days.",
          Map.of("max_days", maxDays));
    }
  }

  private static List<LinkStats.DailyClicks> zeroFill(
      LocalDate start, LocalDate end, List<LinkStats.DailyClicks> days) {
    Map<LocalDate, LinkStats.DailyClicks> byDate =
        days.stream().collect(Collectors.toMap(LinkStats.DailyClicks::date, Function.identity()));
    List<LinkStats.DailyClicks> filled = new ArrayList<>();
    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      filled.add(byDate.getOrDefault(d, new LinkStats.DailyClicks(d, 0, 0, 0)));
    }
    return filled;
  }
}
