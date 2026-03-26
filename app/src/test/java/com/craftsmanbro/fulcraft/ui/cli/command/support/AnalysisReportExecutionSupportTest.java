package com.craftsmanbro.fulcraft.ui.cli.command.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class AnalysisReportExecutionSupportTest {

  private static final String RUN_ID = "run-123";

  @TempDir Path tempDir;

  @Test
  void reportOutput_requiresNonNullOutputDirectory() {
    assertThatThrownBy(() -> new AnalysisReportExecutionSupport.ReportOutput(null, "report.json"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("outputDirectory must not be null");
  }

  @Test
  void resolveOutputOverride_requiresNonNullArguments() {
    Config config = Config.createDefault();
    RunContext context = new RunContext(tempDir, config, RUN_ID);

    assertThatThrownBy(
            () -> AnalysisReportExecutionSupport.resolveOutputOverride(null, context, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("projectRoot must not be null");
    assertThatThrownBy(
            () -> AnalysisReportExecutionSupport.resolveOutputOverride(tempDir, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("context must not be null");
  }

  @Test
  void resolveOutputOverride_usesRunReportDirectory_whenOverrideIsAbsent() {
    Config config = Config.createDefault();
    RunContext context = new RunContext(tempDir, config, RUN_ID);

    AnalysisReportExecutionSupport.ReportOutput output =
        AnalysisReportExecutionSupport.resolveOutputOverride(tempDir, context, null);

    assertThat(output.outputDirectory())
        .isEqualTo(RunPaths.from(config, tempDir, RUN_ID).reportDir());
    assertThat(output.filenameOverride()).isNull();
  }

  @Test
  void resolveOutputOverride_usesExistingDirectoryOverride() throws IOException {
    Config config = Config.createDefault();
    RunContext context = new RunContext(tempDir, config, RUN_ID);
    Path outputDir = Files.createDirectories(tempDir.resolve("custom-report-dir"));

    AnalysisReportExecutionSupport.ReportOutput output =
        AnalysisReportExecutionSupport.resolveOutputOverride(tempDir, context, outputDir);

    assertThat(output.outputDirectory()).isEqualTo(outputDir);
    assertThat(output.filenameOverride()).isNull();
  }

  @Test
  void resolveOutputOverride_splitsFileOverrideIntoParentDirectoryAndFilename() {
    Config config = Config.createDefault();
    RunContext context = new RunContext(tempDir, config, RUN_ID);
    Path outputFile = tempDir.resolve("exports").resolve("custom-summary.json");

    AnalysisReportExecutionSupport.ReportOutput output =
        AnalysisReportExecutionSupport.resolveOutputOverride(tempDir, context, outputFile);

    assertThat(output.outputDirectory()).isEqualTo(outputFile.getParent());
    assertThat(output.filenameOverride()).isEqualTo("custom-summary.json");
  }

  @Test
  void generateReports_requiresNonNullArguments() {
    Config config = Config.createDefault();
    RunContext context = new RunContext(tempDir, config, RUN_ID);
    AnalysisResult result = new AnalysisResult();
    AnalysisReportExecutionSupport.ReportOutput output =
        new AnalysisReportExecutionSupport.ReportOutput(tempDir, null);

    assertThatThrownBy(
            () -> AnalysisReportExecutionSupport.generateReports(null, result, output, false, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("context must not be null");
    assertThatThrownBy(
            () ->
                AnalysisReportExecutionSupport.generateReports(context, null, output, false, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("result must not be null");
    assertThatThrownBy(
            () ->
                AnalysisReportExecutionSupport.generateReports(context, result, null, false, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("output must not be null");
  }
}
