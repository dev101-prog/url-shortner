package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.repository.StatsRepository;
import com.example.urlshortener.service.StatsService;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.domain.LinkStats;
import com.example.urlshortener.service.domain.LinkStatus;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import com.example.urlshortener.support.MutableClock;
import com.example.urlshortener.support.TestProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatsServiceTest {

  /** 2026-10-07 at 00:30 UTC: a timezone-naive "today" would be wrong west of UTC. */
  private static final Instant NOW = Instant.parse("2026-10-07T00:30:00Z");

  private static final LocalDate TODAY = LocalDate.parse("2026-10-07");
  private static final AuthenticatedOwner ALICE = new AuthenticatedOwner(1L, 1L);
  private static final AuthenticatedOwner BOB = new AuthenticatedOwner(2L, 2L);

  private final LinkRepository links = mock(LinkRepository.class);
  private final StatsRepository repo = mock(StatsRepository.class);
  private final StatsService service =
      new StatsService(links, repo, TestProperties.defaults(), new MutableClock(NOW));

  @BeforeEach
  void seedLink() {
    when(links.findByCode("aB3dE7x"))
        .thenReturn(
            Optional.of(
                new Link(
                    1L,
                    "aB3dE7x",
                    1L,
                    "https://a.io",
                    "https://a.io",
                    false,
                    LinkStatus.ACTIVE,
                    NOW.minusSeconds(86_400 * 7L),
                    null,
                    null,
                    6L)));
    when(repo.totals(anyLong(), any(), any())).thenReturn(new LinkStats.ClickTotals(6, 2, 3));
    when(repo.clicksPerDay(anyLong(), any(), any()))
        .thenReturn(
            List.of(
                new LinkStats.DailyClicks(LocalDate.parse("2026-10-04"), 2, 2, 2),
                new LinkStats.DailyClicks(TODAY, 1, 0, 1)));
    when(repo.topReferrers(anyLong(), any(), any()))
        .thenReturn(List.of(new LinkStats.ReferrerClicks("(direct)", 2)));
    when(repo.topCountries(anyLong(), any(), any()))
        .thenReturn(List.of(new LinkStats.CountryClicks("US", 3)));
  }

  private static void assertRangeError(ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
  }

  @Test
  void fr7_5_defaultIsLast30DaysEndingTodayUtc() {
    LinkStats stats = service.stats(ALICE, "aB3dE7x", null, null);

    assertThat(stats.from()).isEqualTo(LocalDate.parse("2026-09-08"));
    assertThat(stats.to()).isEqualTo(TODAY);
    assertThat(stats.clicksPerDay()).hasSize(30);
    verify(repo)
        .totals(
            eq(1L),
            eq(Instant.parse("2026-09-08T00:00:00Z")),
            eq(Instant.parse("2026-10-08T00:00:00Z")));
  }

  @Test
  void fr7_5_zeroFillsDays() {
    LinkStats stats =
        service.stats(
            ALICE, "aB3dE7x", LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-07"));

    assertThat(stats.clicksPerDay())
        .containsExactly(
            new LinkStats.DailyClicks(LocalDate.parse("2026-10-03"), 0, 0, 0),
            new LinkStats.DailyClicks(LocalDate.parse("2026-10-04"), 2, 2, 2),
            new LinkStats.DailyClicks(LocalDate.parse("2026-10-05"), 0, 0, 0),
            new LinkStats.DailyClicks(LocalDate.parse("2026-10-06"), 0, 0, 0),
            new LinkStats.DailyClicks(TODAY, 1, 0, 1));
  }

  @Test
  void fr7_5_totalsSplitHumansAndBots() {
    LinkStats stats = service.stats(ALICE, "aB3dE7x", null, null);

    assertThat(stats.totalClicks()).isEqualTo(6);
    assertThat(stats.botClicks()).isEqualTo(2);
    assertThat(stats.humanClicks()).isEqualTo(4);
    assertThat(stats.uniqueVisitorsEstimate()).isEqualTo(3);
    assertThat(stats.topReferrers()).containsExactly(new LinkStats.ReferrerClicks("(direct)", 2));
    assertThat(stats.topCountries()).containsExactly(new LinkStats.CountryClicks("US", 3));
  }

  @Test
  void fr7_5_noCountriesMeansNull() {
    when(repo.topCountries(anyLong(), any(), any())).thenReturn(List.of());

    assertThat(service.stats(ALICE, "aB3dE7x", null, null).topCountries()).isNull();
  }

  @Test
  void fr7_5_onlyFromOrOnlyToIsCompleted() {
    LinkStats fromOnly = service.stats(ALICE, "aB3dE7x", LocalDate.parse("2026-10-01"), null);
    LinkStats toOnly = service.stats(ALICE, "aB3dE7x", null, LocalDate.parse("2026-09-30"));

    assertThat(fromOnly.to()).isEqualTo(TODAY);
    assertThat(fromOnly.clicksPerDay()).hasSize(7);
    assertThat(toOnly.from()).isEqualTo(LocalDate.parse("2026-09-01"));
    assertThat(toOnly.clicksPerDay()).hasSize(30);
  }

  @Test
  void fr7_5_fromAfterTo_422() {
    assertRangeError(
        () ->
            service.stats(
                ALICE, "aB3dE7x", LocalDate.parse("2026-10-07"), LocalDate.parse("2026-10-06")));
  }

  @Test
  void fr7_5_rangeOver365_422() {
    LinkStats max = service.stats(ALICE, "aB3dE7x", LocalDate.parse("2025-10-08"), TODAY);
    assertThat(max.clicksPerDay()).hasSize(365);

    assertRangeError(() -> service.stats(ALICE, "aB3dE7x", LocalDate.parse("2025-10-07"), TODAY));
  }

  @Test
  void fr7_5_rangeIsValidatedBeforeOwnership() {
    assertRangeError(
        () ->
            service.stats(
                BOB, "unknown1", LocalDate.parse("2026-10-07"), LocalDate.parse("2026-01-01")));
    verifyNoInteractions(links);
  }

  @Test
  void fr7_5_nonOwnerAndUnknownAre404() {
    assertThatThrownBy(() -> service.stats(BOB, "aB3dE7x", null, null))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    assertThatThrownBy(() -> service.stats(ALICE, "unknown1", null, null))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  void fr7_9_excludeBotsUsesTheHumanOnlyQueries() {
    when(repo.humanTotals(anyLong(), any(), any())).thenReturn(new LinkStats.ClickTotals(4, 0, 2));
    when(repo.humanClicksPerDay(anyLong(), any(), any()))
        .thenReturn(List.of(new LinkStats.DailyClicks(TODAY, 4, 4, 2)));
    when(repo.humanTopReferrers(anyLong(), any(), any()))
        .thenReturn(List.of(new LinkStats.ReferrerClicks("twitter.com", 2)));
    when(repo.humanTopCountries(anyLong(), any(), any())).thenReturn(List.of());

    LinkStats stats = service.stats(ALICE, "aB3dE7x", null, null, true);

    assertThat(stats.totalClicks()).isEqualTo(4);
    assertThat(stats.botClicks()).isZero();
    assertThat(stats.humanClicks()).isEqualTo(4);
    assertThat(stats.uniqueVisitorsEstimate()).isEqualTo(2);
    assertThat(stats.topReferrers())
        .containsExactly(new LinkStats.ReferrerClicks("twitter.com", 2));
    assertThat(stats.topCountries()).isNull();
    assertThat(stats.clicksPerDay())
        .hasSize(30)
        .contains(new LinkStats.DailyClicks(TODAY, 4, 4, 2));
    org.mockito.Mockito.verify(repo, org.mockito.Mockito.never()).totals(anyLong(), any(), any());
  }

  @Test
  void fr7_9_defaultOverloadKeepsAllClicks() {
    LinkStats viaDefault = service.stats(ALICE, "aB3dE7x", null, null);
    LinkStats viaFalse = service.stats(ALICE, "aB3dE7x", null, null, false);

    assertThat(viaFalse).isEqualTo(viaDefault);
    assertThat(viaDefault.totalClicks()).isEqualTo(6);
  }
}
