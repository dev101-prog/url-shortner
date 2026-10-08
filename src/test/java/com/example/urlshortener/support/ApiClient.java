package com.example.urlshortener.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Minimal HTTP client for integration tests; never follows redirects. */
public final class ApiClient {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final HttpClient http = HttpClient.newHttpClient();
  private final String base;

  /**
   * Creates a client for the local server.
   *
   * @param port server port
   */
  public ApiClient(int port) {
    this.base = "http://localhost:" + port;
  }

  /**
   * POSTs JSON.
   *
   * @param path path
   * @param apiKey API key or {@code null}
   * @param json body
   * @return response
   */
  public HttpResponse<String> post(String path, String apiKey, String json) {
    return send(
        builder(path, apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)));
  }

  /**
   * GETs a path.
   *
   * @param path path
   * @param apiKey API key or {@code null}
   * @return response
   */
  public HttpResponse<String> get(String path, String apiKey) {
    return send(builder(path, apiKey).GET());
  }

  /**
   * GETs a path with extra headers.
   *
   * @param path path
   * @param headers alternating names and values
   * @return response
   */
  public HttpResponse<String> getWithHeaders(String path, String... headers) {
    HttpRequest.Builder b = builder(path, null).GET();
    if (headers.length > 0) {
      b.headers(headers);
    }
    return send(b);
  }

  /**
   * DELETEs a path.
   *
   * @param path path
   * @param apiKey API key or {@code null}
   * @return response
   */
  public HttpResponse<String> delete(String path, String apiKey) {
    return send(builder(path, apiKey).DELETE());
  }

  /**
   * Parses a JSON body.
   *
   * @param response response
   * @return JSON tree
   */
  public static JsonNode json(HttpResponse<String> response) {
    try {
      return JSON.readTree(response.body());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private HttpRequest.Builder builder(String path, String apiKey) {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
    if (apiKey != null) {
      b.header("X-API-Key", apiKey);
    }
    return b;
  }

  private HttpResponse<String> send(HttpRequest.Builder request) {
    try {
      return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
