package com.example.urlshortener.api.dto;

import java.time.Instant;

/**
 * Metadata response (design §5.3, URL-FR-5.1).
 *
 * @param code short code
 * @param shortUrl base URL plus code
 * @param target target URL
 * @param createdAt creation time (UTC)
 * @param expiresAt expiry or {@code null}
 * @param status {@code active}, {@code inactive} or {@code expired}
 * @param totalClicks lifetime click count
 */
public record LinkMetadataResponse(
    String code,
    String shortUrl,
    String target,
    Instant createdAt,
    Instant expiresAt,
    String status,
    long totalClicks) {}
