package com.example.urlshortener.api.error;

import com.example.urlshortener.api.dto.ErrorResponse;
import com.example.urlshortener.service.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Writes the §5.1 error envelope directly to the servlet response. Used by filters, which run
 * outside the DispatcherServlet and therefore outside {@link GlobalExceptionHandler}.
 */
@Component
public final class ErrorEnvelopeWriter {

  private final ObjectWriter writer;

  /**
   * Creates the writer.
   *
   * @param objectMapper the application's JSON mapper
   */
  public ErrorEnvelopeWriter(ObjectMapper objectMapper) {
    this.writer = objectMapper.writerFor(ErrorResponse.class);
  }

  /**
   * Sends an error response (like {@code HttpServletResponse.sendError}, but with the envelope).
   *
   * @param response servlet response (must not be committed)
   * @param code error code; its HTTP status is used
   * @param message safe message
   * @param details optional details
   * @throws IOException if the response cannot be written
   */
  public void send(
      HttpServletResponse response, ErrorCode code, String message, Map<String, Object> details)
      throws IOException {
    response.setStatus(code.httpStatus());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    writer.writeValue(response.getOutputStream(), ErrorResponse.of(code.name(), message, details));
  }
}
