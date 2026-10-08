package com.example.urlshortener.service.domain;

/** Outcome of a create request (design §6.2): a new link (201) or a dedupe hit (200). */
public sealed interface CreateResult {

  /**
   * Returns the link to report to the caller.
   *
   * @return the created or existing link
   */
  Link link();

  /**
   * A new link was inserted.
   *
   * @param link the new link
   */
  record Created(Link link) implements CreateResult {}

  /**
   * Opt-in dedupe returned an existing link (URL-FR-1.4).
   *
   * @param link the existing link
   */
  record Existing(Link link) implements CreateResult {}
}
