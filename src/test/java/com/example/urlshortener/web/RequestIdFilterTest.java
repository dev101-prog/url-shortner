package com.example.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.probe.ApiSliceTest;
import com.example.urlshortener.service.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ApiSliceTest
class RequestIdFilterTest {

  private static final String UUID_REGEX =
      "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

  private final MockMvc mvc;

  @MockBean private ApiKeyService apiKeys;

  @Autowired
  RequestIdFilterTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  @Test
  void obs1_echoesIncomingIdAndExposesItInMdc() throws Exception {
    mvc.perform(get("/probe/public").header("X-Request-Id", "req-123_abc.9"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "req-123_abc.9"))
        .andExpect(jsonPath("$.request_id").value("req-123_abc.9"));

    assertThat(MDC.get("request_id")).isNull();
  }

  @Test
  void obs1_generatesUuidWhenMissing() throws Exception {
    MvcResult result = mvc.perform(get("/probe/public")).andExpect(status().isOk()).andReturn();

    String id = result.getResponse().getHeader("X-Request-Id");
    assertThat(id).matches(UUID_REGEX);
    assertThat(result.getResponse().getContentAsString()).contains(id);
  }

  @ParameterizedTest
  @ValueSource(strings = {"has space", "inject\r\nX-Evil: 1", "<script>", ""})
  void obs1_replacesUnsafeIncomingIds(String unsafe) throws Exception {
    MvcResult result = mvc.perform(get("/probe/public").header("X-Request-Id", unsafe)).andReturn();

    assertThat(result.getResponse().getHeader("X-Request-Id")).matches(UUID_REGEX);
  }

  @Test
  void obs1_replacesOverlongIds() throws Exception {
    MvcResult result =
        mvc.perform(get("/probe/public").header("X-Request-Id", "a".repeat(65))).andReturn();

    assertThat(result.getResponse().getHeader("X-Request-Id")).matches(UUID_REGEX);
  }

  @Test
  void obs1_errorResponsesCarryRequestIdToo() throws Exception {
    mvc.perform(get("/api/v1/probe/owner"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesRegex(UUID_REGEX)));
  }
}
