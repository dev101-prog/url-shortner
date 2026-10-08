package com.example.urlshortener.it;

import static com.example.urlshortener.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.support.ApiClient;
import com.example.urlshortener.support.PostgresContainerSupport;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * URL-FR-8.1 / 8.2 (design §6.8). Uses its own Postgres container so it can be stopped to prove
 * that readiness fails while liveness stays up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HealthIT {

  private static final PostgreSQLContainer<?> DEDICATED =
      new PostgreSQLContainer<>(PostgresContainerSupport.IMAGE);

  static {
    DEDICATED.start();
  }

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DEDICATED::getJdbcUrl);
    registry.add("spring.datasource.username", DEDICATED::getUsername);
    registry.add("spring.datasource.password", DEDICATED::getPassword);
  }

  private HttpResponse<String> get(String path) {
    return new ApiClient(port).get(path, null);
  }

  @Test
  @Order(1)
  void fr8_1_healthz200() {
    HttpResponse<String> response = get("/healthz");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
  }

  @Test
  @Order(2)
  void fr8_2_readyz200WhenDbUp() {
    HttpResponse<String> response = get("/readyz");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(json(response).get("status").asText()).isEqualTo("UP");
    assertThat(json(response).path("checks").path("db").asText()).isEqualTo("UP");
  }

  @Test
  @Order(3)
  void nfr4_6_errorPageUsesTheEnvelope() {
    HttpResponse<String> response = get("/error");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body())
        .isEqualTo(
            "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"Not found.\",\"details\":null}}");
  }

  @Test
  @Order(10)
  void fr8_2_readyz503WhenDbDown() {
    DEDICATED.stop();

    HttpResponse<String> ready = get("/readyz");
    HttpResponse<String> live = get("/healthz");

    assertThat(ready.statusCode()).isEqualTo(503);
    assertThat(json(ready).get("status").asText()).isEqualTo("DOWN");
    assertThat(json(ready).path("checks").path("db").asText()).isEqualTo("DOWN");
    assertThat(live.statusCode()).as("liveness has no dependency checks").isEqualTo(200);
  }
}
