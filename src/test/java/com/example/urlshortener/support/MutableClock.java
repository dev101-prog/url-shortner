package com.example.urlshortener.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A UTC clock tests can move forward (design §8.5: time comes from an injected Clock). */
public final class MutableClock extends Clock {

  private final AtomicReference<Instant> now;

  /**
   * Creates the clock.
   *
   * @param start initial instant
   */
  public MutableClock(Instant start) {
    this.now = new AtomicReference<>(start);
  }

  /**
   * Moves time forward.
   *
   * @param duration amount
   */
  public void advance(Duration duration) {
    now.updateAndGet(i -> i.plus(duration));
  }

  /**
   * Sets the time.
   *
   * @param instant new instant
   */
  public void set(Instant instant) {
    now.set(instant);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("UTC only");
  }

  @Override
  public Instant instant() {
    return now.get();
  }
}
