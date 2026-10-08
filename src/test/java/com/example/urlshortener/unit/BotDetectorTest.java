package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.service.BotDetector;
import com.example.urlshortener.support.TestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BotDetectorTest {

  private final BotDetector detector = new BotDetector(TestProperties.defaults());

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Googlebot/2.1 (+http://www.google.com/bot.html)",
        "curl/8.4.0",
        "Wget/1.21",
        "facebookexternalhit/1.1",
        "Mozilla/5.0 (compatible; Yahoo! Slurp)",
        "python-requests/2.31",
        "Mozilla/5.0 HeadlessChrome/120.0",
        "SomeCrawler/1.0",
        "BaiduSPIDER",
        "BINGBOT"
      })
  void fr7_4_flagsBotsCaseInsensitively(String userAgent) {
    assertThat(detector.isBot(userAgent)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Mobile Safari"
      })
  void fr7_4_humanBrowsersAreNotBots(String userAgent) {
    assertThat(detector.isBot(userAgent)).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void fr7_4_missingUserAgentIsNotFlagged(String userAgent) {
    assertThat(detector.isBot(userAgent)).isFalse();
  }

  @Test
  void fr7_4_patternListIsVersioned() {
    assertThat(detector.patternsVersion()).isEqualTo("2026-10-01");
  }
}
