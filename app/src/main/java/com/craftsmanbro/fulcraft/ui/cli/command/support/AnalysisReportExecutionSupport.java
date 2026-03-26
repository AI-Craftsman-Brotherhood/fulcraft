package com.craftsmanbro.fulcraft.ui.cli.command.support;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.document.adapter.HtmlDocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.reporting.adapter.AnalysisVisualReportWriter;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportingContext;
import com.craftsmanbro.fulcraft.plugins.reporting.core.service.ReportingService;
import com.craftsmanbro.fulcraft.plugins.reporting.io.adapter.JacocoXmlResultAdapter;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Shared support for analysis-report generation paths used by CLI commands. */
public final class AnalysisReportExecutionSupport {

  private AnalysisReportExecutionSupport() {}

  public record ReportOutput(Path outputDirectory, String filenameOverride) {

    public ReportOutput {
      Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
    }
  }

  public static ReportOutput resolveOutputOverride(
      final Path projectRoot, final RunContext context, final Path outputPathOverride) {
    Objects.requireNonNull(projectRoot, "projectRoot must not be null");
    Objects.requireNonNull(context, "context must not be null");
    if (outputPathOverride == null) {
      final Path defaultDir =
          RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
              .reportDir();
      return new ReportOutput(defaultDir, null);
    }
    final Path normalized = outputPathOverride.toAbsolutePath().normalize();
    if (Files.exists(normalized) && Files.isDirectory(normalized)) {
      return new ReportOutput(normalized, null);
    }
    Path parent = normalized.getParent();
    if (parent == null) {
      parent = projectRoot.toAbsolutePath();
    }
    final Path fileName = normalized.getFileName();
    final String filename = fileName != null ? fileName.toString() : null;
    return new ReportOutput(parent, filename);
  }

  public static void generateReports(
      final RunContext context,
      final AnalysisResult result,
      final ReportOutput output,
      final boolean includeHumanReadableSummary,
      final Supplier<ReportData> reportDataSupplier)
      throws IOException, ReportWriteException {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(result, "result must not be null");
    Objects.requireNonNull(output, "output must not be null");
    final Config config = Objects.requireNonNull(context.getConfig(), "config must not be null");
    AnalysisResultContext.set(context, result);
    final Map<ReportFormat, ReportWriterPort> writers =
        createWriters(output.outputDirectory(), output.filenameOverride(), config);
    final ReportingContext reportingContext =
        new ReportingContext(
            context, config, output.outputDirectory(), new JacocoXmlResultAdapter());
    final ReportingService reportingService = new ReportingService(reportingContext);
    if (includeHumanReadableSummary) {
      reportingService.generateHumanReadableAnalysisSummary(null);
    }
    final ReportData reportData =
        reportDataSupplier != null ? reportDataSupplier.get() : ReportData.fromContext(context);
    reportingService.generateAllFormats(reportData, writers);
    if (writers.containsKey(ReportFormat.HTML)) {
      UiLogger.stdout(
          MessageSource.getMessage(
              "analysis.report.generating", result.getClasses().size(), output.outputDirectory()));
      final HtmlDocumentGenerator docGen = new HtmlDocumentGenerator();
      docGen.generate(result, output.outputDirectory(), config);
      final AnalysisVisualReportWriter visualWriter = new AnalysisVisualReportWriter();
      visualWriter.writeReport(result, context, output.outputDirectory(), config);
    }
  }

  private static Map<ReportFormat, ReportWriterPort> createWriters(
      final Path outputDirectory, final String filenameOverride, final Config config) {
    final ReportFormat primaryFormat =
        ReportFormat.fromStringOrDefault(
            config.getOutput().getReportFormat(), ReportFormat.MARKDOWN);
    return AnalysisReportWriters.create(outputDirectory, primaryFormat, filenameOverride);
  }
}
