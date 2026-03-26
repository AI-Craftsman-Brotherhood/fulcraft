package com.craftsmanbro.fulcraft.infrastructure.fs.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunPathsTest {

  @TempDir Path tempDir;

  @Test
  void resolveRunsRoot_usesDefaultWhenLogsRootIsMissing() {
    Path runsRoot = RunPaths.resolveRunsRoot(new Config(), tempDir);

    assertThat(runsRoot).isEqualTo(tempDir.resolve(RunPaths.DEFAULT_RUNS_DIR).normalize());
  }

  @Test
  void resolveRunsRoot_usesDefaultWhenLogsRootIsBlank() {
    Config config = new Config();
    Config.ExecutionConfig executionConfig = new Config.ExecutionConfig();
    executionConfig.setLogsRoot("   ");
    config.setExecution(executionConfig);

    Path runsRoot = RunPaths.resolveRunsRoot(config, tempDir);

    assertThat(runsRoot).isEqualTo(tempDir.resolve(RunPaths.DEFAULT_RUNS_DIR).normalize());
  }

  @Test
  void resolveRunsRoot_resolvesRelativePathAgainstProjectRoot() {
    Config config = new Config();
    Config.ExecutionConfig executionConfig = new Config.ExecutionConfig();
    executionConfig.setLogsRoot("custom-runs");
    config.setExecution(executionConfig);

    Path runsRoot = RunPaths.resolveRunsRoot(config, tempDir);

    assertThat(runsRoot).isEqualTo(tempDir.resolve("custom-runs"));
  }

  @Test
  void resolveRunsRoot_keepsAbsolutePath() {
    Config config = new Config();
    Config.ExecutionConfig executionConfig = new Config.ExecutionConfig();
    Path absolute = tempDir.resolve("absolute-runs").toAbsolutePath();
    executionConfig.setLogsRoot(absolute.toString());
    config.setExecution(executionConfig);

    Path runsRoot = RunPaths.resolveRunsRoot(config, tempDir.resolve("ignored"));

    assertThat(runsRoot).isEqualTo(absolute.normalize());
  }

  @Test
  void from_resolvesRunRootAndDirectories() {
    Config config = new Config();
    Config.ExecutionConfig executionConfig = new Config.ExecutionConfig();
    executionConfig.setLogsRoot(".ful/custom-runs");
    config.setExecution(executionConfig);

    RunPaths runPaths = RunPaths.from(config, tempDir, "run-123");

    Path expectedRunRoot = tempDir.resolve(".ful/custom-runs").resolve("run-123").normalize();
    assertThat(runPaths.runsRoot()).isEqualTo(tempDir.resolve(".ful/custom-runs").normalize());
    assertThat(runPaths.runRoot()).isEqualTo(expectedRunRoot);
    assertThat(runPaths.logsDir()).isEqualTo(expectedRunRoot.resolve(RunPaths.LOGS_DIR));
    assertThat(runPaths.analysisDir()).isEqualTo(expectedRunRoot.resolve(RunPaths.ANALYSIS_DIR));
    assertThat(runPaths.planDir()).isEqualTo(expectedRunRoot.resolve(RunPaths.PLAN_DIR));
    assertThat(runPaths.generationDir())
        .isEqualTo(expectedRunRoot.resolve(RunPaths.GENERATION_DIR));
    assertThat(runPaths.reportDir()).isEqualTo(expectedRunRoot.resolve(RunPaths.REPORT_DIR));
    assertThat(runPaths.junitReportsDir())
        .isEqualTo(runPaths.reportDir().resolve(RunPaths.JUNIT_REPORTS_DIR));
    assertThat(runPaths.llmLogFile()).isEqualTo(runPaths.logsDir().resolve(RunPaths.LLM_LOG_FILE));
  }

  @Test
  void logFile_usesDefaultWhenConfiguredPathIsNullOrInvalid() {
    RunPaths runPaths = RunPaths.from(new Config(), tempDir, "run-log");

    assertThat(runPaths.logFile(null))
        .isEqualTo(runPaths.logsDir().resolve(RunPaths.DEFAULT_LOG_FILE));
    assertThat(runPaths.logFile(Path.of("")))
        .isEqualTo(runPaths.logsDir().resolve(RunPaths.DEFAULT_LOG_FILE));
  }

  @Test
  void logFile_usesConfiguredFileName() {
    RunPaths runPaths = RunPaths.from(new Config(), tempDir, "run-log-name");

    Path logFile = runPaths.logFile(Path.of("nested/path/custom.log"));

    assertThat(logFile).isEqualTo(runPaths.logsDir().resolve("custom.log"));
  }

  @Test
  void ensureDirectories_createsExpectedDirectories() throws Exception {
    RunPaths runPaths = RunPaths.from(new Config(), tempDir, "run-create");

    runPaths.ensureDirectories();

    assertThat(Files.isDirectory(runPaths.runRoot())).isTrue();
    assertThat(Files.isDirectory(runPaths.logsDir())).isTrue();
    assertThat(Files.isDirectory(runPaths.analysisDir())).isTrue();
    assertThat(Files.isDirectory(runPaths.planDir())).isTrue();
    assertThat(Files.isDirectory(runPaths.generationDir())).isTrue();
    assertThat(Files.isDirectory(runPaths.reportDir())).isTrue();
  }

  @Test
  void resolveRunRoot_rejectsNullRunId() {
    assertThatThrownBy(() -> RunPaths.resolveRunRoot(new Config(), tempDir, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageEndingWith("runId must not be null");
  }
}
