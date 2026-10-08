package com.example.urlshortener.config;

import com.example.urlshortener.infra.ClickBuffer;
import com.example.urlshortener.infra.ClickFlushWorker;
import com.example.urlshortener.repository.ClickEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the click pipeline (design §3.6) from {@code app.analytics.*}. Infra classes receive plain
 * values, so they never depend on the config package (design §8.4 rule 4).
 */
@Configuration(proxyBeanMethods = false)
public class ClickPipelineConfig {

  /**
   * Bounded click buffer.
   *
   * @param props application properties
   * @param meters meter registry
   * @return buffer
   */
  @Bean
  public ClickBuffer clickBuffer(AppProperties props, MeterRegistry meters) {
    return new ClickBuffer(props.analytics().bufferCapacity(), meters);
  }

  /**
   * Scheduled flush worker with shutdown drain.
   *
   * @param buffer click buffer
   * @param repository click event repository
   * @param props application properties
   * @param clock application clock
   * @param meters meter registry
   * @return worker
   */
  @Bean
  public ClickFlushWorker clickFlushWorker(
      ClickBuffer buffer,
      ClickEventRepository repository,
      AppProperties props,
      Clock clock,
      MeterRegistry meters) {
    AppProperties.Analytics analytics = props.analytics();
    return new ClickFlushWorker(
        buffer,
        repository,
        clock,
        meters,
        analytics.flushBatchSize(),
        analytics.flushRetryBackoff(),
        analytics.shutdownDrainTimeout());
  }
}
