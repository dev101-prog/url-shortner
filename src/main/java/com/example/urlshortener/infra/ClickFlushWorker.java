package com.example.urlshortener.infra;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.service.domain.ClickEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moves clicks from the {@link ClickBuffer} to Postgres (design §3.6, flow §6.4). Every flush
 * interval it drains up to {@code flush-batch-size} events and writes them in one transaction. On
 * failure it retries once after {@code flush-retry-backoff}, then drops the batch and counts it. On
 * shutdown it drains the buffer until empty, bounded by {@code shutdown-drain-timeout}.
 */
@Component
public final class ClickFlushWorker implements SmartLifecycle {

  /**
   * Stops after the web server's graceful-shutdown phases (higher phases stop first), so requests
   * in flight have enqueued their clicks before the final drain (URL-OBS-3).
   */
  static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 4096;

  private static final Logger LOG = LoggerFactory.getLogger(ClickFlushWorker.class);

  private final ClickBuffer buffer;
  private final ClickEventRepository repository;
  private final Clock clock;
  private final int batchSize;
  private final Duration retryBackoff;
  private final Duration drainTimeout;
  private final Counter flushed;
  private final Counter droppedFlushFailed;
  private final Timer flushDuration;
  private final AtomicBoolean running = new AtomicBoolean();

  /**
   * Creates the worker.
   *
   * @param buffer click buffer
   * @param repository click event repository
   * @param props application properties
   * @param clock application clock
   * @param meters meter registry
   */
  public ClickFlushWorker(
      ClickBuffer buffer,
      ClickEventRepository repository,
      AppProperties props,
      Clock clock,
      MeterRegistry meters) {
    this.buffer = buffer;
    this.repository = repository;
    this.clock = clock;
    this.batchSize = props.analytics().flushBatchSize();
    this.retryBackoff = props.analytics().flushRetryBackoff();
    this.drainTimeout = props.analytics().shutdownDrainTimeout();
    this.flushed = meters.counter("urlshortener.clicks.flushed");
    this.droppedFlushFailed =
        Counter.builder("urlshortener.clicks.dropped")
            .tag("reason", "flush_failed")
            .register(meters);
    this.flushDuration = meters.timer("urlshortener.clicks.flush.duration");
  }

  /** URL-FR-7.2: periodic flush, off the request path. */
  @Scheduled(fixedDelayString = "${app.analytics.flush-interval:PT1S}")
  public void scheduledFlush() {
    flushOnce();
  }

  /**
   * Flushes one batch.
   *
   * @return number of events taken from the buffer (written or dropped)
   */
  public int flushOnce() {
    List<ClickEvent> batch = new ArrayList<>(batchSize);
    buffer.drainTo(batch, batchSize);
    if (batch.isEmpty()) {
      return 0;
    }
    Instant start = clock.instant();
    if (write(batch)) {
      flushed.increment(batch.size());
      flushDuration.record(Duration.between(start, clock.instant()));
    }
    return batch.size();
  }

  /** URL-FR-7.6: one retry, then drop and count; the redirect path is never affected. */
  private boolean write(List<ClickEvent> batch) {
    try {
      repository.saveBatch(batch);
      return true;
    } catch (RuntimeException first) {
      LOG.warn("Click flush failed for {} events; retrying once", batch.size(), first);
    }
    try {
      Thread.sleep(retryBackoff);
      repository.saveBatch(batch);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      drop(batch, e);
    } catch (RuntimeException second) {
      drop(batch, second);
    }
    return false;
  }

  private void drop(List<ClickEvent> batch, Exception cause) {
    droppedFlushFailed.increment(batch.size());
    LOG.warn("Dropped {} click events after a failed retry", batch.size(), cause);
  }

  @Override
  public void start() {
    running.set(true);
  }

  /** URL-FR-7.6 / URL-OBS-3: drain the buffer until empty (bounded) before the pool closes. */
  @Override
  public void stop() {
    Instant deadline = clock.instant().plus(drainTimeout);
    int drained = 0;
    while (buffer.size() > 0 && clock.instant().isBefore(deadline)) {
      drained += flushOnce();
    }
    if (buffer.size() > 0) {
      LOG.warn("Shutdown drain timed out; {} click events left in the buffer", buffer.size());
    }
    LOG.info("Click buffer drained on shutdown: {} events", drained);
    running.set(false);
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public int getPhase() {
    return PHASE;
  }
}
