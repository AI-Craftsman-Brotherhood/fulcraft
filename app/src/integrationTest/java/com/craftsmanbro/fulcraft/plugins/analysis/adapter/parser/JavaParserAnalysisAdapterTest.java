package com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParserAnalysisAdapterTest {

  @TempDir Path tempDir;

  @Test
  void getEngineName_returnsJavaParser() {
    JavaParserAnalysisAdapter adapter = new JavaParserAnalysisAdapter();

    assertThat(adapter.getEngineName()).isEqualTo("javaparser");
  }

  @Test
  void analyze_rejectsNullProjectRoot() {
    JavaParserAnalysisAdapter adapter = new JavaParserAnalysisAdapter();

    assertThatThrownBy(() -> adapter.analyze(null, new Config()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("projectRoot must not be null");
  }

  @Test
  void analyze_rejectsNullConfig() {
    JavaParserAnalysisAdapter adapter = new JavaParserAnalysisAdapter();

    assertThatThrownBy(() -> adapter.analyze(tempDir, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("config must not be null");
  }

  @Test
  void supports_returnsFalseForNonDirectory() throws IOException {
    JavaParserAnalysisAdapter adapter = new JavaParserAnalysisAdapter();
    Path file = Files.createTempFile(tempDir, "project", ".txt");

    assertThat(adapter.supports(file)).isFalse();
  }

  @Test
  void supports_returnsFalseForEmptyDirectory() {
    JavaParserAnalysisAdapter adapter = new JavaParserAnalysisAdapter();

    assertThat(adapter.supports(tempDir)).isFalse();
  }

  @Test
  void supports_returnsTrueForStandardLayout() throws IOException {
    JavaParserAnalysisAdapter adapter = new JavaParserAnalysisAdapter();
    Files.createDirectories(tempDir.resolve("src/main/java"));

    assertThat(adapter.supports(tempDir)).isTrue();
  }

  @Test
  void supports_returnsTrueForNestedSourceDirectory() throws IOException {
    JavaParserAnalysisAdapter adapter = new JavaParserAnalysisAdapter();
    Files.createDirectories(tempDir.resolve("module/src"));

    assertThat(adapter.supports(tempDir)).isTrue();
  }

  @Test
  void supports_returnsTrueWhenJavaFileExists() throws IOException {
    JavaParserAnalysisAdapter adapter = new JavaParserAnalysisAdapter();
    Path moduleDir = Files.createDirectories(tempDir.resolve("module"));
    Files.writeString(moduleDir.resolve("Example.java"), "class Example {}\n");

    assertThat(adapter.supports(tempDir)).isTrue();
  }
}
