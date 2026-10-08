package com.example.urlshortener.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.infra.RandomBase62CodeGenerator;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomBase62CodeGeneratorTest {

  @Test
  void fr1_2_codesAreBase62OfTheConfiguredLength() {
    RandomBase62CodeGenerator generator = new RandomBase62CodeGenerator(new SecureRandom(), 7);
    Set<String> seen = new HashSet<>();

    for (int i = 0; i < 1_000; i++) {
      String code = generator.next();
      assertThat(code).matches("^[A-Za-z0-9]{7}$");
      seen.add(code);
    }

    assertThat(seen).as("62^7 codes: 1,000 draws should not collide").hasSize(1_000);
  }

  @Test
  void fr1_2_mapsRandomIndexesOntoTheBase62Alphabet() {
    SecureRandom random = mock(SecureRandom.class);
    when(random.nextInt(anyInt())).thenReturn(0, 9, 10, 35, 36, 61);

    assertThat(new RandomBase62CodeGenerator(random, 6).next()).isEqualTo("09AZaz");
    verify(random, org.mockito.Mockito.times(6)).nextInt(62);
  }
}
