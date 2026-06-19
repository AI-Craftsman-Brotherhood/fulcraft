package com.craftsmanbro.fulcraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end smoke test that drives the real CLI ({@link Main#run}) for the {@code analyze} command
 * against a temporary project. Exercises argument parsing, config loading and schema validation,
 * dependency wiring, and the full composite analysis pipeline; a non-zero exit indicates any of
 * those broke. Detailed analysis correctness is covered by the integration tests.
 *
 * <p>Opt-in: only runs under the {@code e2eTest} task (RUN_E2E=true / -Pe2e). Lives in the {@code
 * com.craftsmanbro.fulcraft} package to call the package-private {@link Main#run(String[])} entry
 * point without triggering {@code System.exit}.
 */
@DisplayName("ful analyze (end-to-end CLI)")
class CliAnalyzeE2eTest {

  @AfterEach
  void resetGlobalLoggerState() {
    // Main.run() accumulates de-duplicated warn/info keys in static Logger state; reset so a
    // shared JVM does not leak state into other tests.
    Logger.resetWarnOnceKeys();
    Logger.resetInfoOnceKeys();
  }

  @Test
  @DisplayName("`ful -c config analyze <project>` completes successfully (exit 0)")
  void analyzeCompletesSuccessfully(@TempDir final Path projectRoot) throws IOException {
    writeSource(
        projectRoot,
        "com/demo/Sample.java",
        """
        package com.demo;

        public class Sample {
          public int add(int a, int b) {
            return a + b;
          }
        }
        """);
    final Path config = writeConfig(projectRoot);

    final int exitCode =
        Main.run(new String[] {"-c", config.toString(), "analyze", projectRoot.toString()});

    assertThat(exitCode).isZero();
  }

  private static void writeSource(final Path root, final String relativePath, final String content)
      throws IOException {
    final Path file = root.resolve("src/main/java").resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private static Path writeConfig(final Path root) throws IOException {
    final Path config = root.resolve("config.json");
    final String escapedRoot = root.toString().replace("\\", "\\\\");
    Files.writeString(
        config,
        """
        {
          "schema_version": 1,
          "AppName": "fulcraft",
          "version": "1.0.0",
          "project": {
            "id": "e2e",
            "root": "%s",
            "build_tool": "gradle",
            "include_paths": ["src/main/java"],
            "exclude_paths": ["src/test", "build"]
          },
          "analysis": {
            "engine": "composite",
            "source_root_mode": "AUTO",
            "source_root_paths": ["src/main/java"],
            "language_level": "JAVA_21",
            "spoon": { "no_classpath": true }
          },
          "selection_rules": {
            "class_min_loc": 1,
            "class_min_method_count": 1,
            "method_min_loc": 1,
            "method_max_loc": 1000,
            "max_targets": 200,
            "max_methods_per_class": 5
          },
          "llm": {
            "provider": "openai",
            "model_name": "gpt-4o",
            "api_key": "${OPENAI_API_KEY}",
            "url": "https://api.openai.com/v1"
          }
        }
        """
            .formatted(escapedRoot));
    return config;
  }
}
