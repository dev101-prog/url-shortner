package com.example.urlshortener.api.filter;

import com.example.urlshortener.service.domain.AuthenticatedOwner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * URL-OBS-1 / design §9.5: one structured access-log line per request with {@code route} (the
 * matched route template, never the raw path), {@code method}, {@code status}, {@code latency_ms},
 * {@code owner_id} when authenticated and {@code code} when the route has one. Never logs the
 * client IP, API keys, headers or bodies.
 */
@Component
@Order(FilterOrder.ACCESS_LOG)
public class AccessLogFilter extends OncePerRequestFilter {

  /** Route value when no handler matched (unknown paths are not logged verbatim). */
  static final String UNMATCHED = "UNMATCHED";

  private static final Logger LOG = LoggerFactory.getLogger(AccessLogFilter.class);
  private static final Pattern SAFE_CODE = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
  private static final long NANOS_PER_MILLI = 1_000_000L;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long latencyMs = (System.nanoTime() - start) / NANOS_PER_MILLI;
      put("route", route(request));
      put("method", request.getMethod());
      put("status", String.valueOf(response.getStatus()));
      put("latency_ms", String.valueOf(latencyMs));
      if (request.getAttribute(ApiKeyAuthFilter.OWNER_ATTRIBUTE) instanceof AuthenticatedOwner o) {
        put("owner_id", String.valueOf(o.ownerId()));
      }
      String code = code(request);
      if (code != null) {
        put("code", code);
      }
      try {
        LOG.info("request completed");
      } finally {
        for (String key :
            new String[] {"route", "method", "status", "latency_ms", "owner_id", "code"}) {
          MDC.remove(key);
        }
      }
    }
  }

  private static void put(String key, String value) {
    MDC.put(key, value);
  }

  private static String route(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern instanceof String p ? p : UNMATCHED;
  }

  private static String code(HttpServletRequest request) {
    if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
            instanceof Map<?, ?> vars
        && vars.get("code") instanceof String code
        && SAFE_CODE.matcher(code).matches()) {
      return code;
    }
    return null;
  }
}
