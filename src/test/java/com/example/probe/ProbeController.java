package com.example.probe;

import com.example.urlshortener.api.filter.ApiKeyAuthFilter;
import com.example.urlshortener.api.filter.RequestIdFilter;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller used by web slice tests to exercise filters and error mapping. It lives
 * outside {@code com.example.urlshortener} so component scanning of the real app never sees it.
 */
@RestController
public class ProbeController {

  /**
   * Request body with a required field and a typed field.
   *
   * @param url required text
   * @param count optional number
   */
  public record EchoRequest(@NotBlank String url, Integer count) {}

  /**
   * Returns the authenticated owner set by {@link ApiKeyAuthFilter}.
   *
   * @param request request
   * @return owner ids
   */
  @GetMapping("/api/v1/probe/owner")
  public Map<String, Object> owner(HttpServletRequest request) {
    AuthenticatedOwner owner =
        (AuthenticatedOwner) request.getAttribute(ApiKeyAuthFilter.OWNER_ATTRIBUTE);
    return Map.of("owner_id", owner.ownerId(), "api_key_id", owner.apiKeyId());
  }

  /**
   * Public endpoint returning the MDC request id seen during the request.
   *
   * @return request id from the MDC
   */
  @GetMapping("/probe/public")
  public Map<String, Object> publicProbe() {
    Map<String, Object> body = new HashMap<>();
    body.put("request_id", MDC.get(RequestIdFilter.MDC_KEY));
    return body;
  }

  /**
   * Echoes a validated body.
   *
   * @param body request body
   * @return the url
   */
  @PostMapping("/probe/echo")
  public Map<String, Object> echo(@Valid @RequestBody EchoRequest body) {
    return Map.of("url", body.url());
  }

  /**
   * Throws an expected API error.
   *
   * @return never
   */
  @GetMapping("/probe/api-error")
  public Map<String, Object> apiError() {
    throw new ApiException(
        ErrorCode.ALIAS_CONFLICT,
        "Alias 'spring-docs' is already in use.",
        Map.of("alias", "spring-docs"));
  }

  /**
   * Throws an unexpected error with an internal message.
   *
   * @return never
   */
  @GetMapping("/probe/boom")
  public Map<String, Object> boom() {
    throw new IllegalStateException("secret internal detail db=10.0.0.5");
  }

  /**
   * Takes a typed query parameter.
   *
   * @param n required number
   * @return the number
   */
  @GetMapping("/probe/param")
  public Map<String, Object> param(@RequestParam int n) {
    return Map.of("n", n);
  }
}
