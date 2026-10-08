package com.example.urlshortener.api.dto;

import java.time.OffsetDateTime;

/**
 * Body of {@code POST /api/v1/links} (design §5.3). Unknown fields are rejected (422).
 *
 * @param url target URL (required)
 * @param alias optional custom alias
 * @param expiresAt optional ISO-8601 expiry with an offset
 * @param dedupe opt-in dedupe, default {@code false}
 */
public record CreateLinkRequest(
    String url, String alias, OffsetDateTime expiresAt, boolean dedupe) {}
