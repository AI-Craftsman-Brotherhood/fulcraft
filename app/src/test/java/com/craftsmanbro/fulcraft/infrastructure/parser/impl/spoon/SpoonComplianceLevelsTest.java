package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("SpoonComplianceLevels maps user-facing strings to Spoon's int compliance level")
class SpoonComplianceLevelsTest {

  @Test
  @DisplayName("null and blank fall back to DEFAULT")
  void nullAndBlank_returnDefault() {
    assertThat(SpoonComplianceLevels.resolve(null)).isEqualTo(SpoonComplianceLevels.DEFAULT);
    assertThat(SpoonComplianceLevels.resolve("")).isEqualTo(SpoonComplianceLevels.DEFAULT);
    assertThat(SpoonComplianceLevels.resolve("  ")).isEqualTo(SpoonComplianceLevels.DEFAULT);
  }

  @ParameterizedTest
  @CsvSource({
    "JAVA_8, 8",
    "java-8, 8",
    "8, 8",
    "JAVA_11, 11",
    "11, 11",
    "JAVA_16, 16",
    "16, 16",
    "JAVA_17, 17",
    "17, 17",
    "JAVA_21, 21",
    "21, 21",
    "Java 21, 21"
  })
  @DisplayName("numeric/canonical forms resolve to the matching int")
  void resolvesNumericAndCanonical(final String raw, final int expected) {
    assertThat(SpoonComplianceLevels.resolve(raw)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"JAVA_22, 21", "JAVA_25, 21", "JAVA_99, 21", "999, 21"})
  @DisplayName("values above MAX_SUPPORTED (21) clamp to 21")
  void clampsAboveMax(final String raw, final int expected) {
    assertThat(SpoonComplianceLevels.resolve(raw)).isEqualTo(expected);
  }

  @Test
  @DisplayName("aliases map to their concrete versions")
  void aliases() {
    assertThat(SpoonComplianceLevels.resolve("BLEEDING_EDGE")).isEqualTo(21);
    assertThat(SpoonComplianceLevels.resolve("POPULAR")).isEqualTo(11);
    assertThat(SpoonComplianceLevels.resolve("CURRENT")).isEqualTo(16);
  }

  @Test
  @DisplayName("fromConfig returns DEFAULT for null Config")
  void fromConfig_nullConfig() {
    assertThat(SpoonComplianceLevels.fromConfig(null)).isEqualTo(SpoonComplianceLevels.DEFAULT);
  }
}
