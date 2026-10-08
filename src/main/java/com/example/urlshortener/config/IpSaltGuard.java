package com.example.urlshortener.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Design §13.3 / URL-NFR-4.4: refuse to start with the placeholder IP salt ({@code change-me})
 * unless the {@code local} profile is active, so a shared environment can never hash IPs with a
 * publicly known salt.
 */
@Component
public final class IpSaltGuard {

  /** Placeholder default of {@code app.analytics.ip-salt} in application.yml. */
  public static final String PLACEHOLDER_SALT = "change-me";

  private final String ipSalt;
  private final boolean localProfile;

  /**
   * Checks the salt at startup.
   *
   * @param props application properties
   * @param environment Spring environment (active profiles)
   * @throws IllegalStateException if the placeholder salt is used outside the local profile
   */
  public IpSaltGuard(AppProperties props, Environment environment) {
    this.ipSalt = props.analytics().ipSalt();
    this.localProfile = environment.acceptsProfiles(Profiles.of("local"));
    verify();
  }

  /**
   * The rule itself.
   *
   * @throws IllegalStateException if the placeholder salt is used outside the local profile
   */
  public void verify() {
    if (PLACEHOLDER_SALT.equals(ipSalt) && !localProfile) {
      throw new IllegalStateException(
          "app.analytics.ip-salt (APP_IP_SALT) must be set to a secret value outside the local"
              + " profile");
    }
  }
}
