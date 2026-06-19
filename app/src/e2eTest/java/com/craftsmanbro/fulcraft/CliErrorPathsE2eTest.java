package com.craftsmanbro.fulcraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.support.ConfigBuilder;
import com.craftsmanbro.fulcraft.support.ProjectFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage of the CLI's error/exit-code contract. picocli reports usage errors
 * (including {@code ParameterException}s raised by commands, e.g. an unknown run-id or an invalid
 * format) with exit code {@value #USAGE}; configuration validation failures surface as {@value
 * #SOFTWARE}. A non-existent project root is only required to be non-zero.
 */
@DisplayName("ful CLI error paths and exit codes (end-to-end)")
class CliErrorPathsE2eTest extends E2eTestBase {

  /** picocli {@code CommandLine.ExitCode.USAGE}. */
  private static final int USAGE = 2;

  /** Handled application/configuration error. */
  private static final int SOFTWARE = 1;

  @Test
  @DisplayName("unknown command returns a usage error")
  void unknownCommandReturnsUsageError() {
    assertThat(runCli("definitely-not-a-command")).isEqualTo(USAGE);
  }

  @Test
  @DisplayName("unknown option returns a usage error")
  void unknownOptionReturnsUsageError() throws IOException {
    final Path config = ConfigBuilder.create().writeTo(workspace);
    ProjectFixtures.writeMultiTypeProject(workspace);

    assertThat(runCli("-c", config.toString(), "analyze", "--no-such-flag", workspace.toString()))
        .isEqualTo(USAGE);
  }

  @Test
  @DisplayName("a schema-invalid config fails validation with a software error")
  void schemaInvalidConfigReturnsError() throws IOException {
    ProjectFixtures.writeMultiTypeProject(workspace);
    final Path badConfig = writeInvalidConfig();

    assertThat(runCli("-c", badConfig.toString(), "analyze", workspace.toString()))
        .isEqualTo(SOFTWARE);
  }

  @Test
  @DisplayName("analyze on a non-existent project root fails")
  void nonexistentProjectRootFails() throws IOException {
    final Path config = ConfigBuilder.create().writeTo(workspace);
    final Path missing = workspace.resolve("does-not-exist");

    assertThat(runCli("-c", config.toString(), "analyze", missing.toString())).isNotZero();
  }

  @Test
  @DisplayName("report --run-id for a missing run fails")
  void reportWithUnknownRunIdFails() throws IOException {
    final Path config = ConfigBuilder.create().writeTo(workspace);
    ProjectFixtures.writeMultiTypeProject(workspace);

    assertThat(
            runCli(
                "-c", config.toString(), "report", "--run-id", "missing_run", workspace.toString()))
        .isEqualTo(USAGE);
  }

  @Test
  @DisplayName("document with an invalid format fails")
  void documentWithInvalidFormatFails() throws IOException {
    final Path config = ConfigBuilder.create().writeTo(workspace);
    ProjectFixtures.writeMultiTypeProject(workspace);

    assertThat(
            runCli("-c", config.toString(), "document", "--format", "bogus", workspace.toString()))
        .isEqualTo(USAGE);
  }

  /** Writes a config that parses as JSON but violates the schema (missing required sections). */
  private Path writeInvalidConfig() throws IOException {
    final Path badConfig = workspace.resolve("bad-config.json");
    Files.writeString(
        badConfig,
        """
        {
          "schema_version": 1,
          "AppName": "fulcraft",
          "version": "1.0.0",
          "project": { "id": "bad", "root": "%s", "include_paths": ["src/main/java"] },
          "analysis": { "engine": "composite" }
        }
        """
            .formatted(workspace.toString().replace("\\", "\\\\")));
    return badConfig;
  }
}
