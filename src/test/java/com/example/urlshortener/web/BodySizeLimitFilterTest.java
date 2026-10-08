package com.example.urlshortener.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.probe.ApiSliceTest;
import com.example.urlshortener.service.ApiKeyService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ApiSliceTest
class BodySizeLimitFilterTest {

  private final MockMvc mvc;

  @MockBean private ApiKeyService apiKeys;

  @Autowired
  BodySizeLimitFilterTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  /** A JSON body of exactly {@code size} bytes. */
  private static byte[] jsonOfSize(int size) {
    String prefix = "{\"url\":\"";
    String suffix = "\"}";
    String body = prefix + "a".repeat(size - prefix.length() - suffix.length()) + suffix;
    return body.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void nfr4_5_over8kb413() throws Exception {
    mvc.perform(
            post("/probe/echo").contentType(MediaType.APPLICATION_JSON).content(jsonOfSize(8193)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"))
        .andExpect(jsonPath("$.error.details.max_bytes").value(8192));
  }

  @Test
  void nfr4_5_exactly8kbIsAccepted() throws Exception {
    mvc.perform(
            post("/probe/echo").contentType(MediaType.APPLICATION_JSON).content(jsonOfSize(8192)))
        .andExpect(status().isOk());
  }

  @Test
  void nfr4_5_postWithoutContentLength411() throws Exception {
    mvc.perform(post("/probe/echo").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isLengthRequired())
        .andExpect(jsonPath("$.error.code").value("LENGTH_REQUIRED"));
  }

  @Test
  void nfr4_5_oversizeIsRejectedBeforeAuthentication() throws Exception {
    mvc.perform(
            post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(jsonOfSize(9000)))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void getWithoutBodyIsNotAffected() throws Exception {
    mvc.perform(get("/probe/public")).andExpect(status().isOk());
  }
}
