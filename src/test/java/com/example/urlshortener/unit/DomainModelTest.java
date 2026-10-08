package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.domain.CachedLink;
import com.example.urlshortener.service.domain.ClickEvent;
import com.example.urlshortener.service.domain.CreateResult;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.domain.LinkStats;
import com.example.urlshortener.service.domain.LinkStatus;
import com.example.urlshortener.service.domain.RedirectResult;
import com.example.urlshortener.service.port.RateLimiter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DomainModelTest {

  private static final Instant NOW = Instant.parse("2026-10-07T21:00:00Z");

  @Test
  void fr7_5_linkStatsIsImmutableAndKeepsNullCountries() {
    List<LinkStats.DailyClicks> days = new ArrayList<>();
    days.add(new LinkStats.DailyClicks(LocalDate.parse("2026-10-07"), 2, 1, 2));
    List<LinkStats.ReferrerClicks> referrers = new ArrayList<>();
    referrers.add(new LinkStats.ReferrerClicks("(direct)", 2));

    LinkStats stats =
        new LinkStats(
            "aB3dE7x",
            LocalDate.parse("2026-10-07"),
            LocalDate.parse("2026-10-07"),
            2,
            1,
            1,
            2,
            days,
            referrers,
            null);
    days.clear();
    referrers.clear();

    assertThat(stats.clicksPerDay()).hasSize(1);
    assertThat(stats.topReferrers())
        .extracting(LinkStats.ReferrerClicks::referrer)
        .containsExactly("(direct)");
    assertThat(stats.topCountries()).isNull();
    assertThatThrownBy(() -> stats.clicksPerDay().add(null))
        .isInstanceOf(UnsupportedOperationException.class);

    LinkStats withCountries =
        new LinkStats(
            "aB3dE7x",
            LocalDate.parse("2026-10-07"),
            LocalDate.parse("2026-10-07"),
            0,
            0,
            0,
            0,
            List.of(),
            List.of(),
            List.of(new LinkStats.CountryClicks("US", 3)));
    assertThat(withCountries.topCountries()).containsExactly(new LinkStats.CountryClicks("US", 3));
  }

  @Test
  void fr1_4_createResultExposesLinkAndDistinguishesNewFromExisting() {
    Link link =
        new Link(
            7L,
            "my-promo",
            1L,
            "https://example.org",
            "https://example.org",
            true,
            LinkStatus.ACTIVE,
            NOW,
            null,
            null,
            0L);

    CreateResult created = new CreateResult.Created(link);
    CreateResult existing = new CreateResult.Existing(link);

    assertThat(created.link()).isSameAs(link);
    assertThat(existing.link()).isSameAs(link);
    assertThat(httpStatusOf(created)).isEqualTo(201);
    assertThat(httpStatusOf(existing)).isEqualTo(200);
  }

  @Test
  void fr3_2_redirectResultsMapToDistinctStatuses() {
    assertThat(httpStatusOf(new RedirectResult.Found("https://example.org"))).isEqualTo(302);
    assertThat(httpStatusOf(new RedirectResult.NotFound())).isEqualTo(404);
    assertThat(httpStatusOf(new RedirectResult.Gone())).isEqualTo(410);
    assertThat(httpStatusOf(new RedirectResult.RateLimited(30))).isEqualTo(429);
  }

  @Test
  void nfr4_3_rateLimiterDecisionCarriesRetryAfter() {
    RateLimiter.Decision denied = new RateLimiter.Decision.Denied(42);
    RateLimiter.Decision allowed = new RateLimiter.Decision.Allowed();

    assertThat(retryAfter(denied)).isEqualTo(42L);
    assertThat(retryAfter(allowed)).isZero();
  }

  @Test
  void valueTypesUseValueEquality() {
    assertThat(new CachedLink(1L, "https://a.io", LinkStatus.ACTIVE, NOW))
        .isEqualTo(new CachedLink(1L, "https://a.io", LinkStatus.ACTIVE, NOW));
    assertThat(new AuthenticatedOwner(1L, 2L)).isEqualTo(new AuthenticatedOwner(1L, 2L));
    ClickEvent click = new ClickEvent(1L, NOW, "t.co", "curl/8.4.0", true, "ab".repeat(32), null);
    assertThat(click.bot()).isTrue();
    assertThat(click.country()).isNull();
  }

  private static int httpStatusOf(CreateResult result) {
    return switch (result) {
      case CreateResult.Created c -> 201;
      case CreateResult.Existing e -> 200;
    };
  }

  private static int httpStatusOf(RedirectResult result) {
    return switch (result) {
      case RedirectResult.Found f -> 302;
      case RedirectResult.NotFound n -> 404;
      case RedirectResult.Gone g -> 410;
      case RedirectResult.RateLimited r -> 429;
    };
  }

  private static long retryAfter(RateLimiter.Decision decision) {
    return switch (decision) {
      case RateLimiter.Decision.Allowed a -> 0L;
      case RateLimiter.Decision.Denied d -> d.retryAfterSeconds();
    };
  }
}
