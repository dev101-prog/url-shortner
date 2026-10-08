package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.urlshortener.api.error.ErrorEnvelopeWriter;
import com.example.urlshortener.api.filter.ApiKeyAuthFilter;
import com.example.urlshortener.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ServletRequestPathUtils;

/** B4: the filter also honours a request path that Spring has already parsed and cached. */
class ApiKeyAuthFilterPathTest {

  private final ApiKeyAuthFilter filter =
      new ApiKeyAuthFilter(mock(ApiKeyService.class), new ErrorEnvelopeWriter(new ObjectMapper()));

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"/api/v1/links, 401", "/ctx-free/healthz, 200", "/api;x=1/v1/links, 401"})
  void nfr4_1_preParsedRequestPathIsProtectedTheSameWay(String uri, int expected) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    ServletRequestPathUtils.parseAndCache(request);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(expected);
  }
}
