package com.example.urlshortener.api.filter;

import org.springframework.core.Ordered;

/**
 * Servlet filter order from design §2.1: RequestId, (AccessLog), BodySizeLimit, SecurityHeaders,
 * ApiKeyAuth. AccessLog runs just inside RequestId so every response, including filter rejections,
 * is logged with its request_id.
 */
final class FilterOrder {

  static final int REQUEST_ID = Ordered.HIGHEST_PRECEDENCE;
  static final int ACCESS_LOG = REQUEST_ID + 5;
  static final int BODY_SIZE_LIMIT = REQUEST_ID + 10;
  static final int SECURITY_HEADERS = REQUEST_ID + 20;
  static final int API_KEY_AUTH = REQUEST_ID + 30;

  private FilterOrder() {}
}
