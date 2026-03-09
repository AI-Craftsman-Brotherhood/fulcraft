package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParserAnalyzerTest {

  @TempDir Path tempDir;

  @Test
  void supports_returnsFalseWhenPathIsNullOrMissing() {
    JavaParserAnalyzer analyzer = new JavaParserAnalyzer(GlobalOpenTelemetry.getTracer("test"));

    assertFalse(analyzer.supports(null));
    assertFalse(analyzer.supports(tempDir.resolve("missing")));
  }

  @Test
  void supports_returnsTrueForKnownSourceLayouts() throws Exception {
    JavaParserAnalyzer analyzer = new JavaParserAnalyzer(GlobalOpenTelemetry.getTracer("test"));

    Path mavenLike = tempDir.resolve("maven-like");
    Files.createDirectories(mavenLike.resolve("src/main/java"));
    assertTrue(analyzer.supports(mavenLike));

    Path androidLike = tempDir.resolve("android-like");
    Files.createDirectories(androidLike.resolve("app/src/main/java"));
    assertTrue(analyzer.supports(androidLike));

    Path flatSrc = tempDir.resolve("flat-src");
    Files.createDirectories(flatSrc.resolve("src"));
    assertTrue(analyzer.supports(flatSrc));
  }

  @Test
  void analyze_throwsIOExceptionWhenNoSourceDirectoryIsFound() throws Exception {
    JavaParserAnalyzer analyzer = new JavaParserAnalyzer(GlobalOpenTelemetry.getTracer("test"));
    Path projectRoot = tempDir.resolve("empty-project");
    Files.createDirectories(projectRoot);

    Config config = Config.createDefault();
    config.getProject().setId("project");
    config.getProject().setCommit("abc123");

    IOException error =
        assertThrows(IOException.class, () -> analyzer.analyze(config, projectRoot));
    assertTrue(error.getMessage().contains("Source directory not found in project"));
  }
}
