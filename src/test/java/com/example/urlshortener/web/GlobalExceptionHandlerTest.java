package com.example.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.probe.ApiSliceTest;
import com.example.urlshortener.service.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ApiSliceTest
class GlobalExceptionHandlerTest {

  private final MockMvc mvc;

  @MockBean private ApiKeyService apiKeys;

  @Autowired
  GlobalExceptionHandlerTest(MockMvc mvc) {
    this.mvc = mvc;
  }

  @Test
  void apiExceptionBecomesEnvelopeWithItsStatus() throws Exception {
    mvc.perform(get("/probe/api-error"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ALIAS_CONFLICT"))
        .andExpect(jsonPath("$.error.message").value("Alias 'spring-docs' is already in use."))
        .andExpect(jsonPath("$.error.details.alias").value("spring-docs"));
  }

  @Test
  void malformedJson400() throws Exception {
    mvc.perform(post("/probe/echo").contentType(MediaType.APPLICATION_JSON).content("{\"url\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void emptyBody400() throws Exception {
    mvc.perform(post("/probe/echo").contentType(MediaType.APPLICATION_JSON).content(""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void nfr4_5_unknownField422() throws Exception {
    mvc.perform(
            post("/probe/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://a.io\",\"admin\":true}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.details.field").value("admin"));
  }

  @Test
  void wrongFieldType422() throws Exception {
    mvc.perform(
            post("/probe/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://a.io\",\"count\":\"many\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.details.field").value("count"));
  }

  @Test
  void beanValidationFailure422() throws Exception {
    mvc.perform(
            post("/probe/echo").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\" \"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.details.fields[0]").value("url"));
  }

  @Test
  void badQueryParameter422() throws Exception {
    mvc.perform(get("/probe/param").param("n", "abc"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.details.parameter").value("n"));
    mvc.perform(get("/probe/param"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.details.parameter").value("n"));
  }

  @Test
  void unknownRoute404Envelope() throws Exception {
    mvc.perform(get("/no/such/route"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void wrongMethodAndMediaTypeKeepTheirStatus() throws Exception {
    mvc.perform(delete("/probe/public"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    mvc.perform(post("/probe/echo").contentType(MediaType.TEXT_PLAIN).content("hi"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void nfr4_6_unexpectedError500HidesInternals() throws Exception {
    String body =
        mvc.perform(get("/probe/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.error.message").value("Internal error."))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .doesNotContain("secret")
        .doesNotContain("10.0.0.5")
        .doesNotContain("IllegalStateException")
        .doesNotContain("at com.");
  }
}
