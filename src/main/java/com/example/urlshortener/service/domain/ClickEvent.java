package com.example.urlshortener.service.domain;

import java.time.Instant;

/**
 * One successful redirect (URL-FR-7.1, 7.3). Never carries the raw IP.
 *
 * @param linkId database id of the link
 * @param clickedAt click time (UTC)
 * @param referrerHost lowercased referrer host without {@code www.}, or {@code null}
 * @param userAgent user agent truncated to 512 chars, or {@code null}
 * @param bot whether the user agent matched a bot pattern (URL-FR-7.4)
 * @param ipHash HMAC-SHA256(salt, ip) as lowercase hex
 * @param country ISO-3166 alpha-2 code, or {@code null} (GeoIP is P2)
 */
public record ClickEvent(
    long linkId,
    Instant clickedAt,
    String referrerHost,
    String userAgent,
    boolean bot,
    String ipHash,
    String country) {}
