package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Validation rules for link creation, exactly as in design §3.3. */
@Component
public final class LinkValidator {

  private static final Pattern ALIAS = Pattern.compile("^[A-Za-z0-9_-]{4,32}$");

  private final int maxUrlLength;
  private final Duration maxExpiry;
  private final List<String> allowedSchemes;
  private final Set<String> allowedSchemeSet;
  private final Set<String> blockedDomains;
  private final Set<String> reservedWords;
  private final String selfHost;

  /**
   * Creates the validator from configuration.
   *
   * @param props application properties
   */
  public LinkValidator(AppProperties props) {
    this.maxUrlLength = props.links().maxUrlLength();
    this.maxExpiry = props.links().maxExpiry();
    this.allowedSchemes =
        props.links().allowedSchemes().stream().map(LinkValidator::lower).toList();
    this.allowedSchemeSet = Set.copyOf(allowedSchemes);
    this.blockedDomains = lowerSet(props.links().blockedDomains());
    this.reservedWords = lowerSet(props.links().reservedWords());
    this.selfHost = lower(URI.create(props.baseUrl()).getHost());
  }

  /**
   * URL-FR-1.5 / URL-NFR-4.7: required, length, parseable, scheme allowlist, host present, no
   * credentials, not this service's own host, not on the denylist.
   *
   * @param url submitted target URL
   * @return the parsed URI
   * @throws ApiException {@code INVALID_URL} or {@code TARGET_BLOCKED}
   */
  public URI validateUrl(String url) {
    if (url == null || url.isBlank()) {
      throw invalidUrl("url is required.");
    }
    if (url.length() > maxUrlLength) {
      throw new ApiException(
          ErrorCode.INVALID_URL,
          "url must be at most " + maxUrlLength + " characters.",
          Map.of("max_length", maxUrlLength));
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw invalidUrl("url is not a valid URI.");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !allowedSchemeSet.contains(lower(scheme))) {
      throw new ApiException(
          ErrorCode.INVALID_URL,
          "url scheme must be one of " + allowedSchemes + ".",
          Map.of("allowed_schemes", allowedSchemes));
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw invalidUrl("url must contain a host.");
    }
    if (uri.getRawUserInfo() != null) {
      throw invalidUrl("url must not contain credentials.");
    }
    String lowerHost = lower(host);
    if (lowerHost.equals(selfHost)) {
      throw invalidUrl("url must not point to this service.");
    }
    if (blockedDomains.contains(lowerHost)) {
      throw new ApiException(ErrorCode.TARGET_BLOCKED, ErrorCode.TARGET_BLOCKED.defaultMessage());
    }
    return uri;
  }

  /**
   * URL-FR-2.1 / 2.2: optional alias; format first, then the case-insensitive reserved-word list.
   *
   * @param alias submitted alias, or {@code null}
   * @throws ApiException {@code INVALID_ALIAS} or {@code RESERVED_ALIAS}
   */
  public void validateAlias(String alias) {
    if (alias == null) {
      return;
    }
    if (!ALIAS.matcher(alias).matches()) {
      throw new ApiException(
          ErrorCode.INVALID_ALIAS,
          ErrorCode.INVALID_ALIAS.defaultMessage(),
          Map.of("alias", alias));
    }
    if (reservedWords.contains(lower(alias))) {
      throw new ApiException(
          ErrorCode.RESERVED_ALIAS, "Alias '" + alias + "' is reserved.", Map.of("alias", alias));
    }
  }

  /**
   * URL-FR-4.2: optional expiry, strictly after {@code now} and no later than {@code now +
   * max-expiry}.
   *
   * @param expiresAt requested expiry, or {@code null}
   * @param now current instant from the injected clock
   * @throws ApiException {@code INVALID_EXPIRY}
   */
  public void validateExpiry(Instant expiresAt, Instant now) {
    if (expiresAt == null) {
      return;
    }
    if (!expiresAt.isAfter(now)) {
      throw new ApiException(ErrorCode.INVALID_EXPIRY, "expires_at must be in the future.");
    }
    if (expiresAt.isAfter(now.plus(maxExpiry))) {
      throw new ApiException(
          ErrorCode.INVALID_EXPIRY,
          "expires_at must be at most " + maxExpiry.toDays() + " days from now.",
          Map.of("max_days", maxExpiry.toDays()));
    }
  }

  private static ApiException invalidUrl(String message) {
    return new ApiException(ErrorCode.INVALID_URL, message);
  }

  private static String lower(String s) {
    return s == null ? null : s.toLowerCase(Locale.ROOT);
  }

  private static Set<String> lowerSet(List<String> values) {
    return values.stream().map(LinkValidator::lower).collect(Collectors.toUnmodifiableSet());
  }
}
