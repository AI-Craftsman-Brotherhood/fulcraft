package com.craftsmanbro.fulcraft.ui.cli.command.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class AnalysisReportExecutionSupportIntegrationTest {

  @Test
  void resolveOutputOverride_usesDirectoryOverride_whenProvided(@TempDir Path tempDir)
      throws IOException {
    RunContext context = new RunContext(tempDir, new Config(), "run-1");
    Path overrideDir = tempDir.resolve("custom-report");
    Files.createDirectories(overrideDir);

    AnalysisReportExecutionSupport.ReportOutput output =
        AnalysisReportExecutionSupport.resolveOutputOverride(tempDir, context, overrideDir);

    assertThat(output.outputDirectory()).isEqualTo(overrideDir.toAbsolutePath().normalize());
    assertThat(output.filenameOverride()).isNull();
  }

  @Test
  void resolveOutputOverride_usesDefaultReportDirectory_whenOverrideNull(@TempDir Path tempDir) {
    Config config = configWithRunLogsRoot(tempDir, "markdown");
    RunContext context = new RunContext(tempDir, config, "run-1");

    AnalysisReportExecutionSupport.ReportOutput output =
        AnalysisReportExecutionSupport.resolveOutputOverride(tempDir, context, null);

    assertThat(output.outputDirectory())
        .isEqualTo(
            RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
                .reportDir());
    assertThat(output.filenameOverride()).isNull();
  }

  @Test
  void resolveOutputOverride_treatsNonDirectoryPathAsFilenameOverride(@TempDir Path tempDir) {
    RunContext context = new RunContext(tempDir, new Config(), "run-1");
    Path overrideFile = tempDir.resolve("reports").resolve("custom-summary.md");

    AnalysisReportExecutionSupport.ReportOutput output =
        AnalysisReportExecutionSupport.resolveOutputOverride(tempDir, context, overrideFile);

    assertThat(output.outputDirectory())
        .isEqualTo(tempDir.resolve("reports").toAbsolutePath().normalize());
    assertThat(output.filenameOverride()).isEqualTo("custom-summary.md");
  }

  @Test
  void resolveOutputOverride_treatsExistingFileAsFilenameOverride(@TempDir Path tempDir)
      throws IOException {
    RunContext context = new RunContext(tempDir, new Config(), "run-1");
    Path overrideFile = tempDir.resolve("custom-summary.md");
    Files.writeString(overrideFile, "# existing");

    AnalysisReportExecutionSupport.ReportOutput output =
        AnalysisReportExecutionSupport.resolveOutputOverride(tempDir, context, overrideFile);

    assertThat(output.outputDirectory()).isEqualTo(tempDir.toAbsolutePath().normalize());
    assertThat(output.filenameOverride()).isEqualTo("custom-summary.md");
  }

  @Test
  void resolveOutputOverride_requiresNonNullProjectRootAndContext(@TempDir Path tempDir) {
    RunContext context = new RunContext(tempDir, new Config(), "run-1");

    assertThatThrownBy(
            () -> AnalysisReportExecutionSupport.resolveOutputOverride(null, context, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> AnalysisReportExecutionSupport.resolveOutputOverride(tempDir, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void generateReports_writesMarkdownAndVisualOutputs(@TempDir Path tempDir) throws Exception {
    Config config = configWithRunLogsRoot(tempDir, "markdown");
    RunContext context = new RunContext(tempDir, config, "run-1");
    AnalysisResult result = emptyAnalysisResult();
    Path outputDir = tempDir.resolve("reports");

    AnalysisReportExecutionSupport.generateReports(
        context,
        result,
        new AnalysisReportExecutionSupport.ReportOutput(outputDir, null),
        false,
        () ->
            ReportData.builder().runId("run-1").projectId("sample").analysisResult(result).build());

    assertThat(AnalysisResultContext.get(context)).contains(result);
    assertThat(outputDir.resolve("report.md")).exists();
    assertThat(outputDir.resolve("analysis_visual.html")).exists();
  }

  @Test
  void generateReports_usesContextReportData_whenSupplierIsNull(@TempDir Path tempDir)
      throws Exception {
    Config config = configWithRunLogsRoot(tempDir, "json");
    RunContext context = new RunContext(tempDir, config, "run-2");
    AnalysisResult result = emptyAnalysisResult();
    Path outputDir = tempDir.resolve("reports-json");

    AnalysisReportExecutionSupport.generateReports(
        context,
        result,
        new AnalysisReportExecutionSupport.ReportOutput(outputDir, "custom-summary.json"),
        true,
        null);

    assertThat(outputDir.resolve("custom-summary.json")).exists();
    assertThat(outputDir.resolve("analysis_visual.html")).exists();
  }

  @Test
  void generateReports_requiresNonNullArguments(@TempDir Path tempDir) {
    RunContext context = new RunContext(tempDir, new Config(), "run-1");
    AnalysisResult result = emptyAnalysisResult();
    AnalysisReportExecutionSupport.ReportOutput output =
        new AnalysisReportExecutionSupport.ReportOutput(tempDir, null);

    assertThatThrownBy(
            () ->
                AnalysisReportExecutionSupport.generateReports(
                    null, result, output, false, () -> ReportData.fromContext(context)))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                AnalysisReportExecutionSupport.generateReports(
                    context, null, output, false, () -> ReportData.fromContext(context)))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                AnalysisReportExecutionSupport.generateReports(
                    context, result, null, false, () -> ReportData.fromContext(context)))
        .isInstanceOf(NullPointerException.class);
  }

  private static Config configWithRunLogsRoot(Path projectRoot, String reportFormat) {
    Config config = new Config();
    config.setExecution(new Config.ExecutionConfig());
    config.getExecution().setLogsRoot(projectRoot.resolve(".ful/runs").toString());
    config.getOutput().getFormat().setReport(reportFormat);

    Config.ProjectConfig project = new Config.ProjectConfig();
    project.setId("sample");
    project.setRoot(projectRoot.toString());
    config.setProject(project);
    return config;
  }

  private static AnalysisResult emptyAnalysisResult() {
    AnalysisResult result = new AnalysisResult("sample");
    result.setClasses(List.of());
    return result;
  }
}
