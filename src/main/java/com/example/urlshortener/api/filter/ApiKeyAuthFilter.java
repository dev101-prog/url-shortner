package com.example.urlshortener.api.filter;

import com.example.urlshortener.api.error.ErrorEnvelopeWriter;
import com.example.urlshortener.service.ApiKeyService;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * URL-NFR-4.1: every {@code /api/v1/**} request needs a valid {@code X-API-Key}; missing, unknown
 * or revoked keys get 401 {@code UNAUTHORIZED}. On success the {@link AuthenticatedOwner} is stored
 * as a request attribute. The raw key is never logged (design §9.5).
 */
@Component
@Order(FilterOrder.API_KEY_AUTH)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  /** Request header carrying the raw API key. */
  public static final String HEADER = "X-API-Key";

  /** Request attribute holding the {@link AuthenticatedOwner}. */
  public static final String OWNER_ATTRIBUTE = AuthenticatedOwner.class.getName();

  private static final Logger LOG = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
  private static final List<String> PROTECTED_PREFIX = List.of("api", "v1");

  private final ApiKeyService apiKeys;
  private final ErrorEnvelopeWriter errors;

  /**
   * Creates the filter.
   *
   * @param apiKeys API key service
   * @param errors error envelope writer
   */
  public ApiKeyAuthFilter(ApiKeyService apiKeys, ErrorEnvelopeWriter errors) {
    this.apiKeys = apiKeys;
    this.errors = errors;
  }

  /**
   * Protects every path whose first two decoded segments are {@code api/v1}. Segments are derived
   * the way Spring MVC matches them (percent-decoded, {@code ;} parameters and empty segments
   * ignored), so encodings such as {@code /%61pi/v1} or {@code /api;x=1/v1} cannot bypass the
   * check.
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    List<String> segments =
        requestPath(request).pathWithinApplication().elements().stream()
            .filter(PathContainer.PathSegment.class::isInstance)
            .map(e -> ((PathContainer.PathSegment) e).valueToMatch())
            .filter(s -> !s.isEmpty())
            .limit(PROTECTED_PREFIX.size())
            .map(s -> s.toLowerCase(Locale.ROOT))
            .toList();
    return !segments.equals(PROTECTED_PREFIX);
  }

  private static RequestPath requestPath(HttpServletRequest request) {
    return ServletRequestPathUtils.hasParsedRequestPath(request)
        ? ServletRequestPathUtils.getParsedRequestPath(request)
        : RequestPath.parse(request.getRequestURI(), request.getContextPath());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String rawKey = request.getHeader(HEADER);
    if (rawKey == null || rawKey.isBlank()) {
      LOG.debug("API request rejected: missing API key");
      unauthorized(response);
      return;
    }
    Optional<AuthenticatedOwner> owner = apiKeys.authenticate(rawKey);
    if (owner.isEmpty()) {
      LOG.debug("API request rejected: unknown or revoked API key");
      unauthorized(response);
      return;
    }
    request.setAttribute(OWNER_ATTRIBUTE, owner.get());
    chain.doFilter(request, response);
  }

  private void unauthorized(HttpServletResponse response) throws IOException {
    errors.send(response, ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage(), null);
  }
}
