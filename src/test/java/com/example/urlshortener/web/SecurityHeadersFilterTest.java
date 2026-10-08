package com.example.urlshortener.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.probe.ApiSliceTest;
import com.example.urlshortener.service.ApiKeyService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@ApiSliceTest
class SecurityHeadersFilterTest {

  private final MockMvc mvc;

  @MockBean private ApiKeyService apiKeys;

  @Autowired
  SecurityHeadersFilterTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"/probe/public, 200", "/no/such/route, 404", "/api/v1/probe/owner, 401"})
  void nfr4_6_securityHeadersOnSuccessAndErrors(String path, int expectedStatus) throws Exception {
    mvc.perform(get(path))
        .andExpect(status().is(expectedStatus))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"));
  }
}
