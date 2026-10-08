package com.example.urlshortener.api.dto;

import java.time.OffsetDateTime;

/**
 * Body of {@code POST /api/v1/links} (design §5.3). Unknown fields are rejected (422).
 *
 * @param url target URL (required)
 * @param alias optional custom alias
 * @param expiresAt optional ISO-8601 expiry with an offset
 * @param dedupe opt-in dedupe; {@code null} (absent) uses the owner's default (URL-FR-1.4, B1)
 */
public record CreateLinkRequest(
    String url, String alias, OffsetDateTime expiresAt, Boolean dedupe) {}
