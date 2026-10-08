package com.example.urlshortener.service.port;

/**
 * Source of candidate short codes (scenario B3, design §11.3). Uniqueness is still decided by the
 * database ({@code INSERT ... ON CONFLICT DO NOTHING}); implementations only propose codes, which
 * enables later strategies (sequence + Hashids, pre-generated pools) without touching services.
 */
public interface CodeGenerator {

  /**
   * Proposes the next candidate code.
   *
   * @return a code matching {@code ^[A-Za-z0-9_-]{4,32}$}
   */
  String next();
}
