package com.example.urlshortener.api;

import com.example.urlshortener.api.error.GlobalExceptionHandler;
import com.example.urlshortener.service.RedirectService;
import com.example.urlshortener.service.domain.RedirectResult;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /{code}} (design §5.3). Literal routes ({@code /api/**}, {@code /docs}, actuator) are
 * matched first, so system paths always win over the code pattern (R11).
 */
@RestController
public class RedirectController {

  /** Design D6 / URL-FR-3.3: never cacheable by browsers or proxies. */
  static final String NO_STORE = "private, no-cache, no-store, max-age=0";

  private final RedirectService redirects;

  /**
   * Creates the controller.
   *
   * @param redirects redirect service
   */
  public RedirectController(RedirectService redirects) {
    this.redirects = redirects;
  }

  /**
   * URL-FR-3.1 - 3.3, 3.7: 302 with no-store headers, or 404 / 410 / 429 envelopes that never
   * reveal the target or owner.
   *
   * @param code short code
   * @param request servlet request (client IP, Referer, User-Agent)
   * @return 302 response
   */
  @GetMapping("/{code:[A-Za-z0-9_-]{4,32}}")
  public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
    RedirectResult result =
        redirects.resolve(
            code,
            request.getRemoteAddr(),
            request.getHeader(HttpHeaders.REFERER),
            request.getHeader(HttpHeaders.USER_AGENT));
    return switch (result) {
      case RedirectResult.Found found ->
          ResponseEntity.status(HttpStatus.FOUND)
              .header(HttpHeaders.LOCATION, found.targetUrl())
              .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
              .header(HttpHeaders.PRAGMA, "no-cache")
              .build();
      case RedirectResult.NotFound notFound -> throw new ApiException(ErrorCode.NOT_FOUND);
      case RedirectResult.Gone gone -> throw new ApiException(ErrorCode.LINK_EXPIRED);
      case RedirectResult.RateLimited limited ->
          throw new ApiException(
              ErrorCode.RATE_LIMITED,
              ErrorCode.RATE_LIMITED.defaultMessage(),
              Map.of(GlobalExceptionHandler.RETRY_AFTER_DETAIL, limited.retryAfterSeconds()));
    };
  }
}
