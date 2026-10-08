package com.example.urlshortener.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.domain.LinkStatus;
import com.example.urlshortener.support.AbstractPostgresIT;
import com.example.urlshortener.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LinkRepositoryIT extends AbstractPostgresIT {

  private static final String URL = "https://example.org/a?x=1";

  private final LinkRepository links;
  private final JdbcClient jdbc;
  private long alice;
  private long bob;

  @Autowired
  LinkRepositoryIT(LinkRepository links, JdbcClient jdbc) {
    this.links = links;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void owners() {
    jdbc.sql("TRUNCATE click_events, links, api_keys, owners RESTART IDENTITY").update();
    TestData data = new TestData(jdbc);
    alice = data.owner("alice");
    bob = data.owner("bob");
  }

  private static Instant dbNowPlus(Duration d) {
    return Instant.now().plus(d).truncatedTo(ChronoUnit.MICROS);
  }

  @Test
  void fr1_2_insertIfCodeFreeReturnsTheCommittedRowWithDbDefaults() {
    Instant expiry = dbNowPlus(Duration.ofDays(30));

    Optional<Link> inserted = links.insertIfCodeFree("aB3dE7x", alice, URL, URL, false, expiry);

    assertThat(inserted)
        .hasValueSatisfying(
            l -> {
              assertThat(l.id()).isPositive();
              assertThat(l.code()).isEqualTo("aB3dE7x");
              assertThat(l.ownerId()).isEqualTo(alice);
              assertThat(l.targetUrl()).isEqualTo(URL);
              assertThat(l.customAlias()).isFalse();
              assertThat(l.status()).isEqualTo(LinkStatus.ACTIVE);
              assertThat(l.createdAt()).isNotNull();
              assertThat(l.expiresAt()).isEqualTo(expiry);
              assertThat(l.deactivatedAt()).isNull();
              assertThat(l.clickCount()).isZero();
            });
    // URL-FR-1.6: visible to any other connection immediately (committed)
    assertThat(links.findByCode("aB3dE7x")).contains(inserted.orElseThrow());
  }

  @Test
  void fr2_3_insertReturnsEmptyWhenCodeExistsInAnyStatus() {
    assertThat(links.insertIfCodeFree("my-promo", alice, URL, URL, true, null)).isPresent();
    assertThat(links.insertIfCodeFree("my-promo", bob, URL, URL, true, null)).isEmpty();

    links.deactivate("my-promo", alice, Instant.now());

    assertThat(links.insertIfCodeFree("my-promo", alice, URL, URL, true, null)).isEmpty();
  }

  @Test
  void fr2_4_codesAreCaseSensitiveInOneNamespace() {
    assertThat(links.insertIfCodeFree("AbCdEfG", alice, URL, URL, false, null)).isPresent();
    assertThat(links.insertIfCodeFree("abcdefg", alice, URL, URL, true, null)).isPresent();

    assertThat(links.findByCode("AbCdEfG"))
        .hasValueSatisfying(l -> assertThat(l.customAlias()).isFalse());
    assertThat(links.findByCode("abcdefg"))
        .hasValueSatisfying(l -> assertThat(l.customAlias()).isTrue());
    assertThat(links.findByCode("ABCDEFG")).isEmpty();
  }

  @Test
  void findByCodeUnknownIsEmpty() {
    assertThat(links.findByCode("nope1234")).isEmpty();
  }

  @Test
  void fr1_4_dedupeCandidateMatchesSameOwnerUrlAndExpiry() {
    Instant expiry = dbNowPlus(Duration.ofDays(10));
    links.insertIfCodeFree("older01", alice, URL, URL, false, null);
    Link newer = links.insertIfCodeFree("newer01", alice, URL, URL, false, null).orElseThrow();
    Link withExpiry =
        links.insertIfCodeFree("expiry1", alice, URL, URL, false, expiry).orElseThrow();
    Instant now = Instant.now();

    assertThat(links.findDedupeCandidate(alice, URL, null, now)).contains(newer);
    assertThat(links.findDedupeCandidate(alice, URL, expiry, now)).contains(withExpiry);
    assertThat(links.findDedupeCandidate(alice, URL, expiry.plusSeconds(1), now)).isEmpty();
  }

  @Test
  void fr1_4_dedupeNeverMatchesOtherOwnersAliasesInactiveOrExpiredLinks() {
    Instant expiry = dbNowPlus(Duration.ofHours(1));
    links.insertIfCodeFree("bobs001", bob, URL, URL, false, null);
    links.insertIfCodeFree("alias-1", alice, URL, URL, true, null);
    links.insertIfCodeFree("gone001", alice, URL, URL, false, null);
    links.deactivate("gone001", alice, Instant.now());
    links.insertIfCodeFree("soon001", alice, URL, URL, false, expiry);

    assertThat(links.findDedupeCandidate(alice, URL, null, Instant.now())).isEmpty();
    assertThat(links.findDedupeCandidate(alice, URL, expiry, expiry)).isEmpty();
    assertThat(links.findDedupeCandidate(alice, URL, expiry, expiry.minusSeconds(1))).isPresent();
  }

  @Test
  void fr6_1_deactivateIsOwnerScopedAndIdempotent() {
    links.insertIfCodeFree("aB3dE7x", alice, URL, URL, false, null);
    Instant now = Instant.parse("2030-01-01T00:00:00Z");

    assertThat(links.deactivate("aB3dE7x", bob, now)).isZero();
    assertThat(links.findByCode("aB3dE7x").orElseThrow().status()).isEqualTo(LinkStatus.ACTIVE);

    assertThat(links.deactivate("aB3dE7x", alice, now)).isEqualTo(1);
    Link deactivated = links.findByCode("aB3dE7x").orElseThrow();
    assertThat(deactivated.status()).isEqualTo(LinkStatus.INACTIVE);
    assertThat(deactivated.deactivatedAt()).isEqualTo(now);

    assertThat(links.deactivate("aB3dE7x", alice, now.plusSeconds(5))).isZero();
    assertThat(links.findByCode("aB3dE7x").orElseThrow().deactivatedAt()).isEqualTo(now);
    assertThat(links.deactivate("unknown1", alice, now)).isZero();
  }
}
