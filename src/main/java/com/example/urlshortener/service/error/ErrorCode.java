package com.example.urlshortener.service.error;

/**
 * Machine-readable error codes and their HTTP status (design §5.2). Lives in the service layer so
 * services can raise them without depending on the web layer (§8.4 rule 1).
 */
public enum ErrorCode {
  MALFORMED_REQUEST(400, "Request body is not valid JSON."),
  UNAUTHORIZED(401, "A valid X-API-Key header is required."),
  NOT_FOUND(404, "Not found."),
  ALIAS_CONFLICT(409, "Alias is already in use."),
  LINK_EXPIRED(410, "This link has expired."),
  LENGTH_REQUIRED(411, "Content-Length is required."),
  PAYLOAD_TOO_LARGE(413, "Request body is too large."),
  INVALID_URL(422, "URL is not valid."),
  TARGET_BLOCKED(422, "Target domain is not allowed."),
  INVALID_ALIAS(422, "Alias must match ^[A-Za-z0-9_-]{4,32}$."),
  RESERVED_ALIAS(422, "Alias is a reserved word."),
  INVALID_EXPIRY(422, "expires_at is not valid."),
  INVALID_DATE_RANGE(422, "Date range is not valid."),
  VALIDATION_FAILED(422, "Request validation failed."),
  RATE_LIMITED(429, "Rate limit exceeded."),
  INTERNAL_ERROR(500, "Internal error."),
  CODE_GENERATION_EXHAUSTED(503, "Could not generate a unique code. Please retry."),
  NOT_READY(503, "Service is not ready.");

  private final int httpStatus;
  private final String defaultMessage;

  ErrorCode(int httpStatus, String defaultMessage) {
    this.httpStatus = httpStatus;
    this.defaultMessage = defaultMessage;
  }

  /**
   * HTTP status for this code.
   *
   * @return status code
   */
  public int httpStatus() {
    return httpStatus;
  }

  /**
   * Generic, safe message for this code.
   *
   * @return message
   */
  public String defaultMessage() {
    return defaultMessage;
  }
}
