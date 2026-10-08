package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.repository.ApiKeyRepository;
import com.example.urlshortener.service.ApiKeyService;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import com.example.urlshortener.support.TestProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ApiKeyServiceTest {

  /** SHA-256 of demo-key-alice-0001 (design §4.4 seed). */
  private static final String ALICE_HASH =
      "fa3eb4fb3f5e5d2e92f91162aa0b6e130435c0c8fde4a11f452db7575a7d37d3";

  private final ApiKeyRepository repository = mock(ApiKeyRepository.class);
  private final ApiKeyService service = new ApiKeyService(repository, TestProperties.defaults());

  @Test
  void nfr4_1_authenticatesBySha256HexOfTheRawKey() {
    AuthenticatedOwner alice = new AuthenticatedOwner(1L, 1L);
    when(repository.findActiveByHash(ALICE_HASH)).thenReturn(Optional.of(alice));

    assertThat(service.authenticate("demo-key-alice-0001")).contains(alice);
    verify(repository).findActiveByHash(ALICE_HASH);
  }

  @Test
  void nfr4_1_positiveResultsAreCached() {
    AuthenticatedOwner alice = new AuthenticatedOwner(1L, 1L);
    when(repository.findActiveByHash(ALICE_HASH)).thenReturn(Optional.of(alice));

    assertThat(service.authenticate("demo-key-alice-0001")).contains(alice);
    assertThat(service.authenticate("demo-key-alice-0001")).contains(alice);
    assertThat(service.authenticate("demo-key-alice-0001")).contains(alice);

    verify(repository, times(1)).findActiveByHash(ALICE_HASH);
  }

  @Test
  void nfr4_2_unknownOrRevokedKeysAreRejectedAndNeverCached() {
    when(repository.findActiveByHash(anyString())).thenReturn(Optional.empty());

    assertThat(service.authenticate("demo-key-revoked-0003")).isEmpty();
    assertThat(service.authenticate("demo-key-revoked-0003")).isEmpty();

    verify(repository, times(2)).findActiveByHash(anyString());
  }

  @Test
  void nfr4_1_keysAreCaseSensitive() {
    when(repository.findActiveByHash(ALICE_HASH))
        .thenReturn(Optional.of(new AuthenticatedOwner(1L, 1L)));
    when(repository.findActiveByHash(
            org.mockito.ArgumentMatchers.argThat(h -> !ALICE_HASH.equals(h))))
        .thenReturn(Optional.empty());

    assertThat(service.authenticate("DEMO-KEY-ALICE-0001")).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t"})
  void nfr4_1_missingKeyIsRejectedWithoutDbLookup(String rawKey) {
    assertThat(service.authenticate(rawKey)).isEmpty();
    verify(repository, never()).findActiveByHash(anyString());
  }
}
