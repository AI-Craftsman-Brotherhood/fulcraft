package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.DefaultJsonService;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Report writer implementation that generates JSON format reports.
 *
 * <p>This writer produces machine-readable JSON reports suitable for:
 *
 * <ul>
 *   <li>CI/CD pipeline integration
 *   <li>Dashboard visualization tools
 *   <li>Data analysis and aggregation
 *   <li>API responses and tool interoperability
 * </ul>
 *
 * <h2>Output Structure</h2>
 *
 * <p>The generated JSON includes:
 *
 * <ul>
 *   <li>Metadata (runId, projectId, timestamp)
 *   <li>Summary statistics
 *   <li>Coverage metrics
 *   <li>Plugin extension summaries (if provided)
 *   <li>Task results (optional, controlled by configuration)
 * </ul>
 *
 * @see ReportWriterPort
 * @see ReportFormat#JSON
 */
public class JsonReportWriter implements ReportWriterPort {

  private static final String DEFAULT_OUTPUT_DIR = "build/reports/test-generation";

  private static final String DEFAULT_FILENAME = "summary.json";

  private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_INSTANT;

  private final JsonServicePort jsonService;

  private final Path outputDirectory;

  private final String filename;

  private final boolean includeDetails;

  /** Creates a writer with default settings. */
  public JsonReportWriter() {
    this(null, null, true);
  }

  /**
   * Creates a writer with custom settings.
   *
   * @param outputDirectory the directory to write reports to (null for default)
   * @param filename the filename for the report (null for default)
   * @param includeDetails whether to include per-task details in output
   */
  public JsonReportWriter(
      final Path outputDirectory, final String filename, final boolean includeDetails) {
    this.jsonService = new DefaultJsonService();
    this.outputDirectory = outputDirectory;
    this.filename = filename != null ? filename : DEFAULT_FILENAME;
    this.includeDetails = includeDetails;
  }

