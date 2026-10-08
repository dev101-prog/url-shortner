package com.example.urlshortener.service.port;

/** Fixed-window rate limiter (design D8; URL-NFR-4.3, URL-FR-3.7). */
public interface RateLimiter {

  /**
   * Counts one request against a bucket.
   *
   * @param bucketKey bucket, e.g. {@code create:<apiKeyId>} or {@code redirect:<ipHash>}
   * @param limitPerMinute allowed requests per one-minute window
   * @return {@link Decision.Allowed} or {@link Decision.Denied}
   */
  Decision tryAcquire(String bucketKey, int limitPerMinute);

  /** Result of {@link #tryAcquire(String, int)}. */
  sealed interface Decision {

    /** The request is within the limit. */
    record Allowed() implements Decision {}

    /**
     * The limit is exceeded.
     *
     * @param retryAfterSeconds seconds until the window resets (value of {@code Retry-After})
     */
    record Denied(long retryAfterSeconds) implements Decision {}
  }
}
