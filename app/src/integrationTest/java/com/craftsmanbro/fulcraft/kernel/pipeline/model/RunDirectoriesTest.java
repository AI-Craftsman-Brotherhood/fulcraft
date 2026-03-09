package com.craftsmanbro.fulcraft.kernel.pipeline.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunDirectoriesTest {

  @TempDir Path tempDir;

  @Test
  void fromContext_rejectsNullContext() {
    assertThatThrownBy(() -> RunDirectories.from((RunContext) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("context must not be null");
  }

  @Test
  void fromContext_resolvesRunRootFromContextConfiguration() {
    Config config = new Config();
    Config.ExecutionConfig execution = new Config.ExecutionConfig();
    execution.setLogsRoot("custom-runs");
    config.setExecution(execution);
    RunContext context = new RunContext(tempDir, config, "run-ctx");

    RunDirectories directories = RunDirectories.from(context);

    assertThat(directories.runsRoot()).isEqualTo(tempDir.resolve("custom-runs"));
    assertThat(directories.runRoot()).isEqualTo(tempDir.resolve("custom-runs").resolve("run-ctx"));
  }

  @Test
  void resolveRunsRoot_usesDefaultWhenConfigIsMissing() {
    Path runsRoot = RunDirectories.resolveRunsRoot(null, tempDir);

    assertThat(runsRoot)
        .isEqualTo(tempDir.resolve(Config.ExecutionConfig.DEFAULT_LOGS_ROOT).normalize());
  }

  @Test
  void resolveRunsRoot_usesDefaultWhenConfiguredValueIsBlank() {
    Config config = new Config();
    Config.ExecutionConfig execution = new Config.ExecutionConfig();
    execution.setLogsRoot("   ");
    config.setExecution(execution);

    Path runsRoot = RunDirectories.resolveRunsRoot(config, tempDir);

    assertThat(runsRoot)
        .isEqualTo(tempDir.resolve(Config.ExecutionConfig.DEFAULT_LOGS_ROOT).normalize());
  }

  @Test
  void resolveRunsRoot_resolvesRelativePathAgainstProjectRoot() {
    Config config = new Config();
    Config.ExecutionConfig execution = new Config.ExecutionConfig();
    execution.setLogsRoot("custom/runs");
    config.setExecution(execution);

    Path runsRoot = RunDirectories.resolveRunsRoot(config, tempDir);

    assertThat(runsRoot).isEqualTo(tempDir.resolve("custom/runs").normalize());
  }

  @Test
  void resolveRunsRoot_returnsAbsolutePathWhenProjectRootIsNull() {
    Config config = new Config();
    Config.ExecutionConfig execution = new Config.ExecutionConfig();
    execution.setLogsRoot("relative-runs");
    config.setExecution(execution);

    Path runsRoot = RunDirectories.resolveRunsRoot(config, null);

    assertThat(runsRoot).isAbsolute();
    assertThat(runsRoot.endsWith(Path.of("relative-runs"))).isTrue();
  }

  @Test
  void resolveRunsRoot_keepsAbsoluteConfiguredPath() {
    Config config = new Config();
    Config.ExecutionConfig execution = new Config.ExecutionConfig();
    Path absolute = tempDir.resolve("abs").toAbsolutePath();
    execution.setLogsRoot(absolute.toString());
    config.setExecution(execution);

    Path runsRoot = RunDirectories.resolveRunsRoot(config, tempDir.resolve("ignored"));

    assertThat(runsRoot).isEqualTo(absolute.normalize());
  }

  @Test
  void resolveRunRoot_appendsRunIdToRunsRoot() {
    Config config = new Config();
    Config.ExecutionConfig execution = new Config.ExecutionConfig();
    execution.setLogsRoot("runs-root");
    config.setExecution(execution);

    Path runRoot = RunDirectories.resolveRunRoot(config, tempDir, "run-123");

    assertThat(runRoot).isEqualTo(tempDir.resolve("runs-root").resolve("run-123").normalize());
  }
}
