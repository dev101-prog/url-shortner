package com.example.urlshortener.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.ApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Gate G7 (design §8.2): the generated {@code /openapi.json} must equal {@code
 * src/test/resources/openapi-baseline.json}. A diff fails the build unless the baseline is updated
 * in the same PR and approved by a human (§10.5). To regenerate after an approved change: {@code
 * ./mvnw test -Dtest=OpenApiContractTest -DupdateOpenApiBaseline=true}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiContractTest extends AbstractPostgresIT {

  private static final Path BASELINE = Path.of("src/test/resources/openapi-baseline.json");
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  private final int port;
  private JsonNode actual;

  @Autowired
  OpenApiContractTest(@LocalServerPort int port) {
    this.port = port;
  }

  /** Canonical form: keys sorted, pretty-printed, so diffs are readable. */
  private static String canonical(JsonNode node) throws Exception {
    return JSON.writeValueAsString(JSON.treeToValue(node, Object.class)) + "\n";
  }

  @BeforeAll
  void fetch() throws Exception {
    actual = ApiClient.json(new ApiClient(port).get("/openapi.json", null));
    if (Boolean.getBoolean("updateOpenApiBaseline")) {
      Files.writeString(BASELINE, canonical(actual), StandardCharsets.UTF_8);
    }
  }

  @Test
  void g7_generatedSpecMatchesBaseline() throws Exception {
    assertThat(BASELINE).as("missing baseline; see class Javadoc").exists();
    String expected = Files.readString(BASELINE, StandardCharsets.UTF_8);

    assertThat(canonical(actual))
        .as("API drift: update openapi-baseline.json only with human approval (§10.5)")
        .isEqualTo(expected);
  }

  @Test
  void docs1_everyEndpointAndErrorCodeIsDocumented() {
    JsonNode paths = actual.get("paths");
    assertThat(paths.fieldNames())
        .toIterable()
        .contains(
            "/api/v1/links",
            "/api/v1/links/{code}",
            "/api/v1/links/{code}/stats",
            "/{code}",
            "/healthz",
            "/readyz");
    assertThat(paths.has("/error")).isFalse();
    assertThat(paths.at("/~1api~1v1~1links/post/responses").fieldNames())
        .toIterable()
        .contains("200", "201", "400", "401", "409", "411", "413", "422", "429", "503");
    assertThat(paths.at("/~1{code}/get/responses").fieldNames())
        .toIterable()
        .contains("302", "404", "410", "429");
    assertThat(paths.at("/~1api~1v1~1links/post/security/0").has("ApiKeyAuth")).isTrue();
    assertThat(actual.at("/components/securitySchemes/ApiKeyAuth/name").asText())
        .isEqualTo("X-API-Key");
    assertThat(actual.at("/components/schemas/ErrorBody/properties/code/enum"))
        .extracting(JsonNode::asText)
        .contains(
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
            "NOT_READY");
    assertThat(actual.at("/servers/0/url").asText()).isEqualTo("http://localhost:8080");
  }
}
