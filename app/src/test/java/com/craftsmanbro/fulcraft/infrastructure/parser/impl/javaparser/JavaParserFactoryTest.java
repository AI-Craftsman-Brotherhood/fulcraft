package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JavaParserFactory wires Config -> ParserConfiguration consistently")
class JavaParserFactoryTest {

  private static final String RECORD_SAMPLE =
      "package sample;\n" + "public record Point(int x, int y) {}\n";

  private static final String SEALED_SAMPLE =
      "package sample;\n"
          + "public sealed interface Shape permits Circle, Square {}\n"
          + "final class Circle implements Shape {}\n"
          + "final class Square implements Shape {}\n";

  @Test
  @DisplayName("newDefaultParser uses LanguageLevels.DEFAULT")
  void newDefaultParser_usesDefault() {
    final JavaParser parser = JavaParserFactory.newDefaultParser();
    assertThat(parser.getParserConfiguration().getLanguageLevel())
        .isEqualTo(LanguageLevels.DEFAULT);
  }

  @Test
  @DisplayName("newParser with null Config falls back to DEFAULT")
  void newParser_nullConfig_default() {
    final ParserConfiguration pc = JavaParserFactory.newConfiguration(null);
    assertThat(pc.getLanguageLevel()).isEqualTo(LanguageLevels.DEFAULT);
  }

  @Test
  @DisplayName("newParser with Config.analysis.language_level=JAVA_11 honours user choice")
  void newParser_usesConfiguredLevel() {
    final Config config = new Config();
    final Config.AnalysisConfig analysis = new Config.AnalysisConfig();
    analysis.setLanguageLevel("JAVA_11");
    config.setAnalysis(analysis);

    final ParserConfiguration pc = JavaParserFactory.newConfiguration(config);
    assertThat(pc.getLanguageLevel()).isEqualTo(LanguageLevel.JAVA_11);
  }

  @Test
  @DisplayName("default parser successfully parses a Java 14+ record declaration")
  void defaultParser_parsesRecord() {
    final ParseResult<CompilationUnit> result =
        JavaParserFactory.newDefaultParser().parse(RECORD_SAMPLE);
    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getProblems()).isEmpty();
  }

  @Test
  @DisplayName("default parser successfully parses a Java 17 sealed hierarchy")
  void defaultParser_parsesSealed() {
    final ParseResult<CompilationUnit> result =
        JavaParserFactory.newDefaultParser().parse(SEALED_SAMPLE);
    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getProblems()).isEmpty();
  }
}
