package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.urlshortener.repository.DatabaseHealthRepository;
import com.example.urlshortener.service.ReadinessService;
import com.example.urlshortener.support.TestProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReadinessServiceTest {

  private final DatabaseHealthRepository db = mock(DatabaseHealthRepository.class);
  private final ReadinessService service = new ReadinessService(db, TestProperties.defaults());

  @Test
  void fr8_2_readyOnlyWhenTheDatabaseAnswersWithinOneSecond() {
    when(db.isReachable(Duration.ofSeconds(1))).thenReturn(true, false);

    assertThat(service.isDatabaseUp()).isTrue();
    assertThat(service.isDatabaseUp()).isFalse();
  }
}
