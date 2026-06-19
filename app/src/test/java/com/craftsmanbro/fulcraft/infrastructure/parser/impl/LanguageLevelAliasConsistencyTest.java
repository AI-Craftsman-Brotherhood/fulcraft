package com.craftsmanbro.fulcraft.infrastructure.parser.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.LanguageLevels;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon.SpoonComplianceLevels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Guards that the {@code language_level} aliases resolve to the SAME concrete Java level in both
 * analysis engines. Without this, the JavaParser ({@link LanguageLevels}) and Spoon ({@link
 * SpoonComplianceLevels}) mappings can silently drift apart again (they hold the 17/21 values
 * independently, with no shared constant).
 */
@DisplayName("language_level aliases resolve identically across engines")
class LanguageLevelAliasConsistencyTest {

  @ParameterizedTest
  @CsvSource({
    "POPULAR, 17",
    "CURRENT, 21",
    "JAVA_17, 17",
    "JAVA_21, 21",
    "8, 8",
    "11, 11",
  })
  @DisplayName("JavaParser and Spoon agree on the numeric level for each alias/level")
  void enginesAgree(final String raw, final int expectedLevel) {
    assertThat(SpoonComplianceLevels.resolve(raw)).isEqualTo(expectedLevel);
    assertThat(LanguageLevels.resolve(raw).name()).isEqualTo("JAVA_" + expectedLevel);
  }
}
