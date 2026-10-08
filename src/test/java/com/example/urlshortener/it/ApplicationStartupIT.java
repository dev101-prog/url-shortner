package com.example.urlshortener.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.UrlShortenerApplication;
import com.example.urlshortener.support.PostgresContainerSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/** Build step 1 "done when": the empty application boots through {@code main} against Postgres. */
class ApplicationStartupIT {

  @Test
  void mainStartsTheApplicationAndActuatorHealthIsUp() throws Exception {
    PostgreSQLContainer<?> pg = PostgresContainerSupport.postgres();

    UrlShortenerApplication.main(
        new String[] {
          "--server.port=0",
          "--spring.datasource.url=" + pg.getJdbcUrl(),
          "--spring.datasource.username=" + pg.getUsername(),
          "--spring.datasource.password=" + pg.getPassword(),
          "--app.analytics.ip-salt=startup-it-salt",
          "--context.initializer.classes=" + CapturingInitializer.class.getName()
        });

    ConfigurableApplicationContext context = CapturingInitializer.CONTEXT.get();
    try {
      assertThat(context).isNotNull();
      assertThat(context.isRunning()).isTrue();
      String port = context.getEnvironment().getProperty("local.server.port");
      assertThat(port).isNotBlank();

      HttpResponse<String> health =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(
                          URI.create("http://localhost:" + port + "/actuator/health"))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofString());

      assertThat(health.statusCode()).isEqualTo(200);
      assertThat(health.body()).contains("\"status\":\"UP\"");
    } finally {
      if (context != null) {
        context.close();
      }
    }
  }

  /** Captures the context created by {@code main} so the test can inspect and close it. */
  public static final class CapturingInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final AtomicReference<ConfigurableApplicationContext> CONTEXT = new AtomicReference<>();

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
      CONTEXT.set(applicationContext);
    }
  }
}
