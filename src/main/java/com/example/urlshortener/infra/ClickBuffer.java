package com.example.urlshortener.infra;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.service.domain.ClickEvent;
import com.example.urlshortener.service.port.ClickSink;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.springframework.stereotype.Component;

/**
 * Bounded in-memory click queue (design §3.6, URL-FR-7.2): non-blocking {@code offer}, drained in
 * batches by {@code ClickFlushWorker}. Depth is exposed as {@code
 * urlshortener.clicks.buffer.depth}.
 */
@Component
public final class ClickBuffer implements ClickSink {

  private final BlockingQueue<ClickEvent> queue;

  /**
   * Creates the buffer.
   *
   * @param props application properties ({@code app.analytics.buffer-capacity})
   * @param meters meter registry
   */
  public ClickBuffer(AppProperties props, MeterRegistry meters) {
    this.queue = new ArrayBlockingQueue<>(props.analytics().bufferCapacity());
    Gauge.builder("urlshortener.clicks.buffer.depth", queue, Collection::size).register(meters);
  }

  /** URL-FR-7.2: never blocks; false when the buffer is full. */
  @Override
  public boolean offer(ClickEvent event) {
    return queue.offer(event);
  }

  /**
   * Moves up to {@code max} events into {@code into}.
   *
   * @param into destination
   * @param max maximum events
   * @return number of events moved
   */
  public int drainTo(Collection<ClickEvent> into, int max) {
    return queue.drainTo(into, max);
  }

  /**
   * Current depth.
   *
   * @return buffered events
   */
  public int size() {
    return queue.size();
  }
}
