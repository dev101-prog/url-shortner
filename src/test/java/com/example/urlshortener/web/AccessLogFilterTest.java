package com.example.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.probe.ApiSliceTest;
import com.example.urlshortener.api.filter.AccessLogFilter;
import com.example.urlshortener.service.ApiKeyService;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@ApiSliceTest
class AccessLogFilterTest {

  private static final String KEY = "demo-key-alice-0001";

  private final MockMvc mvc;
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);

  @MockBean private ApiKeyService apiKeys;

  @Autowired
  AccessLogFilterTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  @BeforeEach
  void capture() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void release() {
    logger.detachAppender(appender);
  }

  private Map<String, String> onlyEvent() {
    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getFormattedMessage()).isEqualTo("request completed");
    return event.getMDCPropertyMap();
  }

  @Test
  void obs1_logsRouteTemplateStatusLatencyOwnerAndRequestId() throws Exception {
    when(apiKeys.authenticate(KEY)).thenReturn(Optional.of(new AuthenticatedOwner(7L, 9L)));

    mvc.perform(
        get("/api/v1/probe/owner").header("X-API-Key", KEY).header("X-Request-Id", "req-1"));

    Map<String, String> mdc = onlyEvent();
    assertThat(mdc)
        .containsEntry("route", "/api/v1/probe/owner")
        .containsEntry("method", "GET")
        .containsEntry("status", "200")
        .containsEntry("owner_id", "7")
        .containsEntry("request_id", "req-1")
        .containsKey("latency_ms");
    assertThat(Long.parseLong(mdc.get("latency_ms"))).isNotNegative();
    assertThat(MDC.get("route")).as("MDC cleaned up").isNull();
  }

  @Test
  void obs1_logsCodeFromTheRouteTemplateButNeverTheRawPath() throws Exception {
    mvc.perform(get("/probe/code/aB3dE7x"));

    assertThat(onlyEvent())
        .containsEntry("route", "/probe/code/{code}")
        .containsEntry("code", "aB3dE7x");
  }

  @Test
  void obs1_unsafeCodeValuesAreNotLogged() throws Exception {
    mvc.perform(get("/probe/code/{code}", "bad code\r\nX"));

    assertThat(onlyEvent()).doesNotContainKey("code");
  }

  @Test
  void obs1_rejectedRequestsAreLoggedWithoutKeysOrIps() throws Exception {
    when(apiKeys.authenticate(anyString())).thenReturn(Optional.empty());

    mvc.perform(
        get("/api/v1/secret/path")
            .header("X-API-Key", "raw-secret-key-value")
            .with(
                r -> {
                  r.setRemoteAddr("203.0.113.10");
                  return r;
                }));

    Map<String, String> mdc = onlyEvent();
    assertThat(mdc).containsEntry("status", "401").containsEntry("route", "UNMATCHED");
    assertThat(mdc.toString())
        .doesNotContain("raw-secret-key-value")
        .doesNotContain("203.0.113.10")
        .doesNotContain("/api/v1/secret/path");
  }
}
