package com.example.urlshortener.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * URL-OBS-1: reads {@code X-Request-Id} or generates a UUID, puts it in the MDC as {@code
 * request_id} and echoes it on the response. Incoming ids that are not short and plain-text-safe
 * are replaced, so client input cannot inject into logs or response headers.
 */
@Component
@Order(FilterOrder.REQUEST_ID)
public class RequestIdFilter extends OncePerRequestFilter {

  /** Request and response header name. */
  public static final String HEADER = "X-Request-Id";

  /** MDC key used in JSON logs (design §9.5). */
  public static final String MDC_KEY = "request_id";

  private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request.getHeader(HEADER));
    MDC.put(MDC_KEY, requestId);
    response.setHeader(HEADER, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  /**
   * Accepts the client id only if it matches {@code [A-Za-z0-9._-]{1,64}} (no whitespace, CR/LF or
   * markup, so it is safe to echo in a header and to log); otherwise generates a UUID.
   *
   * @param incoming raw {@code X-Request-Id} header, may be {@code null}
   * @return the id to use for this request
   */
  static String resolveRequestId(String incoming) {
    if (incoming != null && SAFE_ID.matcher(incoming).matches()) {
      return incoming;
    }
    return UUID.randomUUID().toString();
  }
}
