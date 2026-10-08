package com.example.urlshortener.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * The error envelope for every non-2xx response (design §5.1): {@code {"error": {"code": ...,
 * "message": ..., "details": ...}}}.
 *
 * @param error error body
 */
@Schema(
    name = "ErrorResponse",
    description = "Error envelope used by every non-2xx response (design §5.1).",
    example =
        "{\"error\":{\"code\":\"ALIAS_CONFLICT\",\"message\":\"Alias 'spring-docs' is already in"
            + " use.\",\"details\":{\"alias\":\"spring-docs\"}}}")
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
  @Schema(name = "ErrorBody")
  public record ErrorBody(
      @Schema(
              description = "Machine-readable error code (design §5.2).",
              allowableValues = {
                "MALFORMED_REQUEST",
                "UNAUTHORIZED",
                "NOT_FOUND",
                "ALIAS_CONFLICT",
                "LINK_EXPIRED",
                "LENGTH_REQUIRED",
                "PAYLOAD_TOO_LARGE",
                "INVALID_URL",
                "TARGET_BLOCKED",
                "INVALID_ALIAS",
                "RESERVED_ALIAS",
                "INVALID_EXPIRY",
                "INVALID_DATE_RANGE",
                "VALIDATION_FAILED",
                "RATE_LIMITED",
                "INTERNAL_ERROR",
                "CODE_GENERATION_EXHAUSTED",
                "NOT_READY"
              })
          String code,
      @Schema(description = "Human-readable message; never contains stack traces.") String message,
      @Schema(description = "Optional machine-readable details.", nullable = true)
          Map<String, Object> details) {

    /** Copies details so the envelope is immutable. */
    public ErrorBody {
      details = details == null ? null : Map.copyOf(details);
    }
  }
}
