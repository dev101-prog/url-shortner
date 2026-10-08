package com.example.urlshortener.service.domain;

import java.time.Instant;

/** Status derived at read time from the persisted status and expiry (URL-FR-4.3). */
public enum EffectiveStatus {
  ACTIVE,
  INACTIVE,
  EXPIRED;

  /**
   * URL-FR-4.3: the single status rule for both DB rows and cache entries. INACTIVE wins over
   * EXPIRED; the expiry boundary is exclusive (at exactly {@code expiresAt} the link is expired).
   *
   * @param status persisted status
   * @param expiresAt expiry or {@code null}
   * @param now current instant from the injected clock
   * @return effective status
   */
  public static EffectiveStatus evaluate(LinkStatus status, Instant expiresAt, Instant now) {
    if (status == LinkStatus.INACTIVE) {
      return INACTIVE;
    }
    if (expiresAt != null && !now.isBefore(expiresAt)) {
      return EXPIRED;
    }
    return ACTIVE;
  }
}
