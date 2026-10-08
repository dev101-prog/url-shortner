package com.example.urlshortener.api;

import com.example.urlshortener.service.ReadinessService;
import io.swagger.v3.oas.annotations.Operation;
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
   * URL-FR-8.2: 200 when the database answers within 1 s, otherwise 503 with the same shape (design
   * §5.3); readiness checks the database only (design §16.3).
   *
   * @return readiness with per-dependency status
   */
  @GetMapping("/readyz")
  @Operation(summary = "Readiness", description = "Checks the database only (design §16.3).")
  @ApiResponse(responseCode = "200", description = "{\"status\":\"UP\",\"checks\":{\"db\":\"UP\"}}")
  @ApiResponse(
      responseCode = "503",
      description = "NOT_READY: {\"status\":\"DOWN\",\"checks\":{\"db\":\"DOWN\"}}")
  public ResponseEntity<Map<String, Object>> readiness() {
    boolean dbUp = readiness.isDatabaseUp();
    String status = dbUp ? UP : DOWN;
    return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("status", status, "checks", Map.of("db", status)));
  }
}
