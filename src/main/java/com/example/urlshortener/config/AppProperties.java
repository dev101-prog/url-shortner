package com.example.urlshortener.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed view of the {@code app.*} configuration (design §7.3). All limits, TTLs, sizes and word
 * lists come from here; nothing is hard-coded in the services.
 *
 * @param baseUrl public base URL used for {@code short_url} and the self-redirect check
 * @param links link creation settings
 * @param cache cache sizes and TTLs
 * @param rateLimit rate limits
 * @param analytics click pipeline settings
 * @param http HTTP input limits (not in §7.3; defaults equal the design values)
 * @param stats stats endpoint range rules (not in §7.3; defaults equal the design values)
 * @param health readiness check settings (not in §7.3; defaults equal the design values)
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @NotBlank String baseUrl,
    @NotNull @Valid Links links,
    @NotNull @Valid Cache cache,
    @NotNull @Valid RateLimit rateLimit,
    @NotNull @Valid Analytics analytics,
    @NotNull @Valid @DefaultValue Http http,
    @NotNull @Valid @DefaultValue Stats stats,
    @NotNull @Valid @DefaultValue Health health) {

  /**
   * Link creation rules (URL-FR-1.x, 2.x, 4.x).
   *
   * @param codeLength length of generated codes
   * @param maxCodegenAttempts collision retries before 503
   * @param allowedSchemes URL scheme allowlist
   * @param maxUrlLength maximum target URL length
   * @param maxExpiry latest allowed expiry relative to now
   * @param blockedDomains target host denylist (P2)
   * @param reservedWords aliases rejected case-insensitively
   */
  public record Links(
      @Positive int codeLength,
      @Positive int maxCodegenAttempts,
      @NotEmpty List<String> allowedSchemes,
      @Positive int maxUrlLength,
      @NotNull Duration maxExpiry,
      List<String> blockedDomains,
      List<String> reservedWords) {

    /** Copies lists; absent lists become empty. */
    public Links {
      allowedSchemes = allowedSchemes == null ? List.of() : List.copyOf(allowedSchemes);
      blockedDomains = blockedDomains == null ? List.of() : List.copyOf(blockedDomains);
      reservedWords = reservedWords == null ? List.of() : List.copyOf(reservedWords);
    }
  }

  /**
   * Cache settings (design §3.5).
   *
   * @param ttl positive link cache TTL cap
   * @param negativeTtl negative cache TTL
   * @param maxSize maximum entries per link cache
   * @param apiKeyTtl API key cache TTL
   * @param apiKeyMaxSize maximum API key cache entries (design §3.5: 10,000)
   */
  public record Cache(
      @NotNull Duration ttl,
      @NotNull Duration negativeTtl,
      @Positive long maxSize,
      @NotNull Duration apiKeyTtl,
      @Positive @DefaultValue("10000") long apiKeyMaxSize) {}

  /**
   * Rate limits (PRD A8).
   *
   * @param createPerMinute creates per minute per API key
   * @param redirectPerMinute redirects per minute per IP hash
   * @param maxBuckets maximum live fixed-window counters (design §3.5: 200,000)
   */
  public record RateLimit(
      @Positive int createPerMinute,
      @Positive int redirectPerMinute,
      @Positive @DefaultValue("200000") long maxBuckets) {}

  /**
   * Click pipeline settings (design §3.6; PRD A9).
   *
   * @param bufferCapacity in-memory buffer size
   * @param flushInterval flush period
   * @param flushBatchSize events per flush
   * @param ipSalt HMAC salt for IP hashing (secret outside local)
   * @param botPatternsVersion version label of the bot pattern list
   * @param botPatterns case-insensitive user agent regexes
   * @param flushRetryBackoff wait before the single flush retry (design §3.6: 200 ms)
   * @param shutdownDrainTimeout maximum time to drain the buffer on shutdown (design §3.6: 10 s)
   */
  public record Analytics(
      @Positive int bufferCapacity,
      @NotNull Duration flushInterval,
      @Positive int flushBatchSize,
      @NotBlank String ipSalt,
      @NotBlank String botPatternsVersion,
      List<String> botPatterns,
      @NotNull @DefaultValue("PT0.2S") Duration flushRetryBackoff,
      @NotNull @DefaultValue("PT10S") Duration shutdownDrainTimeout) {

    /** Copies the pattern list; an absent list becomes empty. */
    public Analytics {
      botPatterns = botPatterns == null ? List.of() : List.copyOf(botPatterns);
    }
  }

  /**
   * HTTP input limits (URL-NFR-4.5).
   *
   * @param maxBodyBytes maximum request body size in bytes (design §3.1: 8 KB)
   */
  public record Http(@Positive @DefaultValue("8192") int maxBodyBytes) {}

  /**
   * Stats range rules (URL-FR-7.5, design §5.3).
   *
   * @param defaultDays days covered when no range is given (last 30 days, inclusive of today)
   * @param maxDays maximum inclusive span of a requested range
   */
  public record Stats(
      @Positive @DefaultValue("30") int defaultDays, @Positive @DefaultValue("365") int maxDays) {}

  /**
   * Readiness settings (design §6.8, URL-FR-8.2).
   *
   * @param dbTimeout maximum time for the readiness database check (design §3.1: 1 s)
   */
  public record Health(@NotNull @DefaultValue("PT1S") Duration dbTimeout) {}
}