  /**
   * Creates a writer with a custom JsonServicePort.
   *
   * @param jsonService the JsonServicePort to use for JSON serialization
   */
  public JsonReportWriter(final JsonServicePort jsonService) {
    this.jsonService =
        Objects.requireNonNull(
            jsonService,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "jsonService must not be null"));
    this.outputDirectory = null;
    this.filename = DEFAULT_FILENAME;
    this.includeDetails = true;
  }

  @Override
  public void writeReport(final ReportData data, final Config config) throws ReportWriteException {
    Objects.requireNonNull(
        data,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "data must not be null"));
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "config must not be null"));
    final Path outputPath = resolveOutputPath(config);
    try {
      final Map<String, Object> reportMap = buildReportMap(data);
      ensureDirectoryExists(outputPath);
      jsonService.writeToFile(outputPath, reportMap);
    } catch (IOException e) {
      throw new ReportWriteException(
          MessageSource.getMessage(
              "report.error.write_failed",
              MessageSource.getMessage("report.format.json"),
              outputPath),
          e);
    }
  }

  /**
   * Generates JSON content from the report data.
   *
   * @param data the report data
   * @return the JSON content as a string
   * @throws ReportWriteException if serialization fails
   */
  public String generateJson(final ReportData data) throws ReportWriteException {
    try {
      final Map<String, Object> reportMap = buildReportMap(data);
      return jsonService.toJsonPretty(reportMap);
    } catch (IOException e) {
      throw new ReportWriteException(
          MessageSource.getMessage(
              "report.error.serialize_failed", MessageSource.getMessage("report.format.json")),
          e);
    }
  }

  private Map<String, Object> buildReportMap(final ReportData data) {
    final Map<String, Object> report = new LinkedHashMap<>();
    // Metadata
    report.put("schemaVersion", "1.0");
    report.put("runId", data.getRunId());
    report.put("projectId", data.getProjectId());
    report.put("timestamp", formatTimestamp(data.getTimestamp()));
    report.put("generatedAt", formatTimestamp(Instant.now()));
    // Summary
    report.put("summary", buildSummaryMap(data));
    if (data.getAnalysisHumanSummary() != null && !data.getAnalysisHumanSummary().isBlank()) {
      report.put("analysisHumanSummary", data.getAnalysisHumanSummary());
    }
    // Coverage
    if (data.getLineCoverage() != null || data.getBranchCoverage() != null) {
      report.put("coverage", buildCoverageMap(data));
    }
    // Plugin-specific extensions
    if (!data.getExtensions().isEmpty()) {
      report.put("extensions", data.getExtensions());
    }
    // Execution metadata
    report.put("execution", buildExecutionMap(data));
    // Details (optional)
    if (includeDetails && !data.getTaskResults().isEmpty()) {
      report.put("details", buildDetailsMap(data));
    }
    return report;
  }

  private Map<String, Object> buildSummaryMap(final ReportData data) {
    final Map<String, Object> summary = new LinkedHashMap<>();
    final GenerationSummary genSummary = data.getSummary();
    if (genSummary != null) {
      summary.put("totalTasks", genSummary.getTotalTasks());
      summary.put("succeeded", genSummary.getSucceeded());
      summary.put("failed", genSummary.getFailed());
      summary.put("skipped", genSummary.getSkipped());
      if (genSummary.getSuccessRate() != null) {
        summary.put("successRate", genSummary.getSuccessRate());
      }
      if (genSummary.getSuccessRateDelta() != null) {
        summary.put("successRateDelta", genSummary.getSuccessRateDelta());
      }
      if (genSummary.getErrorCategoryCounts() != null) {
        summary.put("errorCategories", genSummary.getErrorCategoryCounts());
      }
    } else {
      summary.put("classesAnalyzed", data.getTotalClassesAnalyzed());
      summary.put("methodsAnalyzed", data.getTotalMethodsAnalyzed());
      summary.put("selectedTasks", data.getSelectedTasks().size());
      summary.put("excludedTasks", data.getExcludedTaskCount());
    }
    return summary;
  }

  private Map<String, Object> buildCoverageMap(final ReportData data) {
    final Map<String, Object> coverage = new LinkedHashMap<>();
    if (data.getLineCoverage() != null) {
      coverage.put("line", data.getLineCoverage());
    }
    if (data.getBranchCoverage() != null) {
      coverage.put("branch", data.getBranchCoverage());
    }
    return coverage;
  }

  private Map<String, Object> buildExecutionMap(final ReportData data) {
    final Map<String, Object> execution = new LinkedHashMap<>();
    execution.put("durationMs", data.getDurationMs());
    execution.put("hasErrors", data.hasErrors());
    execution.put("hasWarnings", data.hasWarnings());
    if (!data.getErrors().isEmpty()) {
      execution.put("errors", data.getErrors());
    }
    if (!data.getWarnings().isEmpty()) {
      execution.put("warnings", data.getWarnings());
    }
    return execution;
  }

  private Map<String, Object> buildDetailsMap(final ReportData data) {
    final Map<String, Object> details = new LinkedHashMap<>();
    final List<Map<String, Object>> taskList =
        data.getTaskResults().stream().map(this::taskResultToMap).toList();
    details.put("tasks", taskList);
    details.put("taskCount", taskList.size());
    return details;
  }

  private Map<String, Object> taskResultToMap(
      final com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult result) {
    final Map<String, Object> task = new LinkedHashMap<>();
    task.put("taskId", result.getTaskId());
    task.put("classFqn", result.getClassFqn());
    task.put("methodName", result.getMethodName());
    task.put("status", result.getStatus());
    if (result.getErrorMessage() != null) {
      task.put("errorMessage", result.getErrorMessage());
    }
    if (result.getErrorCategory() != null) {
      task.put("errorCategory", result.getErrorCategory());
    }
    if (result.getComplexityStrategy() != null) {
      task.put("complexityStrategy", result.getComplexityStrategy());
    }
    if (result.getGenerationResult() != null) {
      task.put("generationResult", result.getGenerationResult());
    }
    return task;
  }

  private Path resolveOutputPath(final Config config) {
    Path baseDir = outputDirectory;
    if (baseDir == null && config.getProject() != null && config.getProject().getRoot() != null) {
      baseDir = Path.of(config.getProject().getRoot()).resolve(DEFAULT_OUTPUT_DIR);
    } else if (baseDir == null) {
      baseDir = Path.of(DEFAULT_OUTPUT_DIR);
    }
    return baseDir.resolve(filename);
  }

  private void ensureDirectoryExists(final Path path) throws IOException {
    final Path parent = path.getParent();
    if (parent != null && !Files.exists(parent)) {
      Files.createDirectories(parent);
    }
  }

  private String formatTimestamp(final Instant instant) {
    if (instant == null) {
      return null;
    }
    return instant.atZone(ZoneId.systemDefault()).format(ISO_FORMAT);
  }

  /**
   * Returns the report format this writer produces.
   *
   * @return {@link ReportFormat#JSON}
   */
  @Override
  public ReportFormat getFormat() {
    return ReportFormat.JSON;
  }
}
