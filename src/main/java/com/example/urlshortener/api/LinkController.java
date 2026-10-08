package com.example.urlshortener.api;

import com.example.urlshortener.api.dto.CreateLinkRequest;
import com.example.urlshortener.api.dto.ErrorResponse;
import com.example.urlshortener.api.dto.LinkMetadataResponse;
import com.example.urlshortener.api.dto.LinkResponse;
import com.example.urlshortener.api.dto.StatsResponse;
import com.example.urlshortener.api.error.GlobalExceptionHandler;
import com.example.urlshortener.api.filter.ApiKeyAuthFilter;
import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.config.OpenApiConfig;
import com.example.urlshortener.service.LinkService;
import com.example.urlshortener.service.StatsService;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.domain.CreateResult;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import com.example.urlshortener.service.port.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/links}: DTO mapping only; rules live in {@link LinkService} (design §3.1). */
@RestController
@RequestMapping("/api/v1/links")
@Tag(name = "links", description = "Create, inspect, deactivate links and read their stats")
@SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME)
public class LinkController {

  private static final String ERR = "application/json";

  private final LinkService links;
  private final StatsService stats;
  private final RateLimiter rateLimiter;
  private final int createPerMinute;
  private final String baseUrl;

  /**
   * Creates the controller.
   *
   * @param links link service
   * @param stats stats service
   * @param rateLimiter rate limiter
   * @param props application properties
   */
  public LinkController(
      LinkService links, StatsService stats, RateLimiter rateLimiter, AppProperties props) {
    this.links = links;
    this.stats = stats;
    this.rateLimiter = rateLimiter;
    this.createPerMinute = props.rateLimit().createPerMinute();
    this.baseUrl =
        props.baseUrl().endsWith("/")
            ? props.baseUrl().substring(0, props.baseUrl().length() - 1)
            : props.baseUrl();
  }

