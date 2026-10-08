package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.domain.CachedLink;
import com.example.urlshortener.service.domain.CreateResult;
import com.example.urlshortener.service.domain.EffectiveStatus;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import com.example.urlshortener.service.port.CodeGenerator;
import com.example.urlshortener.service.port.LinkCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Create, read and deactivate links (design §3.1, flows §6.2, §6.5, §6.6). */
@Service
public final class LinkService {

  private static final Logger LOG = LoggerFactory.getLogger(LinkService.class);

  private final LinkRepository links;
  private final LinkValidator validator;
  private final UrlNormalizer normalizer;
  private final LinkCache cache;
  private final Clock clock;
  private final CodeGenerator codes;
  private final int maxAttempts;
  private final Counter codegenRetries;
  private final Counter codegenExhausted;

  /**
   * Creates the service.
   *
   * @param links link repository
   * @param validator link validator
   * @param normalizer URL normaliser
   * @param cache link cache
   * @param props application properties
   * @param clock application clock
   * @param codes candidate code source (URL-FR-1.2; scenario B3)
   * @param meters meter registry
   */
  public LinkService(
      LinkRepository links,
      LinkValidator validator,
      UrlNormalizer normalizer,
      LinkCache cache,
      AppProperties props,
      Clock clock,
      CodeGenerator codes,
      MeterRegistry meters) {
    this.links = links;
    this.validator = validator;
    this.normalizer = normalizer;
    this.cache = cache;
    this.clock = clock;
    this.codes = codes;
    this.maxAttempts = props.links().maxCodegenAttempts();
    this.codegenRetries = meters.counter("urlshortener.codegen.retries");
    this.codegenExhausted = meters.counter("urlshortener.codegen.exhausted");
  }

  /**
   * URL-FR-1.x, 2.x, 4.x: validates, optionally dedupes, inserts with a random code or the alias,
   * then warms the cache. The row is committed before this returns (URL-FR-1.6).
   *
   * @param owner authenticated caller
   * @param url target URL
   * @param alias custom alias or {@code null}
   * @param expiresAt expiry or {@code null}
   * @param dedupe opt-in dedupe flag; ignored when an alias is given (design §3.3)
   * @return {@link CreateResult.Created} or {@link CreateResult.Existing}
   */
  public CreateResult create(
      AuthenticatedOwner owner, String url, String alias, Instant expiresAt, boolean dedupe) {
    Instant now = clock.instant();
    validator.validateUrl(url);
    validator.validateAlias(alias);
    validator.validateExpiry(expiresAt, now);
    String normalized = normalizer.normalize(url);

    if (dedupe && alias == null) {
      Optional<Link> existing =
          links.findDedupeCandidate(owner.ownerId(), normalized, expiresAt, now);
      if (existing.isPresent()) {
        return new CreateResult.Existing(existing.get());
      }
    }

    Link link =
        alias != null
            ? insertAlias(owner, url, normalized, alias, expiresAt)
            : insertRandom(owner, url, normalized, expiresAt);
    cache.clearMissing(link.code());
    cache.put(link.code(), toCached(link));
    return new CreateResult.Created(link);
  }

  /**
   * URL-FR-5.1 / 5.2: metadata for the owner; anyone else gets 404, never 403.
   *
   * @param owner authenticated caller
   * @param code short code
   * @return the link
   */
  public Link get(AuthenticatedOwner owner, String code) {
    return links
        .findByCode(code)
        .filter(l -> l.ownerId() == owner.ownerId())
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
  }

  /**
   * URL-FR-4.3: status derived at read time with the injected clock.
   *
   * @param link link
   * @return effective status now
   */
  public EffectiveStatus statusOf(Link link) {
    return link.effectiveStatus(clock.instant());
  }

  /**
   * URL-FR-6.1 - 6.3: owner-only soft delete, idempotent, evicts the cache entry in the same
   * request.
   *
   * @param owner authenticated caller
   * @param code short code
   */
  public void deactivate(AuthenticatedOwner owner, String code) {
    Link link = get(owner, code);
    int updated = links.deactivate(link.code(), owner.ownerId(), clock.instant());
    // only DB-sourced values are logged; the path variable is caller-controlled (CRLF injection)
    LOG.debug("Deactivate link id {}: {} row(s) updated", link.id(), updated);
    cache.evict(link.code());
  }

  private Link insertAlias(
      AuthenticatedOwner owner, String url, String normalized, String alias, Instant expiresAt) {
    return links
        .insertIfCodeFree(alias, owner.ownerId(), url, normalized, true, expiresAt)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.ALIAS_CONFLICT,
                    "Alias '" + alias + "' is already in use.",
                    Map.of("alias", alias)));
  }

  /** URL-FR-1.2: up to {@code max-codegen-attempts} random codes, then 503. */
  private Link insertRandom(
      AuthenticatedOwner owner, String url, String normalized, Instant expiresAt) {
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      Optional<Link> inserted =
          links.insertIfCodeFree(codes.next(), owner.ownerId(), url, normalized, false, expiresAt);
      if (inserted.isPresent()) {
        return inserted.get();
      }
      codegenRetries.increment();
    }
    codegenExhausted.increment();
    LOG.warn("Code generation exhausted after {} attempts", maxAttempts);
    throw new ApiException(ErrorCode.CODE_GENERATION_EXHAUSTED);
  }

  private static CachedLink toCached(Link link) {
    return new CachedLink(link.id(), link.targetUrl(), link.status(), link.expiresAt());
  }
}
