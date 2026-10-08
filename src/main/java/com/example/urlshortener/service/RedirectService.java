package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.domain.CachedLink;
import com.example.urlshortener.service.domain.ClickEvent;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.domain.LinkStatus;
import com.example.urlshortener.service.domain.RedirectResult;
import com.example.urlshortener.service.port.ClickSink;
import com.example.urlshortener.service.port.LinkCache;
import com.example.urlshortener.service.port.RateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** The redirect hot path (design §6.3, URL-FR-3.x, 7.1 - 7.3). */
@Service
public final class RedirectService {

  /** {@code click_events.user_agent} is VARCHAR(512) (URL-FR-7.3). */
  static final int MAX_USER_AGENT_LENGTH = 512;

  /** {@code click_events.referrer_host} is VARCHAR(255). */
  static final int MAX_REFERRER_HOST_LENGTH = 255;

  private static final String WWW = "www.";

  private final LinkRepository links;
  private final LinkCache cache;
  private final RateLimiter rateLimiter;
  private final ClickSink clicks;
  private final IpHasher ipHasher;
  private final BotDetector bots;
  private final Clock clock;
  private final int redirectPerMinute;
  private final Counter droppedBufferFull;

  /**
   * Creates the service.
   *
   * @param links link repository
   * @param cache link cache
   * @param rateLimiter rate limiter
   * @param clicks click sink
   * @param ipHasher IP hasher
   * @param bots bot detector
   * @param props application properties
   * @param clock application clock
   * @param meters meter registry
   */
  public RedirectService(
      LinkRepository links,
      LinkCache cache,
      RateLimiter rateLimiter,
      ClickSink clicks,
      IpHasher ipHasher,
      BotDetector bots,
      AppProperties props,
      Clock clock,
      MeterRegistry meters) {
    this.links = links;
    this.cache = cache;
    this.rateLimiter = rateLimiter;
    this.clicks = clicks;
    this.ipHasher = ipHasher;
    this.bots = bots;
    this.clock = clock;
    this.redirectPerMinute = props.rateLimit().redirectPerMinute();
    this.droppedBufferFull =
        Counter.builder("urlshortener.clicks.dropped")
            .tag("reason", "buffer_full")
            .register(meters);
  }

  /**
   * URL-FR-3.1 - 3.7: rate limit per IP hash, cache, negative cache, DB; status at request time;
   * unknown or inactive is 404, expired is 410; exactly one click per 302 (URL-FR-7.1), enqueued
   * without waiting (URL-FR-7.2).
   *
   * @param code short code (case-sensitive)
   * @param remoteIp client IP (hashed immediately, never stored or logged)
   * @param referer {@code Referer} header or {@code null}
   * @param userAgent {@code User-Agent} header or {@code null}
   * @return the redirect outcome
   */
  public RedirectResult resolve(String code, String remoteIp, String referer, String userAgent) {
    String ipHash = ipHasher.hash(remoteIp);
    if (rateLimiter.tryAcquire("redirect:" + ipHash, redirectPerMinute)
        instanceof RateLimiter.Decision.Denied denied) {
      return new RedirectResult.RateLimited(denied.retryAfterSeconds());
    }

    Instant now = clock.instant();
    Optional<CachedLink> hit = cache.get(code);
    CachedLink link;
    if (hit.isPresent()) {
      CachedLink cached = hit.get();
      // BUGGY (seeded): cache-hit path collapses EXPIRED into NOT_FOUND
      if (cached.status() == LinkStatus.INACTIVE || cached.isExpired(now)) {
        return new RedirectResult.NotFound();
      }
      link = cached;
    } else {
      if (cache.isKnownMissing(code)) {
        return new RedirectResult.NotFound();
      }
      Optional<Link> row = links.findByCode(code);
      if (row.isEmpty()) {
        cache.markMissing(code);
        return new RedirectResult.NotFound();
      }
      Link l = row.get();
      link = new CachedLink(l.id(), l.targetUrl(), l.status(), l.expiresAt());
      cache.put(code, link);
    }

    return switch (link.effectiveStatus(now)) {
      case INACTIVE -> new RedirectResult.NotFound();
      case EXPIRED -> new RedirectResult.Gone();
      case ACTIVE -> {
        recordClick(link, now, referer, userAgent, ipHash);
        yield new RedirectResult.Found(link.targetUrl());
      }
    };
  }

  private void recordClick(
      CachedLink link, Instant now, String referer, String userAgent, String ipHash) {
    ClickEvent event =
        new ClickEvent(
            link.linkId(),
            now,
            referrerHost(referer),
            truncate(userAgent, MAX_USER_AGENT_LENGTH),
            bots.isBot(userAgent),
            ipHash,
            null);
    if (!clicks.offer(event)) {
      droppedBufferFull.increment();
    }
  }

  /**
   * Design §3.6: only the lowercased host of the {@code Referer}, without a leading {@code www.};
   * {@code null} when missing or unparseable.
   *
   * @param referer raw header
   * @return host or {@code null}
   */
  static String referrerHost(String referer) {
    if (referer == null || referer.isBlank()) {
      return null;
    }
    String host;
    try {
      host = new URI(referer.trim()).getHost();
    } catch (URISyntaxException e) {
      return null;
    }
    if (host == null || host.isBlank()) {
      return null;
    }
    host = host.toLowerCase(Locale.ROOT);
    if (host.startsWith(WWW)) {
      host = host.substring(WWW.length());
    }
    return truncate(host, MAX_REFERRER_HOST_LENGTH);
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
