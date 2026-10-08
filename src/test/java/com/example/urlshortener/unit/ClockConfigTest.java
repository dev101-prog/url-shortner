package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.config.ClockConfig;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

  @Test
  void clockIsUtcSystemClock() {
    Clock clock = new ClockConfig().clock();

    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    assertThat(clock).isEqualTo(Clock.systemUTC());
  }
}
