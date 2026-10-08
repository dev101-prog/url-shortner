package com.example.urlshortener.api.dto;

import java.time.Instant;

/**
 * Create response (design §5.3).
 *
 * @param code short code
 * @param shortUrl base URL plus code (URL-FR-1.7)
 * @param target target URL
 * @param createdAt creation time (UTC)
 * @param expiresAt expiry or {@code null}
 * @param status {@code active}, {@code inactive} or {@code expired}
 */
public record LinkResponse(
    String code,
    String shortUrl,
    String target,
    Instant createdAt,
    Instant expiresAt,
    String status) {}
