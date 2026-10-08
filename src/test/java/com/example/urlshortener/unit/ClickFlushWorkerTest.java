package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.urlshortener.infra.ClickBuffer;
import com.example.urlshortener.infra.ClickFlushWorker;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.service.domain.ClickEvent;
import com.example.urlshortener.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessResourceFailureException;

class ClickFlushWorkerTest {

  private static final Instant NOW = Instant.parse("2026-10-07T21:00:00Z");

  private final ClickEventRepository repo = mock(ClickEventRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final MutableClock clock = new MutableClock(NOW);
  private final ClickBuffer buffer = new ClickBuffer(10_000, meters);
  private final ClickFlushWorker worker =
      new ClickFlushWorker(buffer, repo, clock, meters, 500, Duration.ZERO, Duration.ofSeconds(10));
  private final List<Integer> savedSizes = new ArrayList<>();

  private void fill(int n) {
    for (int i = 0; i < n; i++) {
      buffer.offer(new ClickEvent(1L, NOW, null, null, false, null, null));
    }
  }

  private void recordSaves() {
    doAnswer(
            inv -> {
              savedSizes.add(((List<?>) inv.getArgument(0)).size());
              return null;
            })
        .when(repo)
        .saveBatch(anyList());
  }

  private double dropped() {
    return meters.counter("urlshortener.clicks.dropped", "reason", "flush_failed").count();
  }

  @Test
  void fr7_2_flushWritesAtMostOneBatchOf500() {
    recordSaves();
    fill(700);

    assertThat(worker.flushOnce()).isEqualTo(500);

    assertThat(savedSizes).containsExactly(500);
    assertThat(buffer.size()).isEqualTo(200);
    assertThat(meters.counter("urlshortener.clicks.flushed").count()).isEqualTo(500.0);
    assertThat(meters.timer("urlshortener.clicks.flush.duration").count()).isEqualTo(1L);
  }

  @Test
  void fr7_2_emptyBufferDoesNotTouchTheDatabase() {
    worker.scheduledFlush();

    verify(repo, never()).saveBatch(anyList());
  }

  @Test
  void fr7_6_shutdownDrainsBuffer() {
    recordSaves();
    worker.start();
    fill(1200);

    worker.stop();

    assertThat(savedSizes).containsExactly(500, 500, 200);
    assertThat(buffer.size()).isZero();
    assertThat(worker.isRunning()).isFalse();
    assertThat(meters.counter("urlshortener.clicks.flushed").count()).isEqualTo(1200.0);
  }

  @Test
  void fr7_6_flushFailureDropsAndCounts() {
    doThrow(new DataAccessResourceFailureException("db down")).when(repo).saveBatch(anyList());
    fill(3);

    assertThat(worker.flushOnce()).isEqualTo(3);

    verify(repo, times(2)).saveBatch(anyList());
    assertThat(dropped()).isEqualTo(3.0);
    assertThat(meters.counter("urlshortener.clicks.flushed").count()).isZero();
    assertThat(buffer.size()).isZero();
  }

  @Test
  void fr7_6_retrySucceedsAfterOneFailure() {
    doThrow(new DataAccessResourceFailureException("blip"))
        .doNothing()
        .when(repo)
        .saveBatch(anyList());
    fill(2);

    worker.flushOnce();

    verify(repo, times(2)).saveBatch(anyList());
    assertThat(dropped()).isZero();
    assertThat(meters.counter("urlshortener.clicks.flushed").count()).isEqualTo(2.0);
  }

  @Test
  void fr7_6_interruptedRetryDropsAndKeepsInterruptFlag() {
    doThrow(new DataAccessResourceFailureException("db down")).when(repo).saveBatch(anyList());
    fill(1);
    ClickFlushWorker slowRetry =
        new ClickFlushWorker(
            buffer, repo, clock, meters, 500, Duration.ofMillis(200), Duration.ofSeconds(10));

    Thread.currentThread().interrupt();
    try {
      slowRetry.flushOnce();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
    assertThat(dropped()).isEqualTo(1.0);
    verify(repo, times(1)).saveBatch(anyList());
  }

  @Test
  void fr7_6_shutdownDrainStopsAtTheDeadline() {
    doAnswer(
            inv -> {
              clock.advance(Duration.ofSeconds(6));
              return null;
            })
        .when(repo)
        .saveBatch(anyList());
    fill(1500);

    worker.stop();

    // two batches (12 s simulated) exceed the 10 s bound; the rest stays in the buffer
    assertThat(buffer.size()).isEqualTo(500);
  }

  @Test
  void lifecycleStopsAfterTheWebServer() {
    doNothing().when(repo).saveBatch(anyList());
    assertThat(worker.isRunning()).isFalse();
    worker.start();
    assertThat(worker.isRunning()).isTrue();
    assertThat(worker.getPhase()).isLessThan(SmartLifecycle.DEFAULT_PHASE - 2048);
    assertThat(worker.isAutoStartup()).isTrue();
  }
}
