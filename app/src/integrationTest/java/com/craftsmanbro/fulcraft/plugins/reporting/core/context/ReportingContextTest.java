package com.craftsmanbro.fulcraft.plugins.reporting.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.Config.ExecutionConfig;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.reporting.io.contract.CoverageReader;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportingContextTest {

  private RunContext runContext;
  private Config config;
  private CoverageReader coverageReader;
  private ExecutionConfig executionConfig;

  @BeforeEach
  void setUp() {
    runContext = mock(RunContext.class);
    config = mock(Config.class);
    coverageReader = mock(CoverageReader.class);
    executionConfig = mock(ExecutionConfig.class);
  }

  @Test
  void constructor_shouldThrowException_whenRunContextIsNull() {
    assertThatThrownBy(() -> new ReportingContext(null, config, coverageReader))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("runContext must not be null");
  }

  @Test
  void constructor_shouldThrowException_whenConfigIsNull() {
    assertThatThrownBy(() -> new ReportingContext(runContext, null, coverageReader))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("config must not be null");
  }

  @Test
  void constructor_shouldThrowException_whenCoverageReaderIsNull() {
    assertThatThrownBy(() -> new ReportingContext(runContext, config, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("coverageReader must not be null");
  }

  @Test
  void resolveOutputDirectory_shouldUseProvidedDirectory(@TempDir Path tempDir) {
    Path outputDir = tempDir.resolve("output");
    ReportingContext reportingContext =
        new ReportingContext(runContext, config, outputDir, coverageReader);

    assertThat(reportingContext.resolveOutputDirectory()).isEqualTo(outputDir);
  }

  @Test
  void resolveOutputDirectory_shouldResolveFromRunContext_whenOutputDirectoryIsOnlyInCtx(
      @TempDir Path tempDir) {
    Path projectRoot = tempDir.resolve("project");
    String runId = "test-run-id";

    when(runContext.getConfig()).thenReturn(config);
    when(runContext.getProjectRoot()).thenReturn(projectRoot);
    when(runContext.getRunId()).thenReturn(runId);
    when(config.getExecution()).thenReturn(executionConfig);
    when(executionConfig.getEffectiveLogsRoot())
        .thenReturn(Config.ExecutionConfig.DEFAULT_LOGS_ROOT);

    ReportingContext reportingContext = new ReportingContext(runContext, config, coverageReader);
    Path result = reportingContext.resolveOutputDirectory();

    Path expected = projectRoot.resolve(".ful/runs").resolve(runId).resolve("report").normalize();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void resolveOutputDirectory_shouldUseConfiguredRelativeLogsRoot(@TempDir Path tempDir) {
    Path projectRoot = tempDir.resolve("project");
    String runId = "test-run-id";

    when(runContext.getConfig()).thenReturn(config);
    when(runContext.getProjectRoot()).thenReturn(projectRoot);
    when(runContext.getRunId()).thenReturn(runId);
    when(config.getExecution()).thenReturn(executionConfig);
    when(executionConfig.getEffectiveLogsRoot()).thenReturn("custom-runs");

    ReportingContext reportingContext = new ReportingContext(runContext, config, coverageReader);
    Path result = reportingContext.resolveOutputDirectory();

    Path expected = projectRoot.resolve("custom-runs").resolve(runId).resolve("report").normalize();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void resolveOutputDirectory_shouldUseConfiguredAbsoluteLogsRoot(@TempDir Path tempDir) {
    Path projectRoot = tempDir.resolve("project");
    Path absoluteLogsRoot = tempDir.resolve("custom-runs").toAbsolutePath();
    String runId = "test-run-id";

    when(runContext.getConfig()).thenReturn(config);
    when(runContext.getProjectRoot()).thenReturn(projectRoot);
    when(runContext.getRunId()).thenReturn(runId);
    when(config.getExecution()).thenReturn(executionConfig);
    when(executionConfig.getEffectiveLogsRoot()).thenReturn(absoluteLogsRoot.toString());

    ReportingContext reportingContext = new ReportingContext(runContext, config, coverageReader);
    Path result = reportingContext.resolveOutputDirectory();

    Path expected = absoluteLogsRoot.resolve(runId).resolve("report").normalize();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void getters_shouldReturnCorrectValues() {
    ReportingContext reportingContext = new ReportingContext(runContext, config, coverageReader);

    assertThat(reportingContext.getRunContext()).isEqualTo(runContext);
    assertThat(reportingContext.getConfig()).isEqualTo(config);
    assertThat(reportingContext.getCoverageReader()).isEqualTo(coverageReader);
  }
}
