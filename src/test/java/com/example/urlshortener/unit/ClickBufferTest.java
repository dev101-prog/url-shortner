package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.infra.ClickBuffer;
import com.example.urlshortener.service.domain.ClickEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClickBufferTest {

  private static ClickEvent click(long linkId) {
    return new ClickEvent(
        linkId, Instant.parse("2026-10-07T21:00:00Z"), null, null, false, null, null);
  }

  @Test
  void fr7_2_offerNeverBlocksAndRejectsWhenFull() {
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    ClickBuffer buffer = new ClickBuffer(2, meters);

    assertThat(buffer.offer(click(1))).isTrue();
    assertThat(buffer.offer(click(2))).isTrue();
    assertThat(buffer.offer(click(3))).isFalse();
    assertThat(buffer.size()).isEqualTo(2);
    assertThat(meters.get("urlshortener.clicks.buffer.depth").gauge().value()).isEqualTo(2.0);
  }

  @Test
  void fr7_2_drainsInFifoOrderUpToMax() {
    ClickBuffer buffer = new ClickBuffer(10, new SimpleMeterRegistry());
    for (long i = 1; i <= 5; i++) {
      buffer.offer(click(i));
    }
    List<ClickEvent> batch = new ArrayList<>();

    assertThat(buffer.drainTo(batch, 3)).isEqualTo(3);
    assertThat(batch).extracting(ClickEvent::linkId).containsExactly(1L, 2L, 3L);
    assertThat(buffer.size()).isEqualTo(2);
  }
}
