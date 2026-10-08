package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.example.urlshortener.service.UrlNormalizer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlNormalizerTest {

  private final UrlNormalizer normalizer = new UrlNormalizer();

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "HTTPS://Example.ORG/Path?Q=1#Frag | https://example.org/Path?Q=1#Frag",
        "http://example.org:80/a           | http://example.org/a",
        "https://example.org:443/a         | https://example.org/a",
        "http://example.org:443/a          | http://example.org:443/a",
        "https://example.org:8443/a        | https://example.org:8443/a",
        "https://example.org               | https://example.org",
        "https://example.org/a%20b?x=%2F   | https://example.org/a%20b?x=%2F",
        "https://user@Example.org/         | https://user@example.org/",
      })
  void a5_normalisesSchemeHostAndDefaultPortOnly(String input, String expected) {
    assertThat(normalizer.normalize(input)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/relative/path", "mailto:someone@example.org"})
  void rejectsUrlsWithoutSchemeOrHost(String url) {
    assertThatIllegalArgumentException().isThrownBy(() -> normalizer.normalize(url));
  }
}
