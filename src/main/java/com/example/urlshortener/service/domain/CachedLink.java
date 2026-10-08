package com.example.urlshortener.service.domain;

import java.time.Instant;

/**
 * Hot-path cache entry (design §3.5): enough to evaluate status at request time (URL-FR-3.4).
 *
 * @param linkId database id of the link
 * @param targetUrl redirect target
 * @param status persisted status
 * @param expiresAt expiry time, or {@code null} for never
 */
public record CachedLink(long linkId, String targetUrl, LinkStatus status, Instant expiresAt) {

  /**
   * URL-FR-4.3: same rule as {@link Link#effectiveStatus(Instant)}, so cache hits and DB reads
   * cannot disagree.
   *
   * @param now current instant from the injected clock
   * @return the effective status at {@code now}
   */
  public EffectiveStatus effectiveStatus(Instant now) {
    return EffectiveStatus.evaluate(status, expiresAt, now);
  }
}
