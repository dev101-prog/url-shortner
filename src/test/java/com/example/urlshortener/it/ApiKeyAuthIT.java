package com.example.urlshortener.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.TestData;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Build step 6 "done when": the 401 paths, end to end on Tomcat and Postgres. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiKeyAuthIT extends AbstractPostgresIT {

  private static final String ACTIVE = "test-key-it-active-0001";
  private static final String REVOKED = "test-key-it-revoked-0002";

  private final HttpClient http = HttpClient.newHttpClient();
  private final JdbcClient jdbc;
  private final int port;

  @Autowired
  ApiKeyAuthIT(JdbcClient jdbc, @LocalServerPort int port) {
    this.jdbc = jdbc;
    this.port = port;
  }

  @BeforeAll
  void keys() {
    TestData data = new TestData(jdbc);
    long owner = data.owner("it-owner");
    data.apiKey(owner, ACTIVE, false);
    data.apiKey(owner, REVOKED, true);
  }

  private HttpResponse<String> get(String path, String apiKey) throws Exception {
    HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (apiKey != null) {
      req.header("X-API-Key", apiKey);
    }
    return http.send(req.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private static void assertUnauthorizedEnvelope(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(ct -> assertThat(ct).startsWith("application/json"));
    assertThat(response.body())
        .isEqualTo(
            "{\"error\":{\"code\":\"UNAUTHORIZED\","
                + "\"message\":\"A valid X-API-Key header is required.\",\"details\":null}}");
    assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
    assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
  }

  @Test
  void nfr4_1_missingKey401() throws Exception {
    assertUnauthorizedEnvelope(get("/api/v1/links", null));
  }

  @Test
  void nfr4_1_unknownKey401() throws Exception {
    assertUnauthorizedEnvelope(get("/api/v1/links", "not-a-real-key"));
  }

  @Test
  void nfr4_2_revokedKey401() throws Exception {
    assertUnauthorizedEnvelope(get("/api/v1/links", REVOKED));
  }

  @Test
  void nfr4_1_pathParameterOrEncodingCannotBypassAuthOnRealServer() throws Exception {
    assertUnauthorizedEnvelope(get("/api;jsessionid=x/v1/links", null));
    assertUnauthorizedEnvelope(get("/%61pi/v1/links", null));
  }

  @Test
  void nfr4_1_validKeyPassesAuthentication() throws Exception {
    // no controllers exist yet (step 7), so an authenticated request reaches MVC and gets 404
    HttpResponse<String> response = get("/api/v1/links/anything", ACTIVE);

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
  }

  @Test
  void nfr4_1_publicEndpointsNeedNoKey() throws Exception {
    assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200);
  }

  @Test
  void nfr4_5_oversizeBody413AndChunkedPost411OnRealServer() throws Exception {
    URI uri = URI.create("http://localhost:" + port + "/api/v1/links");
    byte[] big = ("{\"url\":\"" + "a".repeat(9000) + "\"}").getBytes(StandardCharsets.UTF_8);

    HttpResponse<String> tooLarge =
        http.send(
            HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(big))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> chunked =
        http.send(
            HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("X-API-Key", ACTIVE)
                .POST(
                    HttpRequest.BodyPublishers.ofInputStream(
                        () -> new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(tooLarge.statusCode()).isEqualTo(413);
    assertThat(tooLarge.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    assertThat(chunked.statusCode()).isEqualTo(411);
    assertThat(chunked.body()).contains("\"code\":\"LENGTH_REQUIRED\"");
  }
}
