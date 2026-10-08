package com.example.urlshortener.service.domain;

/** Outcome of resolving a short code on the redirect hot path (URL-FR-3.x). */
public sealed interface RedirectResult {

  /**
   * Active link: respond 302 to the target.
   *
   * @param targetUrl redirect target
   */
  record Found(String targetUrl) implements RedirectResult {}

  /** Unknown or inactive code: respond 404. */
  record NotFound() implements RedirectResult {}

  /** Expired link: respond 410. */
  record Gone() implements RedirectResult {}

  /**
   * Rate limit exceeded: respond 429 with {@code Retry-After}.
   *
   * @param retryAfterSeconds seconds until the current window ends
   */
  record RateLimited(long retryAfterSeconds) implements RedirectResult {}
}
