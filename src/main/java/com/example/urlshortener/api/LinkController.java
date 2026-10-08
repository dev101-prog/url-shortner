package com.example.urlshortener.api;

import com.example.urlshortener.api.dto.CreateLinkRequest;
import com.example.urlshortener.api.dto.LinkMetadataResponse;
import com.example.urlshortener.api.dto.LinkResponse;
import com.example.urlshortener.api.error.GlobalExceptionHandler;
import com.example.urlshortener.api.filter.ApiKeyAuthFilter;
import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.service.LinkService;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.domain.CreateResult;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import com.example.urlshortener.service.port.RateLimiter;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/links}: DTO mapping only; rules live in {@link LinkService} (design §3.1). */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

  private final LinkService links;
  private final RateLimiter rateLimiter;
  private final int createPerMinute;
  private final String baseUrl;

  /**
   * Creates the controller.
   *
   * @param links link service
   * @param rateLimiter rate limiter
   * @param props application properties
   */
  public LinkController(LinkService links, RateLimiter rateLimiter, AppProperties props) {
    this.links = links;
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
  @PostMapping
  public ResponseEntity<LinkResponse> create(
      @RequestAttribute(ApiKeyAuthFilter.OWNER_ATTRIBUTE) AuthenticatedOwner owner,
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
  @GetMapping("/{code}")
  public LinkMetadataResponse get(
      @RequestAttribute(ApiKeyAuthFilter.OWNER_ATTRIBUTE) AuthenticatedOwner owner,
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
  @DeleteMapping("/{code}")
  public ResponseEntity<Void> deactivate(
      @RequestAttribute(ApiKeyAuthFilter.OWNER_ATTRIBUTE) AuthenticatedOwner owner,
      @PathVariable String code) {
    links.deactivate(owner, code);
    return ResponseEntity.noContent().build();
  }

  private String shortUrl(Link link) {
    return baseUrl + "/" + link.code();
  }

  private String status(Link link) {
    return links.statusOf(link).name().toLowerCase(Locale.ROOT);
  }
}
