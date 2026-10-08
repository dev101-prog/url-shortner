package com.example.urlshortener.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.probe.ApiSliceTest;
import com.example.urlshortener.api.error.EnvelopeErrorController;
import com.example.urlshortener.service.ApiKeyService;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@ApiSliceTest
@Import(EnvelopeErrorController.class)
class EnvelopeErrorControllerTest {

  private final MockMvc mvc;

  @MockBean private ApiKeyService apiKeys;

  @Autowired
  EnvelopeErrorControllerTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "400, MALFORMED_REQUEST",
    "401, UNAUTHORIZED",
    "404, NOT_FOUND",
    "405, MALFORMED_REQUEST",
    "411, LENGTH_REQUIRED",
    "413, PAYLOAD_TOO_LARGE",
    "418, MALFORMED_REQUEST",
    "422, VALIDATION_FAILED",
    "429, RATE_LIMITED",
    "500, INTERNAL_ERROR",
    "503, NOT_READY"
  })
  void nfr4_6_containerErrorsGetTheEnvelope(int httpStatus, String code) throws Exception {
    mvc.perform(
            get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, httpStatus)
                .requestAttr(
                    RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("secret")))
        .andExpect(status().is(httpStatus))
        .andExpect(jsonPath("$.error.code").value(code))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }

  @Test
  void everyMethodIsHandled() throws Exception {
    for (var builder :
        java.util.List.of(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/error"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/error"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/error"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/error"))) {
      // a body avoids the 411 from BodySizeLimitFilter, which MockMvc (unlike a real error
      // dispatch) runs
      mvc.perform(builder.content("{}").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 413))
          .andExpect(status().isPayloadTooLarge())
          .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
    }
  }

  @Test
  void directRequestIs404AndNonErrorStatusBecomes500() throws Exception {
    mvc.perform(get("/error"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 200))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 999))
        .andExpect(status().isInternalServerError());
  }
}
