package com.example.urlshortener.service.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A short link as stored in {@code links} (design §3.2, §4.3).
 *
 * @param id database id
 * @param code short code or custom alias (case-sensitive)
 * @param ownerId owning owner id
 * @param targetUrl redirect target as submitted
 * @param normalizedUrl normalised target used for opt-in dedupe
 * @param customAlias whether the code was supplied by the caller
 * @param status persisted status
 * @param createdAt creation time (UTC)
 * @param expiresAt expiry time, or {@code null} for never
 * @param deactivatedAt deactivation time, or {@code null} while active
 * @param clickCount lifetime click count
 */
public record Link(
    long id,
    String code,
    long ownerId,
    String targetUrl,
    String normalizedUrl,
    boolean customAlias,
    LinkStatus status,
    Instant createdAt,
    Instant expiresAt,
    Instant deactivatedAt,
    long clickCount) {

  /** Validates required fields. */
  public Link {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(targetUrl, "targetUrl");
    Objects.requireNonNull(normalizedUrl, "normalizedUrl");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /**
   * URL-FR-4.3: status is derived at read time; the expiry boundary is exclusive, so at exactly
   * {@code expiresAt} the link is already expired. INACTIVE wins over EXPIRED.
   *
   * @param now current instant from the injected clock
   * @return the effective status at {@code now}
   */
  public EffectiveStatus effectiveStatus(Instant now) {
    if (status == LinkStatus.INACTIVE) {
      return EffectiveStatus.INACTIVE;
    }
    if (expiresAt != null && !now.isBefore(expiresAt)) {
      return EffectiveStatus.EXPIRED;
    }
    return EffectiveStatus.ACTIVE;
  }
}
