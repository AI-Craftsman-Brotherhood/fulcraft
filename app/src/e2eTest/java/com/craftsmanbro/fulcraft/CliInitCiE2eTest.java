package com.craftsmanbro.fulcraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for the flag-driven {@code ful init-ci} scaffolding command (deterministic, no
 * LLM, no analysis run).
 */
@DisplayName("ful init-ci (end-to-end CLI)")
class CliInitCiE2eTest extends E2eTestBase {

  @Test
  @DisplayName("exits non-zero when the required --github-actions flag is missing")
  void requiresProviderFlag() {
    // picocli reports a usage error (exit 2) for the missing required option.
    assertThat(runCli("init-ci")).isNotZero();
  }

  @Test
  @DisplayName("--github-actions -o writes a workflow file")
  void writesWorkflowFile() {
    final Path out = workspace.resolve(".github/workflows/ful.yml");

    final int exitCode = runCli("init-ci", "--github-actions", "-o", out.toString());

    assertThat(exitCode).isZero();
    assertThat(out).exists();
    assertThat(out).isNotEmptyFile();
  }

  @Test
  @DisplayName("--dry-run writes no file")
  void dryRunWritesNothing() {
    final Path out = workspace.resolve("workflow-should-not-exist.yml");

    final int exitCode = runCli("init-ci", "--github-actions", "--dry-run", "-o", out.toString());

    assertThat(exitCode).isZero();
    assertThat(out).doesNotExist();
  }

  @Test
  @DisplayName("refuses to overwrite an existing file without --force")
  void refusesOverwriteWithoutForce() throws Exception {
    final Path out = workspace.resolve("existing.yml");
    Files.writeString(out, "pre-existing");

    assertThat(runCli("init-ci", "--github-actions", "-o", out.toString())).isEqualTo(1);
    assertThat(runCli("init-ci", "--github-actions", "--force", "-o", out.toString())).isZero();
    assertThat(Files.readString(out)).doesNotContain("pre-existing");
  }
}
