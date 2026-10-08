package com.example.urlshortener.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.repository.ApiKeyRepository;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApiKeyRepositoryIT extends AbstractPostgresIT {

  @Test
  void nfr4_2_findsActiveKeysByHashOnly(
      @Autowired ApiKeyRepository repo, @Autowired JdbcClient jdbc) {
    TestData data = new TestData(jdbc);
    long owner = data.owner("carol");
    long activeId = data.apiKey(owner, "test-key-carol-active", false);
    data.apiKey(owner, "test-key-carol-revoked", true);

    assertThat(repo.findActiveByHash(TestData.sha256Hex("test-key-carol-active")))
        .contains(new AuthenticatedOwner(owner, activeId));
    assertThat(repo.findActiveByHash(TestData.sha256Hex("test-key-carol-revoked"))).isEmpty();
    assertThat(repo.findActiveByHash(TestData.sha256Hex("unknown"))).isEmpty();
    // the raw key itself is never a valid lookup value
    assertThat(repo.findActiveByHash("test-key-carol-active")).isEmpty();
  }
}
