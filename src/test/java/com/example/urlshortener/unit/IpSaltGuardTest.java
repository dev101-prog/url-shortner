package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.example.urlshortener.config.IpSaltGuard;
import com.example.urlshortener.support.TestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class IpSaltGuardTest {

  private static MockEnvironment profiles(String... profiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(profiles);
    return env;
  }

  @Test
  void nfr4_4_placeholderSaltFailsStartupOutsideLocal() {
    assertThatIllegalStateException()
        .isThrownBy(() -> new IpSaltGuard(TestProperties.withSalt("change-me"), profiles()))
        .withMessageContaining("APP_IP_SALT");
    assertThatIllegalStateException()
        .isThrownBy(() -> new IpSaltGuard(TestProperties.withSalt("change-me"), profiles("prod")));
  }

  @Test
  void nfr4_4_placeholderSaltIsAllowedUnderLocal() {
    assertThatCode(() -> new IpSaltGuard(TestProperties.withSalt("change-me"), profiles("local")))
        .doesNotThrowAnyException();
    assertThatCode(
            () -> new IpSaltGuard(TestProperties.withSalt("change-me"), profiles("test", "local")))
        .doesNotThrowAnyException();
  }

  @Test
  void nfr4_4_realSaltIsAllowedEverywhere() {
    assertThatCode(
            () -> new IpSaltGuard(TestProperties.withSalt("s3cr3t-from-vault"), profiles("prod")))
        .doesNotThrowAnyException();
  }
}
