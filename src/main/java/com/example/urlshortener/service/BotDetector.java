package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * URL-FR-7.4: flags (never drops) bot clicks using the versioned, case-insensitive user-agent
 * pattern list from {@code app.analytics.bot-patterns}.
 */
@Component
public final class BotDetector {

  private final List<Pattern> patterns;
  private final String patternsVersion;

  /**
   * Compiles the configured patterns.
   *
   * @param props application properties
   */
  public BotDetector(AppProperties props) {
    this.patterns =
        props.analytics().botPatterns().stream()
            .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
            .toList();
    this.patternsVersion = props.analytics().botPatternsVersion();
  }

  /**
   * URL-FR-7.4: true when any pattern matches the user agent. A missing user agent is not flagged.
   *
   * @param userAgent raw user agent, may be {@code null}
   * @return whether the click is a bot click
   */
  public boolean isBot(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) {
      return false;
    }
    for (Pattern pattern : patterns) {
      if (pattern.matcher(userAgent).find()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Version label of the active pattern list.
   *
   * @return version, e.g. {@code 2026-10-01}
   */
  public String patternsVersion() {
    return patternsVersion;
  }
}