  /**
   * URL-FR-1.1 / 1.4 / URL-NFR-4.3: 201 with {@code Location}, 200 on a dedupe hit, 429 over the
   * per-key limit.
   *
   * @param owner authenticated caller
   * @param request body
   * @return created or existing link
   */
  @Operation(
      summary = "Create a short link",
      description = "Random 7-char base62 code or custom alias; opt-in dedupe.")
  @ApiResponse(responseCode = "201", description = "Created; Location header points to the link.")
  @ApiResponse(
      responseCode = "200",
      description = "Dedupe hit: an existing active link of this owner is returned.")
  @ApiResponse(
      responseCode = "400",
      description = "MALFORMED_REQUEST: body is not valid JSON",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED: X-API-Key missing, unknown or revoked",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "ALIAS_CONFLICT: alias already exists in any status",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "411",
      description = "LENGTH_REQUIRED: POST without Content-Length",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "413",
      description = "PAYLOAD_TOO_LARGE: body over 8 KB",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "422",
      description =
          "INVALID_URL, TARGET_BLOCKED, INVALID_ALIAS, RESERVED_ALIAS, INVALID_EXPIRY or VALIDATION_FAILED",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "RATE_LIMITED: per-key create limit exceeded; see Retry-After",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "503",
      description = "CODE_GENERATION_EXHAUSTED: 5 code collisions in a row",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping
  public ResponseEntity<LinkResponse> create(
      @Parameter(hidden = true) @RequestAttribute(ApiKeyAuthFilter.OWNER_ATTRIBUTE)
          AuthenticatedOwner owner,
      @RequestBody CreateLinkRequest request) {
    if (rateLimiter.tryAcquire("create:" + owner.apiKeyId(), createPerMinute)
        instanceof RateLimiter.Decision.Denied denied) {
      throw new ApiException(
          ErrorCode.RATE_LIMITED,
          ErrorCode.RATE_LIMITED.defaultMessage(),
          Map.of(GlobalExceptionHandler.RETRY_AFTER_DETAIL, denied.retryAfterSeconds()));
    }
    Instant expiresAt = request.expiresAt() == null ? null : request.expiresAt().toInstant();
    CreateResult result =
        links.create(owner, request.url(), request.alias(), expiresAt, request.dedupe());
    Link link = result.link();
    LinkResponse body =
        new LinkResponse(
            link.code(),
            shortUrl(link),
            link.targetUrl(),
            link.createdAt(),
            link.expiresAt(),
            status(link));
    if (result instanceof CreateResult.Created) {
      return ResponseEntity.created(URI.create("/api/v1/links/" + link.code())).body(body);
    }
    return ResponseEntity.ok(body);
  }

  /**
   * URL-FR-5.1 / 5.2: owner-only metadata.
   *
   * @param owner authenticated caller
   * @param code short code
   * @return metadata
   */
  @Operation(summary = "Link metadata", description = "Owner only; other owners get 404.")
  @ApiResponse(responseCode = "200", description = "Metadata with lifetime total_clicks.")
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED: X-API-Key missing, unknown or revoked",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: unknown code or not owned by the caller",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/{code}")
  public LinkMetadataResponse get(
      @Parameter(hidden = true) @RequestAttribute(ApiKeyAuthFilter.OWNER_ATTRIBUTE)
          AuthenticatedOwner owner,
      @PathVariable String code) {
    Link link = links.get(owner, code);
    return new LinkMetadataResponse(
        link.code(),
        shortUrl(link),
        link.targetUrl(),
        link.createdAt(),
        link.expiresAt(),
        status(link),
        link.clickCount());
  }

  /**
   * URL-FR-6.1 / 6.2: soft delete, 204 also on repeat.
   *
   * @param owner authenticated caller
   * @param code short code
   * @return 204
   */
  @Operation(summary = "Deactivate (soft delete)", description = "Idempotent; owner only.")
  @ApiResponse(responseCode = "204", description = "Deactivated (also on repeat).")
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED: X-API-Key missing, unknown or revoked",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: unknown code or not owned by the caller",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @DeleteMapping("/{code}")
  public ResponseEntity<Void> deactivate(
      @Parameter(hidden = true) @RequestAttribute(ApiKeyAuthFilter.OWNER_ATTRIBUTE)
          AuthenticatedOwner owner,
      @PathVariable String code) {
    links.deactivate(owner, code);
    return ResponseEntity.noContent().build();
  }

  /**
   * URL-FR-7.5 / 7.7: owner-only statistics over an inclusive UTC date range.
   *
   * @param owner authenticated caller
   * @param code short code
   * @param from first day (ISO date) or absent
   * @param to last day (ISO date) or absent
   * @param excludeBots URL-FR-7.9: count human clicks only
   * @return statistics
   */
  @Operation(
      summary = "Click statistics",
      description = "Inclusive UTC dates; default last 30 days, max 365.")
  @ApiResponse(
      responseCode = "200",
      description = "Totals, clicks per day (zero-filled), top referrers and countries.")
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED: X-API-Key missing, unknown or revoked",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: unknown code or not owned by the caller",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "422",
      description =
          "INVALID_DATE_RANGE (from after to, span over 365 days) or VALIDATION_FAILED (bad date format)",
      content = @Content(mediaType = ERR, schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/{code}/stats")
  public StatsResponse stats(
      @Parameter(hidden = true) @RequestAttribute(ApiKeyAuthFilter.OWNER_ATTRIBUTE)
          AuthenticatedOwner owner,
      @PathVariable String code,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @Parameter(
              description =
                  "URL-FR-7.9: true counts human clicks only (is_bot = false); absent or false"
                      + " returns the unchanged default output.")
          @RequestParam(name = "exclude_bots", required = false, defaultValue = "false")
          boolean excludeBots) {
    return StatsResponse.from(stats.stats(owner, code, from, to, excludeBots));
  }

  private String shortUrl(Link link) {
    return baseUrl + "/" + link.code();
  }

  private String status(Link link) {
    return links.statusOf(link).name().toLowerCase(Locale.ROOT);
  }
}
