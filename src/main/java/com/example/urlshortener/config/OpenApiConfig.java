package com.example.urlshortener.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document served at {@code /openapi.json} with Swagger UI at {@code /docs} (URL-DOC-1).
 * The server URL is the configured base URL so the generated document is stable and can be compared
 * with {@code openapi-baseline.json} (gate G7).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  /** Name of the {@code X-API-Key} security scheme. */
  public static final String API_KEY_SCHEME = "ApiKeyAuth";

  /**
   * API description.
   *
   * @param props application properties
   * @return OpenAPI model
   */
  @Bean
  public OpenAPI urlShortenerOpenApi(AppProperties props) {
    return new OpenAPI()
        .info(
            new Info()
                .title("URL Shortener API")
                .version("v1")
                .description(
                    "Shorten, resolve, inspect and deactivate links. All /api/v1 endpoints need"
                        + " X-API-Key. Every non-2xx response uses the error envelope"
                        + " {\"error\":{\"code\",\"message\",\"details\"}}."))
        .servers(List.of(new Server().url(props.baseUrl())))
        .components(
            new Components()
                .addSecuritySchemes(
                    API_KEY_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")
                        .description("Raw API key; stored server-side only as SHA-256.")));
  }
}
