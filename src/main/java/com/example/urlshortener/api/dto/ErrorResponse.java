package com.example.urlshortener.api.dto;

import java.util.Map;

/**
 * The error envelope for every non-2xx response (design §5.1): {@code {"error": {"code": ...,
 * "message": ..., "details": ...}}}.
 *
 * @param error error body
 */
public record ErrorResponse(ErrorBody error) {

  /**
   * Builds an envelope.
   *
   * @param code machine-readable code (§5.2)
   * @param message safe, human-readable message
   * @param details optional details, or {@code null}
   * @return envelope
   */
  public static ErrorResponse of(String code, String message, Map<String, Object> details) {
    return new ErrorResponse(new ErrorBody(code, message, details));
  }

  /**
   * Error body.
   *
   * @param code machine-readable code
   * @param message human-readable message
   * @param details optional details
   */
  public record ErrorBody(String code, String message, Map<String, Object> details) {

    /** Copies details so the envelope is immutable. */
    public ErrorBody {
      details = details == null ? null : Map.copyOf(details);
    }
  }
}
