package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.BotDetector;
import com.example.urlshortener.service.IpHasher;
import com.example.urlshortener.service.RedirectService;
import com.example.urlshortener.service.domain.CachedLink;
import com.example.urlshortener.service.domain.ClickEvent;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.domain.LinkStatus;
import com.example.urlshortener.service.domain.RedirectResult;
import com.example.urlshortener.service.port.ClickSink;
import com.example.urlshortener.service.port.LinkCache;
import com.example.urlshortener.service.port.RateLimiter;
import com.example.urlshortener.support.MutableClock;
import com.example.urlshortener.support.TestProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RedirectServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-07T21:00:00Z");
  private static final String IP = "203.0.113.10";
  private static final String IP_HASH =
      "081be95cdda838c0390345ef0bd1004b1509648c1511d7908d01af957bf61fa5";
  private static final String TARGET = "https://spring.io/projects/spring-boot";
  private static final String SAFARI =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Safari/605.1.15";

  private final LinkRepository repo = mock(LinkRepository.class);
  private final LinkCache cache = mock(LinkCache.class);
  private final RateLimiter rateLimiter = mock(RateLimiter.class);
  private final List<ClickEvent> sunk = new ArrayList<>();
  private boolean sinkAccepts = true;
  private final ClickSink sink =
      e -> {
        if (sinkAccepts) {
          sunk.add(e);
        }
        return sinkAccepts;
      };
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final MutableClock clock = new MutableClock(NOW);
  private final RedirectService service =
      new RedirectService(
          repo,
          cache,
          rateLimiter,
          sink,
          new IpHasher(TestProperties.withSalt("local-dev-salt")),
          new BotDetector(TestProperties.defaults()),
          TestProperties.defaults(),
          clock,
          meters);

  @BeforeEach
  void allowByDefault() {
    when(rateLimiter.tryAcquire(anyString(), anyInt()))
        .thenReturn(new RateLimiter.Decision.Allowed());
  }

  private static CachedLink cached(LinkStatus status, Instant expiresAt) {
    return new CachedLink(1L, TARGET, status, expiresAt);
  }

  private static Link row(LinkStatus status, Instant expiresAt) {
    return new Link(
        1L,
        "aB3dE7x",
        1L,
        TARGET,
        TARGET,
        false,
        status,
        NOW.minusSeconds(3600),
        expiresAt,
        status == LinkStatus.INACTIVE ? NOW.minusSeconds(60) : null,
        6L);
  }

  @Test
  void fr3_1_activeLinkRedirectsAndEnqueuesExactlyOneClick() {
    when(cache.get("aB3dE7x")).thenReturn(Optional.of(cached(LinkStatus.ACTIVE, null)));

    RedirectResult result =
        service.resolve("aB3dE7x", IP, "https://www.Twitter.com/some/post?x=1", SAFARI);

    assertThat(result).isEqualTo(new RedirectResult.Found(TARGET));
    assertThat(sunk)
        .containsExactly(new ClickEvent(1L, NOW, "twitter.com", SAFARI, false, IP_HASH, null));
  }

  @Test
  void fr3_4_cacheHitSkipsDb() {
    when(cache.get("aB3dE7x")).thenReturn(Optional.of(cached(LinkStatus.ACTIVE, null)));

    service.resolve("aB3dE7x", IP, null, null);

    verifyNoInteractions(repo);
    verify(cache, never()).isKnownMissing(any());
  }

  @Test
  void fr3_4_cacheMissReadsDbAndBackfillsCache() {
    when(cache.get("aB3dE7x")).thenReturn(Optional.empty());
    when(repo.findByCode("aB3dE7x")).thenReturn(Optional.of(row(LinkStatus.ACTIVE, null)));

    assertThat(service.resolve("aB3dE7x", IP, null, null))
        .isEqualTo(new RedirectResult.Found(TARGET));
    verify(cache).put("aB3dE7x", cached(LinkStatus.ACTIVE, null));
  }

  @Test
  void fr3_6_negativeCacheSkipsDb() {
    when(cache.get("nope1234")).thenReturn(Optional.empty());
    when(cache.isKnownMissing("nope1234")).thenReturn(true);

    assertThat(service.resolve("nope1234", IP, null, null))
        .isEqualTo(new RedirectResult.NotFound());
    verifyNoInteractions(repo);
    assertThat(sunk).isEmpty();
  }

  @Test
  void fr3_6_unknownCodeIsNegativelyCached() {
    when(cache.get("nope1234")).thenReturn(Optional.empty());
    when(repo.findByCode("nope1234")).thenReturn(Optional.empty());

    assertThat(service.resolve("nope1234", IP, null, null))
        .isEqualTo(new RedirectResult.NotFound());
    verify(cache).markMissing("nope1234");
    verify(cache, never()).put(any(), any());
  }

  @Test
  void fr3_2_inactiveIs404AndExpiredIs410WithoutClicks() {
    when(cache.get("Qm4Rt8Z")).thenReturn(Optional.empty());
    when(repo.findByCode("Qm4Rt8Z")).thenReturn(Optional.of(row(LinkStatus.INACTIVE, null)));
    when(cache.get("Xy9Kp2Q")).thenReturn(Optional.empty());
    when(repo.findByCode("Xy9Kp2Q"))
        .thenReturn(Optional.of(row(LinkStatus.ACTIVE, NOW.minusSeconds(86_400))));

    assertThat(service.resolve("Qm4Rt8Z", IP, null, null)).isEqualTo(new RedirectResult.NotFound());
    assertThat(service.resolve("Xy9Kp2Q", IP, null, null)).isEqualTo(new RedirectResult.Gone());
    assertThat(sunk).isEmpty();
  }

  @Test
  void fr4_3_expiredLinkServedFromCacheReturnsGone() {
    // cached while active; the cache hit must still be evaluated at request time (B2 regression)
    when(cache.get("Xy9Kp2Q"))
        .thenReturn(Optional.of(cached(LinkStatus.ACTIVE, NOW.plusSeconds(5))));

    assertThat(service.resolve("Xy9Kp2Q", IP, null, null))
        .isEqualTo(new RedirectResult.Found(TARGET));
    clock.advance(java.time.Duration.ofSeconds(5));
    assertThat(service.resolve("Xy9Kp2Q", IP, null, null)).isEqualTo(new RedirectResult.Gone());
    verifyNoInteractions(repo);
  }

  /** B2 regression (design §11.3): an expired link served from the cache must return 410. */
  @Test
  void b2_expiredFromCacheReturnsGone() {
    when(cache.get("Xy9Kp2Q"))
        .thenReturn(Optional.of(cached(LinkStatus.ACTIVE, NOW.minusSeconds(86_400))));

    assertThat(service.resolve("Xy9Kp2Q", IP, null, null)).isEqualTo(new RedirectResult.Gone());
    verifyNoInteractions(repo);
    assertThat(sunk).isEmpty();
  }

  @Test
  void fr3_2_inactiveFromCacheIs404() {
    when(cache.get("Qm4Rt8Z")).thenReturn(Optional.of(cached(LinkStatus.INACTIVE, null)));

    assertThat(service.resolve("Qm4Rt8Z", IP, null, null)).isEqualTo(new RedirectResult.NotFound());
  }

  @Test
  void fr3_7_rateLimitedPerIpHashBeforeAnyLookup() {
    when(rateLimiter.tryAcquire("redirect:" + IP_HASH, 600))
        .thenReturn(new RateLimiter.Decision.Denied(30));

    assertThat(service.resolve("aB3dE7x", IP, null, null))
        .isEqualTo(new RedirectResult.RateLimited(30));
    verifyNoInteractions(cache, repo);
    assertThat(sunk).isEmpty();
  }

  @Test
  void fr3_5_fullBufferStillRedirectsAndCountsDrop() {
    sinkAccepts = false;
    when(cache.get("aB3dE7x")).thenReturn(Optional.of(cached(LinkStatus.ACTIVE, null)));

    assertThat(service.resolve("aB3dE7x", IP, null, null))
        .isEqualTo(new RedirectResult.Found(TARGET));
    assertThat(meters.counter("urlshortener.clicks.dropped", "reason", "buffer_full").count())
        .isEqualTo(1.0);
  }

  @Test
  void fr7_4_botsAreFlaggedNotDropped() {
    when(cache.get("aB3dE7x")).thenReturn(Optional.of(cached(LinkStatus.ACTIVE, null)));

    service.resolve("aB3dE7x", IP, null, "Googlebot/2.1 (+http://www.google.com/bot.html)");

    assertThat(sunk).singleElement().satisfies(e -> assertThat(e.bot()).isTrue());
  }

  @Test
  void fr7_3_userAgentIsTruncatedTo512() {
    when(cache.get("aB3dE7x")).thenReturn(Optional.of(cached(LinkStatus.ACTIVE, null)));

    service.resolve("aB3dE7x", IP, null, "U".repeat(600));

    assertThat(sunk.get(0).userAgent()).hasSize(512);
    assertThat(sunk.get(0).ipHash()).doesNotContain(IP);
  }

  @ParameterizedTest(name = "[{0}] -> [{1}]")
  @CsvSource(
      value = {
        "https://WWW.Example.org/x | example.org",
        "http://news.ycombinator.com/item?id=1 | news.ycombinator.com",
        "https://t.co | t.co",
        "not a url | NULL",
        "/relative/only | NULL",
        "'' | NULL"
      },
      delimiter = '|',
      nullValues = "NULL")
  void fr7_3_referrerIsHostOnly(String referer, String expectedHost) {
    when(cache.get("aB3dE7x")).thenReturn(Optional.of(cached(LinkStatus.ACTIVE, null)));

    service.resolve("aB3dE7x", IP, referer, SAFARI);

    assertThat(sunk.get(0).referrerHost()).isEqualTo(expectedHost);
  }

  @Test
  void fr7_3_veryLongReferrerHostIsTruncated() {
    when(cache.get("aB3dE7x")).thenReturn(Optional.of(cached(LinkStatus.ACTIVE, null)));
    String host =
        "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".io";

    service.resolve("aB3dE7x", IP, "https://" + host + "/", SAFARI);

    assertThat(sunk.get(0).referrerHost()).hasSize(255);
  }

  @Test
  void rateLimitBucketUsesIpHashNotRawIp() {
    when(cache.get(eq("aB3dE7x"))).thenReturn(Optional.of(cached(LinkStatus.ACTIVE, null)));

    service.resolve("aB3dE7x", IP, null, null);

    verify(rateLimiter).tryAcquire("redirect:" + IP_HASH, 600);
  }
}
