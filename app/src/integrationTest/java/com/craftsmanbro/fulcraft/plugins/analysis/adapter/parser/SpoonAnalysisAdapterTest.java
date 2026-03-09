package com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpoonAnalysisAdapterTest {

  @TempDir Path tempDir;

  @Test
  void getEngineName_returnsSpoon() {
    SpoonAnalysisAdapter adapter = new SpoonAnalysisAdapter();

    assertThat(adapter.getEngineName()).isEqualTo("spoon");
  }

  @Test
  void analyze_rejectsNullProjectRoot() {
    SpoonAnalysisAdapter adapter = new SpoonAnalysisAdapter();

    assertThatThrownBy(() -> adapter.analyze(null, new Config()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("projectRoot must not be null");
  }

  @Test
  void analyze_rejectsNullConfig() {
    SpoonAnalysisAdapter adapter = new SpoonAnalysisAdapter();

    assertThatThrownBy(() -> adapter.analyze(tempDir, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("config must not be null");
  }

  @Test
  void supports_returnsFalseForNonDirectory() throws IOException {
    SpoonAnalysisAdapter adapter = new SpoonAnalysisAdapter();
    Path file = Files.createTempFile(tempDir, "project", ".txt");

    assertThat(adapter.supports(file)).isFalse();
  }

  @Test
  void supports_returnsFalseForEmptyDirectory() {
    SpoonAnalysisAdapter adapter = new SpoonAnalysisAdapter();

    assertThat(adapter.supports(tempDir)).isFalse();
  }

  @Test
  void supports_returnsTrueForStandardLayout() throws IOException {
    SpoonAnalysisAdapter adapter = new SpoonAnalysisAdapter();
    Files.createDirectories(tempDir.resolve("src/main/java"));

    assertThat(adapter.supports(tempDir)).isTrue();
  }

  @Test
  void supports_returnsTrueWhenJavaFileExists() throws IOException {
    SpoonAnalysisAdapter adapter = new SpoonAnalysisAdapter();
    Path moduleDir = Files.createDirectories(tempDir.resolve("module"));
    Files.writeString(moduleDir.resolve("Example.java"), "class Example {}\n");

    assertThat(adapter.supports(tempDir)).isTrue();
  }
}
