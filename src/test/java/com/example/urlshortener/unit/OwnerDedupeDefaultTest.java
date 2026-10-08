package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.LinkService;
import com.example.urlshortener.service.LinkValidator;
import com.example.urlshortener.service.UrlNormalizer;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.service.domain.CreateResult;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.domain.LinkStatus;
import com.example.urlshortener.service.port.LinkCache;
import com.example.urlshortener.support.MutableClock;
import com.example.urlshortener.support.TestProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Scenario B1 (design §11.3): request {true, false, null} x owner default {true, false}. */
class OwnerDedupeDefaultTest {

  private static final Instant NOW = Instant.parse("2026-10-07T21:00:00Z");
  private static final AuthenticatedOwner OWNER = new AuthenticatedOwner(1L, 11L);
  private static final String URL = "https://example.org/a";
  private static final Link EXISTING =
      new Link(9L, "aB3dE7x", 1L, URL, URL, false, LinkStatus.ACTIVE, NOW, null, null, 0L);

  private final LinkRepository repo = mock(LinkRepository.class);
  private final LinkService service =
      new LinkService(
          repo,
          new LinkValidator(TestProperties.defaults()),
          new UrlNormalizer(),
          mock(LinkCache.class),
          TestProperties.defaults(),
          new MutableClock(NOW),
          new SecureRandom(),
          new SimpleMeterRegistry());

  @ParameterizedTest(name = "request={0}, owner default={1} -> dedupe={2}")
  @CsvSource(
      nullValues = "NULL",
      value = {
        "true,  true,  true",
        "true,  false, true",
        "false, true,  false",
        "false, false, false",
        "NULL,  true,  true",
        "NULL,  false, false"
      })
  void fr1_4_b1_requestFlagOverridesOwnerDefault(
      Boolean requested, boolean ownerDefault, boolean expectDedupe) {
    when(repo.ownerDedupeDefault(1L)).thenReturn(ownerDefault);
    when(repo.findDedupeCandidate(1L, URL, null, NOW)).thenReturn(Optional.of(EXISTING));
    when(repo.insertIfCodeFree(anyString(), anyLong(), any(), any(), anyBoolean(), any()))
        .thenAnswer(
            inv ->
                Optional.of(
                    new Link(
                        10L,
                        inv.getArgument(0),
                        1L,
                        URL,
                        URL,
                        false,
                        LinkStatus.ACTIVE,
                        NOW,
                        null,
                        null,
                        0L)));

    CreateResult result = service.create(OWNER, URL, null, null, requested);

    assertThat(result instanceof CreateResult.Existing).isEqualTo(expectDedupe);
    if (requested != null) {
      verify(repo, never()).ownerDedupeDefault(anyLong());
    }
  }

  @ParameterizedTest(name = "alias with request={0}, owner default=true never dedupes")
  @CsvSource(
      nullValues = "NULL",
      value = {"true", "NULL"})
  void fr1_4_b1_aliasIgnoresDedupeEvenWithOwnerDefault(Boolean requested) {
    when(repo.ownerDedupeDefault(1L)).thenReturn(true);
    when(repo.insertIfCodeFree(anyString(), anyLong(), any(), any(), anyBoolean(), any()))
        .thenReturn(
            Optional.of(
                new Link(
                    10L, "my-promo", 1L, URL, URL, true, LinkStatus.ACTIVE, NOW, null, null, 0L)));

    assertThat(service.create(OWNER, URL, "my-promo", null, requested))
        .isInstanceOf(CreateResult.Created.class);
    verify(repo, never()).findDedupeCandidate(anyLong(), any(), any(), any());
    verify(repo, never()).ownerDedupeDefault(anyLong());
  }
}
