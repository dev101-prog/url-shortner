package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.urlshortener.infra.RandomBase62CodeGenerator;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.LinkService;
import com.example.urlshortener.service.LinkValidator;
import com.example.urlshortener.service.UrlNormalizer;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.domain.CachedLink;
import com.example.urlshortener.service.domain.CreateResult;
import com.example.urlshortener.service.domain.EffectiveStatus;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.domain.LinkStatus;
import com.example.urlshortener.service.error.ApiException;
import com.example.urlshortener.service.error.ErrorCode;
import com.example.urlshortener.service.port.CodeGenerator;
import com.example.urlshortener.service.port.LinkCache;
import com.example.urlshortener.support.MutableClock;
import com.example.urlshortener.support.TestProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class LinkServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-07T21:00:00Z");
  private static final AuthenticatedOwner ALICE = new AuthenticatedOwner(1L, 11L);
  private static final AuthenticatedOwner BOB = new AuthenticatedOwner(2L, 22L);
  private static final String URL = "HTTPS://Example.org:443/a?x=1";
  private static final String NORMALIZED = "https://example.org/a?x=1";

  private final LinkRepository repo = mock(LinkRepository.class);
  private final LinkCache cache = mock(LinkCache.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final MutableClock clock = new MutableClock(NOW);
  private final LinkService service =
      new LinkService(
          repo,
          new LinkValidator(TestProperties.defaults()),
          new UrlNormalizer(),
          cache,
          TestProperties.defaults(),
          clock,
          new RandomBase62CodeGenerator(new SecureRandom(), 7),
          meters);

  private static Link link(String code, long ownerId, boolean alias, Instant expiresAt) {
    return new Link(
        42L, code, ownerId, URL, NORMALIZED, alias, LinkStatus.ACTIVE, NOW, expiresAt, null, 3L);
  }

  /** B3: a stub {@link CodeGenerator} returning fixed codes in order. */
  private LinkService withCodes(String... codes) {
    Iterator<String> next = List.of(codes).iterator();
    CodeGenerator stub = next::next;
    return new LinkService(
        repo,
        new LinkValidator(TestProperties.defaults()),
        new UrlNormalizer(),
        cache,
        TestProperties.defaults(),
        clock,
        stub,
        meters);
  }

  private double counter(String name) {
    return meters.counter(name).count();
  }

  @Test
  void fr1_1_createsRandomBase62CodeWithNormalizedUrlAndWarmsCache() {
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    when(repo.insertIfCodeFree(code.capture(), eq(1L), eq(URL), eq(NORMALIZED), eq(false), any()))
        .thenAnswer(inv -> Optional.of(link(inv.getArgument(0), 1L, false, null)));

    CreateResult result = service.create(ALICE, URL, null, null, false);

    assertThat(result).isInstanceOf(CreateResult.Created.class);
    assertThat(code.getValue()).matches("^[A-Za-z0-9]{7}$");
    InOrder order = inOrder(cache);
    order.verify(cache).clearMissing(code.getValue());
    order.verify(cache).put(code.getValue(), new CachedLink(42L, URL, LinkStatus.ACTIVE, null));
  }

  @Test
  void fr1_2_retriesOnCollisionThenSucceeds() {
    LinkService stubbed = withCodes("Coll001", "Coll002", "Free003");
    when(repo.insertIfCodeFree(eq("Free003"), anyLong(), any(), any(), eq(false), any()))
        .thenAnswer(inv -> Optional.of(link("Free003", 1L, false, null)));
    when(repo.insertIfCodeFree(eq("Coll001"), anyLong(), any(), any(), eq(false), any()))
        .thenReturn(Optional.empty());
    when(repo.insertIfCodeFree(eq("Coll002"), anyLong(), any(), any(), eq(false), any()))
        .thenReturn(Optional.empty());

    CreateResult result = stubbed.create(ALICE, URL, null, null, false);

    assertThat(result.link().code()).isEqualTo("Free003");
    verify(repo, times(3)).insertIfCodeFree(anyString(), anyLong(), any(), any(), eq(false), any());
    assertThat(counter("urlshortener.codegen.retries")).isEqualTo(2.0);
    assertThat(counter("urlshortener.codegen.exhausted")).isZero();
  }

  @Test
  void fr1_2_exhaustsAfter5() {
    LinkService stubbed =
        withCodes("Coll001", "Coll002", "Coll003", "Coll004", "Coll005", "Never06");
    when(repo.insertIfCodeFree(anyString(), anyLong(), any(), any(), anyBoolean(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> stubbed.create(ALICE, URL, null, null, false))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.CODE_GENERATION_EXHAUSTED);
              assertThat(e.code().httpStatus()).isEqualTo(503);
            });
    verify(repo, times(5)).insertIfCodeFree(anyString(), anyLong(), any(), any(), eq(false), any());
    verify(repo, never())
        .insertIfCodeFree(eq("Never06"), anyLong(), any(), any(), anyBoolean(), any());
    assertThat(counter("urlshortener.codegen.exhausted")).isEqualTo(1.0);
    verifyNoInteractions(cache);
  }

  @Test
  void fr2_1_aliasBecomesTheCode() {
    when(repo.insertIfCodeFree(eq("my-promo"), eq(1L), eq(URL), eq(NORMALIZED), eq(true), any()))
        .thenReturn(Optional.of(link("my-promo", 1L, true, null)));

    CreateResult result = service.create(ALICE, URL, "my-promo", null, false);

    assertThat(result.link().code()).isEqualTo("my-promo");
    assertThat(result.link().customAlias()).isTrue();
  }

  @Test
  void fr2_3_aliasConflictIs409WithDetails() {
    when(repo.insertIfCodeFree(eq("spring-docs"), anyLong(), any(), any(), eq(true), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(BOB, URL, "spring-docs", null, false))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.ALIAS_CONFLICT);
              assertThat(e.getMessage()).isEqualTo("Alias 'spring-docs' is already in use.");
              assertThat(e.details()).containsEntry("alias", "spring-docs");
            });
    verifyNoInteractions(cache);
  }

  @Test
  void fr1_4_dedupeReturnsExistingWithoutInsert() {
    Instant expiry = NOW.plus(Duration.ofDays(3));
    Link existing = link("aB3dE7x", 1L, false, expiry);
    when(repo.findDedupeCandidate(1L, NORMALIZED, expiry, NOW)).thenReturn(Optional.of(existing));

    CreateResult result = service.create(ALICE, URL, null, expiry, true);

    assertThat(result).isEqualTo(new CreateResult.Existing(existing));
    verify(repo, never()).insertIfCodeFree(any(), anyLong(), any(), any(), anyBoolean(), any());
  }

  @Test
  void fr1_4_dedupeMissInsertsNewLink() {
    when(repo.findDedupeCandidate(anyLong(), any(), any(), any())).thenReturn(Optional.empty());
    when(repo.insertIfCodeFree(anyString(), anyLong(), any(), any(), eq(false), any()))
        .thenAnswer(inv -> Optional.of(link(inv.getArgument(0), 1L, false, null)));

    assertThat(service.create(ALICE, URL, null, null, true))
        .isInstanceOf(CreateResult.Created.class);
  }

  @Test
  void fr1_4_dedupeIsIgnoredWhenAliasGiven() {
    when(repo.insertIfCodeFree(eq("my-promo"), anyLong(), any(), any(), eq(true), any()))
        .thenReturn(Optional.of(link("my-promo", 1L, true, null)));

    service.create(ALICE, URL, "my-promo", null, true);

    verify(repo, never()).findDedupeCandidate(anyLong(), any(), any(), any());
  }

  @Test
  void validationRunsBeforeAnyPersistence() {
    assertThatThrownBy(() -> service.create(ALICE, "javascript:alert(1)", null, null, false))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_URL));
    assertThatThrownBy(() -> service.create(ALICE, URL, "Docs", null, false))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RESERVED_ALIAS));
    assertThatThrownBy(() -> service.create(ALICE, URL, null, NOW, false))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_EXPIRY));
    verifyNoInteractions(repo, cache);
  }

  @Test
  void fr5_2_getIsOwnerOnly() {
    when(repo.findByCode("aB3dE7x")).thenReturn(Optional.of(link("aB3dE7x", 1L, false, null)));

    assertThat(service.get(ALICE, "aB3dE7x").code()).isEqualTo("aB3dE7x");
    assertThatThrownBy(() -> service.get(BOB, "aB3dE7x"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    assertThatThrownBy(() -> service.get(ALICE, "unknown1"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  void fr4_3_statusIsDerivedFromTheInjectedClock() {
    Link link = link("aB3dE7x", 1L, false, NOW.plusSeconds(60));

    assertThat(service.statusOf(link)).isEqualTo(EffectiveStatus.ACTIVE);
    clock.advance(Duration.ofSeconds(60));
    assertThat(service.statusOf(link)).isEqualTo(EffectiveStatus.EXPIRED);
  }

  @Test
  void fr6_1_deactivateUpdatesWithClockAndEvicts() {
    when(repo.findByCode("aB3dE7x")).thenReturn(Optional.of(link("aB3dE7x", 1L, false, null)));
    when(repo.deactivate("aB3dE7x", 1L, NOW)).thenReturn(1, 0);

    service.deactivate(ALICE, "aB3dE7x");
    service.deactivate(ALICE, "aB3dE7x");

    verify(repo, times(2)).deactivate("aB3dE7x", 1L, NOW);
    verify(cache, times(2)).evict("aB3dE7x");
  }

  @Test
  void fr6_2_deactivateByNonOwnerIs404AndChangesNothing() {
    when(repo.findByCode("aB3dE7x")).thenReturn(Optional.of(link("aB3dE7x", 1L, false, null)));

    assertThatThrownBy(() -> service.deactivate(BOB, "aB3dE7x"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    verify(repo, never()).deactivate(any(), anyLong(), any());
    verifyNoInteractions(cache);
  }
}
