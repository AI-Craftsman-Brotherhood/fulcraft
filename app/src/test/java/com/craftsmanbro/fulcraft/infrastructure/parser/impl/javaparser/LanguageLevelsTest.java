package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("LanguageLevels resolves user-facing strings to JavaParser LanguageLevel")
class LanguageLevelsTest {

  @Test
  @DisplayName("null and blank input return DEFAULT")
  void nullAndBlank_returnDefault() {
    assertThat(LanguageLevels.resolve(null)).isEqualTo(LanguageLevels.DEFAULT);
    assertThat(LanguageLevels.resolve("")).isEqualTo(LanguageLevels.DEFAULT);
    assertThat(LanguageLevels.resolve("   ")).isEqualTo(LanguageLevels.DEFAULT);
  }

  @ParameterizedTest
  @CsvSource({
    "JAVA_8, JAVA_8",
    "java_8, JAVA_8",
    "Java 8, JAVA_8",
    "java-8, JAVA_8",
    "8, JAVA_8",
    "JAVA_11, JAVA_11",
    "11, JAVA_11",
    "JAVA_17, JAVA_17",
    "Java17, JAVA_17",
    "17, JAVA_17",
    "JAVA_18, JAVA_18"
  })
  @DisplayName("canonical names and numeric forms map to the correct enum")
  void canonicalAndNumericForms(final String raw, final String expectedName) {
    assertThat(LanguageLevels.resolve(raw).name()).isEqualTo(expectedName);
  }

  @ParameterizedTest
  @CsvSource({"JAVA_21", "21", "JAVA_19", "JAVA_20", "BLEEDING_EDGE"})
  @DisplayName("19+ and BLEEDING_EDGE all map to the BLEEDING_EDGE alias")
  void java19PlusMapsToBleedingEdge(final String raw) {
    assertThat(LanguageLevels.resolve(raw)).isEqualTo(LanguageLevel.BLEEDING_EDGE);
  }

  @Test
  @DisplayName("unknown values fall back to DEFAULT without throwing")
  void unknownValuesFallBack() {
    assertThat(LanguageLevels.resolve("JAVA_99")).isEqualTo(LanguageLevels.DEFAULT);
    assertThat(LanguageLevels.resolve("garbage")).isEqualTo(LanguageLevels.DEFAULT);
    assertThat(LanguageLevels.resolve("999")).isEqualTo(LanguageLevels.DEFAULT);
  }

  @Test
  @DisplayName("configurationFor produces a usable ParserConfiguration")
  void configurationFor_producesUsableConfig() {
    final ParserConfiguration pc = LanguageLevels.configurationFor("JAVA_17");
    assertThat(pc).isNotNull();
    assertThat(pc.getLanguageLevel()).isEqualTo(LanguageLevel.JAVA_17);
  }

  @Test
  @DisplayName("DEFAULT parses a record sample without errors")
  void defaultSupportsRecords() {
    // Concrete behaviour check: whatever DEFAULT resolves to must accept Java 14+ syntax.
    final ParserConfiguration pc = LanguageLevels.configurationFor(null);
    final com.github.javaparser.JavaParser parser = new com.github.javaparser.JavaParser(pc);
    final var result = parser.parse("package sample; public record Point(int x, int y) {}");
    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getProblems()).isEmpty();
  }
}
