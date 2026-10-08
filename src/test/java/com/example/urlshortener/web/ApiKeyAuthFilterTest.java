package com.example.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.probe.ApiSliceTest;
import com.example.urlshortener.service.ApiKeyService;
import com.example.urlshortener.service.domain.AuthenticatedOwner;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@ApiSliceTest
class ApiKeyAuthFilterTest {

  private static final String VALID = "demo-key-alice-0001";
  private static final String REVOKED = "demo-key-revoked-0003";

  private final MockMvc mvc;

  @MockBean private ApiKeyService apiKeys;

  @Autowired
  ApiKeyAuthFilterTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  private static void assertUnauthorized(ResultActions result) throws Exception {
    result
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("A valid X-API-Key header is required."));
  }

  @Test
  void nfr4_1_missingKeyIs401() throws Exception {
    assertUnauthorized(mvc.perform(get("/api/v1/probe/owner")));
    verify(apiKeys, never()).authenticate(anyString());
  }

  @Test
  void nfr4_1_blankKeyIs401() throws Exception {
    assertUnauthorized(mvc.perform(get("/api/v1/probe/owner").header("X-API-Key", "   ")));
    verify(apiKeys, never()).authenticate(anyString());
  }

  @Test
  void nfr4_1_unknownOrRevokedKeyIs401() throws Exception {
    when(apiKeys.authenticate(anyString())).thenReturn(Optional.empty());

    assertUnauthorized(mvc.perform(get("/api/v1/probe/owner").header("X-API-Key", "nope")));
    assertUnauthorized(mvc.perform(get("/api/v1/probe/owner").header("X-API-Key", REVOKED)));
  }

  @Test
  void nfr4_1_apiRootAndUnknownApiPathsAreProtected() throws Exception {
    assertUnauthorized(mvc.perform(get("/api/v1")));
    assertUnauthorized(mvc.perform(get("/api/v1/links/aB3dE7x")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api;x=1/v1/probe/owner",
        "/api/v1;x=1/probe/owner",
        "/%61pi/v1/probe/owner",
        "/api/%761/probe/owner",
        "/api//v1/probe/owner",
        "/API/V1/probe/owner"
      })
  void nfr4_1_encodedOrDecoratedPathsCannotBypassAuth(String path) throws Exception {
    assertUnauthorized(mvc.perform(get(URI.create(path))));
    verify(apiKeys, never()).authenticate(anyString());
  }

  @Test
  void nfr4_1_validKeyReachesControllerWithOwner() throws Exception {
    when(apiKeys.authenticate(VALID)).thenReturn(Optional.of(new AuthenticatedOwner(1L, 7L)));

    mvc.perform(get("/api/v1/probe/owner").header("X-API-Key", VALID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owner_id").value(1))
        .andExpect(jsonPath("$.api_key_id").value(7));
  }

  @Test
  void nfr4_1_publicRoutesNeedNoKey() throws Exception {
    mvc.perform(get("/probe/public")).andExpect(status().isOk());
    verify(apiKeys, never()).authenticate(anyString());
  }

  @Test
  void nfr4_1_rawKeyIsNeverLogged() throws Exception {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    Level previous = root.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    root.addAppender(appender);
    root.setLevel(Level.TRACE);
    try {
      when(apiKeys.authenticate(VALID)).thenReturn(Optional.of(new AuthenticatedOwner(1L, 7L)));
      when(apiKeys.authenticate(REVOKED)).thenReturn(Optional.empty());

      mvc.perform(get("/api/v1/probe/owner").header("X-API-Key", VALID));
      mvc.perform(get("/api/v1/probe/owner").header("X-API-Key", REVOKED));
      mvc.perform(get("/api/v1/probe/owner"));
    } finally {
      root.setLevel(previous);
      root.detachAppender(appender);
    }

    assertThat(appender.list).isNotEmpty();
    assertThat(appender.list)
        .allSatisfy(
            e -> {
              assertThat(e.getFormattedMessage()).doesNotContain(VALID).doesNotContain(REVOKED);
              assertThat(String.valueOf(e.getMDCPropertyMap()))
                  .doesNotContain(VALID)
                  .doesNotContain(REVOKED);
            });
    assertThat(appender.list)
        .anySatisfy(
            e ->
                assertThat(e.getFormattedMessage())
                    .isEqualTo("API request rejected: unknown or revoked API key"));
  }
}
