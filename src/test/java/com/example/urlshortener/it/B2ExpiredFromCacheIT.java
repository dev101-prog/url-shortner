package com.example.urlshortener.it;

import static com.example.urlshortener.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.ApiClient;
import com.example.urlshortener.support.TestData;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * B2 regression, the engineer's curl reproduction (design §11.3): the first request for an expired
 * link is answered from the DB, the second from the cache. Both must be 410.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class B2ExpiredFromCacheIT extends AbstractPostgresIT {

  @Test
  void b2_expiredLinkIs410FromDbAndFromCache(
      @LocalServerPort int port, @Autowired JdbcClient jdbc) {
    long owner = new TestData(jdbc).owner("b2-owner");
    jdbc.sql(
            "INSERT INTO links (code, owner_id, target_url, normalized_url, created_at, expires_at)"
                + " VALUES ('b2Expired', :o, 'https://www.postgresql.org/docs/', 'x',"
                + " now() - interval '10 days', now() - interval '1 day')")
        .param("o", owner)
        .update();
    ApiClient api = new ApiClient(port);

    HttpResponse<String> fromDb = api.get("/b2Expired", null);
    HttpResponse<String> fromCache = api.get("/b2Expired", null);

    assertThat(fromDb.statusCode()).as("first request (DB)").isEqualTo(410);
    assertThat(fromCache.statusCode()).as("second request (cache hit)").isEqualTo(410);
    assertThat(json(fromCache).path("error").path("code").asText()).isEqualTo("LINK_EXPIRED");
  }
}
