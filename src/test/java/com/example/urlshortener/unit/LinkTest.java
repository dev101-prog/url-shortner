package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.example.urlshortener.service.domain.EffectiveStatus;
import com.example.urlshortener.service.domain.Link;
import com.example.urlshortener.service.domain.LinkStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LinkTest {

  private static final Instant CREATED = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant EXPIRES = Instant.parse("2026-10-07T21:00:00Z");

  private static Link link(LinkStatus status, Instant expiresAt) {
    Instant deactivated = status == LinkStatus.INACTIVE ? CREATED.plusSeconds(60) : null;
    return new Link(
        1L,
        "aB3dE7x",
        1L,
        "https://example.org/x",
        "https://example.org/x",
        false,
        status,
        CREATED,
        expiresAt,
        deactivated,
        0L);
  }

  @Test
  void fr4_3_expiryBoundaryIsExclusive() {
    Link link = link(LinkStatus.ACTIVE, EXPIRES);

    assertThat(link.effectiveStatus(EXPIRES.minusNanos(1))).isEqualTo(EffectiveStatus.ACTIVE);
    assertThat(link.effectiveStatus(EXPIRES)).isEqualTo(EffectiveStatus.EXPIRED);
    assertThat(link.effectiveStatus(EXPIRES.plusSeconds(1))).isEqualTo(EffectiveStatus.EXPIRED);
  }

  @Test
  void fr4_1_noExpiryMeansNeverExpires() {
    assertThat(link(LinkStatus.ACTIVE, null).effectiveStatus(Instant.parse("2099-01-01T00:00:00Z")))
        .isEqualTo(EffectiveStatus.ACTIVE);
  }

  @Test
  void fr4_3_inactiveWinsOverExpired() {
    Link link = link(LinkStatus.INACTIVE, EXPIRES);

    assertThat(link.effectiveStatus(EXPIRES.minusSeconds(10))).isEqualTo(EffectiveStatus.INACTIVE);
    assertThat(link.effectiveStatus(EXPIRES.plusSeconds(10))).isEqualTo(EffectiveStatus.INACTIVE);
  }

  @Test
  void requiredFieldsAreEnforced() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new Link(1L, null, 1L, "u", "u", false, LinkStatus.ACTIVE, CREATED, null, null, 0L))
        .withMessage("code");
    assertThatNullPointerException()
        .isThrownBy(() -> new Link(1L, "abcd", 1L, "u", "u", false, null, CREATED, null, null, 0L))
        .withMessage("status");
  }
}
