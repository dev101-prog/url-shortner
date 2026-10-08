package com.example.urlshortener.infra;

import com.example.urlshortener.service.port.CodeGenerator;
import java.security.SecureRandom;

/**
 * URL-FR-1.2: random base62 codes from a CSPRNG (design §3.4), moved out of {@code LinkService} in
 * scenario B3 with no behaviour change. Constructed by {@code config.RandomConfig}.
 */
public final class RandomBase62CodeGenerator implements CodeGenerator {

  private static final char[] ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

  private final SecureRandom random;
  private final int length;

  /**
   * Creates the generator.
   *
   * @param random CSPRNG
   * @param length code length ({@code app.links.code-length})
   */
  public RandomBase62CodeGenerator(SecureRandom random, int length) {
    this.random = random;
    this.length = length;
  }

  /** {@code length} characters from the base62 alphabet via {@code random.nextInt(62)}. */
  @Override
  public String next() {
    char[] code = new char[length];
    for (int i = 0; i < length; i++) {
      code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
    }
    return new String(code);
  }
}
