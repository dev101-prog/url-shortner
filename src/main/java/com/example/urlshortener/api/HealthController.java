package com.example.urlshortener.api;

import com.example.urlshortener.api.dto.ErrorResponse;
import com.example.urlshortener.service.ReadinessService;
import com.example.urlshortener.service.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness and readiness probes (design §5.3, §6.8; URL-FR-8.1, 8.2). Public. */
@RestController
@Tag(name = "health", description = "Liveness and readiness probes")
public class HealthController {

  private static final String UP = "UP";
  private static final String DOWN = "DOWN";
  private static final String NOT_READY_MESSAGE = "Service is not ready: database unreachable.";

  private final ReadinessService readiness;

  /**
   * Creates the controller.
   *
   * @param readiness readiness service
   */
  public HealthController(ReadinessService readiness) {
    this.readiness = readiness;
  }

  /**
   * URL-FR-8.1: 200 while the process runs; no dependency checks.
   *
   * @return {@code {"status":"UP"}}
   */
  @GetMapping("/healthz")
  @Operation(summary = "Liveness", description = "Always 200 while the process is running.")
  @ApiResponse(responseCode = "200", description = "Process is alive.")
  public Map<String, Object> liveness() {
    return Map.of("status", UP);
  }

  /**
   * URL-FR-8.2: 200 {@code {"status":"UP","checks":{"db":"UP"}}} when the database answers within 1
   * s; otherwise 503 with the standard envelope, code {@code NOT_READY} (design §5.2) and the
   * per-dependency checks in {@code details}. Readiness checks the database only (design §16.3).
   *
   * @return readiness
   */
  @GetMapping("/readyz")
  @Operation(summary = "Readiness", description = "Checks the database only (design §16.3).")
  @ApiResponse(
      responseCode = "200",
      description = "Ready: {\"status\":\"UP\",\"checks\":{\"db\":\"UP\"}}")
  @ApiResponse(
      responseCode = "503",
      description = "NOT_READY: database unreachable; details.checks.db = DOWN",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<Object> readiness() {
    if (readiness.isDatabaseUp()) {
      return ResponseEntity.ok(Map.of("status", UP, "checks", Map.of("db", UP)));
    }
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ErrorResponse.of(
                ErrorCode.NOT_READY.name(),
                NOT_READY_MESSAGE,
                Map.of("checks", Map.of("db", DOWN))));
  }
}
