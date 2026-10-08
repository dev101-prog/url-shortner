package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.example.urlshortener.service.IpHasher;
import com.example.urlshortener.support.TestProperties;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IpHasherTest {

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "203.0.113.10, 081be95cdda838c0390345ef0bd1004b1509648c1511d7908d01af957bf61fa5",
    "198.51.100.7, 027ec7699d9fb2c0277ed9fba2da9f667d031b04f1f33117d9ff177fb6e8838b",
    "192.0.2.55,   473492d386b23c12622b28329df48f1fad12822dae0f3935d37db34173d5a243"
  })
  void fr7_3_matchesSeedHmacVectorsForLocalSalt(String ip, String expected) {
    assertThat(new IpHasher(TestProperties.withSalt("local-dev-salt")).hash(ip))
        .isEqualTo(expected);
  }

  @Test
  void fr7_3_noRawIp() {
    String ip = "203.0.113.10";
    String hash = new IpHasher(TestProperties.withSalt("local-dev-salt")).hash(ip);

    assertThat(hash).matches("^[0-9a-f]{64}$").doesNotContain(ip).doesNotContain("203");
  }

  @Test
  void fr7_3_saltChangesTheHash() {
    String a = new IpHasher(TestProperties.withSalt("salt-a")).hash("198.51.100.7");
    String b = new IpHasher(TestProperties.withSalt("salt-b")).hash("198.51.100.7");

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void nullIpHashesToNull() {
    assertThat(new IpHasher(TestProperties.defaults()).hash(null)).isNull();
  }

  @Test
  void emptySaltFailsFast() {
    assertThatIllegalStateException()
        .isThrownBy(() -> new IpHasher(TestProperties.withSalt("")))
        .withMessageContaining("ip-salt")
        .withMessageNotContaining("local-dev-salt");
  }

  @Test
  void isThreadSafeUnderConcurrentUse() throws Exception {
    IpHasher hasher = new IpHasher(TestProperties.withSalt("local-dev-salt"));
    Set<String> results = ConcurrentHashMap.newKeySet();
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> futures =
          IntStream.range(0, 2_000)
              .<Future<?>>mapToObj(i -> pool.submit(() -> results.add(hasher.hash("203.0.113.10"))))
              .toList();
      for (Future<?> f : futures) {
        f.get();
      }
    }

    assertThat(results)
        .containsExactly("081be95cdda838c0390345ef0bd1004b1509648c1511d7908d01af957bf61fa5");
  }
}
