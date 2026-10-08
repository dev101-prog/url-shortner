package com.example.urlshortener.service;

import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Normalises target URLs for opt-in dedupe (PRD A5, URL-FR-1.4): lowercase scheme and host, drop
 * the default port, keep path, query and fragment exactly as submitted.
 */
@Component
public final class UrlNormalizer {

  private static final int HTTP_DEFAULT_PORT = 80;
  private static final int HTTPS_DEFAULT_PORT = 443;

  /**
   * Normalises a URL that has already passed {@link LinkValidator#validateUrl(String)}.
   *
   * @param url validated absolute http(s) URL
   * @return normalised form
   * @throws IllegalArgumentException if the URL is not absolute or has no host
   */
  public String normalize(String url) {
    URI uri = URI.create(url);
    if (uri.getScheme() == null || uri.getHost() == null) {
      throw new IllegalArgumentException("URL must be absolute with a host");
    }
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(url.length()).append(scheme).append("://");
    if (uri.getRawUserInfo() != null) {
      out.append(uri.getRawUserInfo()).append('@');
    }
    out.append(uri.getHost().toLowerCase(Locale.ROOT));
    int port = uri.getPort();
    if (port != -1 && !isDefaultPort(scheme, port)) {
      out.append(':').append(port);
    }
    if (uri.getRawPath() != null) {
      out.append(uri.getRawPath());
    }
    if (uri.getRawQuery() != null) {
      out.append('?').append(uri.getRawQuery());
    }
    if (uri.getRawFragment() != null) {
      out.append('#').append(uri.getRawFragment());
    }
    return out.toString();
  }

  private static boolean isDefaultPort(String scheme, int port) {
    return ("http".equals(scheme) && port == HTTP_DEFAULT_PORT)
        || ("https".equals(scheme) && port == HTTPS_DEFAULT_PORT);
  }
}
