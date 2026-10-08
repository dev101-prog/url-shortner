package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.concurrent.locks.ReentrantLock;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * URL-FR-7.3: {@code HMAC-SHA256(salt, ip)} as lowercase hex. The raw IP is never stored or logged;
 * the salt comes from {@code app.analytics.ip-salt} (a secret outside local).
 */
@Component
public final class IpHasher {

  private static final String ALGORITHM = "HmacSHA256";

  private final Mac mac;
  private final ReentrantLock lock = new ReentrantLock();

  /**
   * Initialises the HMAC with the configured salt; fails fast on an unusable salt.
   *
   * @param props application properties
   */
  public IpHasher(AppProperties props) {
    try {
      Mac m = Mac.getInstance(ALGORITHM);
      m.init(
          new SecretKeySpec(
              props.analytics().ipSalt().getBytes(StandardCharsets.UTF_8), ALGORITHM));
      this.mac = m;
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("app.analytics.ip-salt cannot be used as an HMAC key", e);
    }
  }

  /**
   * URL-FR-7.3: hashes a client IP.
   *
   * @param ip client IP (never logged), may be {@code null}
   * @return 64-char lowercase hex HMAC, or {@code null} when {@code ip} is null
   */
  public String hash(String ip) {
    if (ip == null) {
      return null;
    }
    byte[] digest;
    lock.lock();
    try {
      digest = mac.doFinal(ip.getBytes(StandardCharsets.UTF_8));
    } finally {
      lock.unlock();
    }
    return HexFormat.of().formatHex(digest);
  }
}
