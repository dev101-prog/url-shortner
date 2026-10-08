package com.example.urlshortener.service.error;

import java.io.Serial;
import java.util.Map;

/**
 * The single exception type for expected failures (design §3.1). {@code GlobalExceptionHandler}
 * maps it to the §5.1 error envelope. Messages and details must never contain secrets, raw IPs or
 * stack traces.
 */
public class ApiException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final ErrorCode code;
  private final transient Map<String, Object> details;

  /**
   * Creates an exception with the code's default message and no details.
   *
   * @param code error code
   */
  public ApiException(ErrorCode code) {
    this(code, code.defaultMessage(), null);
  }

  /**
   * Creates an exception with a specific message.
   *
   * @param code error code
   * @param message safe, human-readable message
   */
  public ApiException(ErrorCode code, String message) {
    this(code, message, null);
  }

  /**
   * Creates an exception with a message and details.
   *
   * @param code error code
   * @param message safe, human-readable message
   * @param details optional machine-readable details (snake_case keys)
   */
  public ApiException(ErrorCode code, String message, Map<String, Object> details) {
    super(message, null, false, false);
    this.code = code;
    this.details = details == null ? null : Map.copyOf(details);
  }

  /**
   * Error code.
   *
   * @return code
   */
  public ErrorCode code() {
    return code;
  }

  /**
   * Optional details.
   *
   * @return immutable details, or {@code null}
   */
  public Map<String, Object> details() {
    return details;
  }
}
