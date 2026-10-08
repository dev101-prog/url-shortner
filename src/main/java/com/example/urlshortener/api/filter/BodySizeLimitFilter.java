package com.example.urlshortener.api.filter;

import com.example.urlshortener.api.error.ErrorEnvelopeWriter;
import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.service.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * URL-NFR-4.5: rejects a {@code Content-Length} above the limit (8 KB) with 413 and POST requests
 * without a {@code Content-Length} (chunked) with 411, before any body is read.
 */
@Component
@Order(FilterOrder.BODY_SIZE_LIMIT)
public class BodySizeLimitFilter extends OncePerRequestFilter {

  private final int maxBodyBytes;
  private final ErrorEnvelopeWriter errors;

  /**
   * Creates the filter.
   *
   * @param props application properties ({@code app.http.max-body-bytes})
   * @param errors error envelope writer
   */
  public BodySizeLimitFilter(AppProperties props, ErrorEnvelopeWriter errors) {
    this.maxBodyBytes = props.http().maxBodyBytes();
    this.errors = errors;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long contentLength = request.getContentLengthLong();
    if (contentLength > maxBodyBytes) {
      errors.send(
          response,
          ErrorCode.PAYLOAD_TOO_LARGE,
          "Request body must be at most " + maxBodyBytes + " bytes.",
          Map.of("max_bytes", maxBodyBytes));
      return;
    }
    if (contentLength < 0 && HttpMethod.POST.matches(request.getMethod())) {
      errors.send(
          response, ErrorCode.LENGTH_REQUIRED, ErrorCode.LENGTH_REQUIRED.defaultMessage(), null);
      return;
    }
    chain.doFilter(request, response);
  }
}
