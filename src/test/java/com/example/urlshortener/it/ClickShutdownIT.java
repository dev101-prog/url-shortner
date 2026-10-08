package com.example.urlshortener.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.UrlShortenerApplication;
import com.example.urlshortener.infra.ClickBuffer;
import com.example.urlshortener.service.domain.ClickEvent;
import com.example.urlshortener.support.PostgresContainerSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * URL-FR-7.6 / URL-OBS-3: closing the application (as on SIGTERM) flushes clicks still in the
 * buffer. Periodic flushing is pushed out to one hour so only the shutdown drain can write them.
 */
class ClickShutdownIT {

  @Test
  void fr7_6_gracefulShutdownFlushesTheBuffer() throws Exception {
    PostgreSQLContainer<?> pg = PostgresContainerSupport.postgres();
    String url = PostgresContainerSupport.jdbcUrlForDatabase("shutdown_it");

    ConfigurableApplicationContext app =
        new SpringApplicationBuilder(UrlShortenerApplication.class)
            .profiles("test")
            .run(
                "--server.port=0",
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + pg.getUsername(),
                "--spring.datasource.password=" + pg.getPassword(),
                "--app.analytics.flush-interval=PT1H");
    long linkId;
    try {
      JdbcClient jdbc = app.getBean(JdbcClient.class);
      long owner =
          jdbc.sql("INSERT INTO owners (name) VALUES ('shutdown') RETURNING id")
              .query(Long.class)
              .single();
      linkId =
          jdbc.sql(
                  "INSERT INTO links (code, owner_id, target_url, normalized_url)"
                      + " VALUES ('shutdwn', :o, 'https://a.io', 'https://a.io') RETURNING id")
              .param("o", owner)
              .query(Long.class)
              .single();
      ClickBuffer buffer = app.getBean(ClickBuffer.class);
      for (int i = 0; i < 3; i++) {
        assertThat(
                buffer.offer(
                    new ClickEvent(
                        linkId,
                        Instant.parse("2026-10-07T21:00:00Z"),
                        null,
                        null,
                        false,
                        null,
                        null)))
            .isTrue();
      }
      assertThat(buffer.size()).isEqualTo(3);
    } finally {
      app.close();
    }

    try (Connection c = DriverManager.getConnection(url, pg.getUsername(), pg.getPassword());
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT click_count, (SELECT count(*) FROM click_events WHERE link_id = "
                    + linkId
                    + ") FROM links WHERE id = "
                    + linkId)) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong(1)).isEqualTo(3L);
      assertThat(rs.getLong(2)).isEqualTo(3L);
    }
  }
}
