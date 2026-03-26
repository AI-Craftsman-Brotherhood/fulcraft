package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodSemantics;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Report writer implementation that generates Markdown format reports.
 *
 * <p>This writer produces human-readable Markdown reports suitable for:
 *
 * <ul>
 *   <li>Pull request comments and descriptions
 *   <li>README documentation updates
 *   <li>Wiki pages
 *   <li>Email reports (when rendered)
 * </ul>
 *
 * <h2>Output Structure</h2>
 *
 * <p>The generated report includes:
 *
 * <ul>
 *   <li>Header with project and run metadata
 *   <li>Summary table with key metrics
 *   <li>Coverage information (if available)
 *   <li>Flaky test detection results
 *   <li>Brittle test analysis results
 *   <li>Task details (failures, successes, skipped)
 * </ul>
 *
 * @see ReportWriterPort
 * @see ReportFormat#MARKDOWN
 */
public class MarkdownReportWriter implements ReportWriterPort {

  private static final String DEFAULT_OUTPUT_DIR = "build/reports/test-generation";

  private static final String DEFAULT_FILENAME = "report.md";

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private static final String STATUS_FAILURE = "FAILURE";

  private static final String STATUS_SUCCESS = "SUCCESS";

  private static final String STATUS_SKIPPED = "SKIPPED";

  private static final String REPORT_TABLE_TASK_ID_KEY = "report.table.task_id";

  private static final int HIGH_COMPLEXITY_THRESHOLD = 15;

  private static final int TOP_CLASS_LIMIT = 8;

  private static final int TOP_METHOD_LIMIT = 12;

  private final Path outputDirectory;

  private final String filename;

  /** Creates a writer with default output settings. */
  public MarkdownReportWriter() {
    this(null, null);
  }

  /**
   * Creates a writer with custom output settings.
   *
   * @param outputDirectory the directory to write reports to (null for default)
   * @param filename the filename for the report (null for default)
   */
  public MarkdownReportWriter(final Path outputDirectory, final String filename) {
    this.outputDirectory = outputDirectory;
    this.filename = filename != null ? filename : DEFAULT_FILENAME;
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
      final String content = generateMarkdown(data);
      ensureDirectoryExists(outputPath);
      Files.writeString(outputPath, content);
    } catch (IOException e) {
      throw new ReportWriteException(
          MessageSource.getMessage(
              "report.error.write_failed",
              MessageSource.getMessage("report.format.markdown"),
              outputPath),
          e);
    }
  }

  /**
   * Generates Markdown content from the report data.
   *
   * @param data the report data
   * @return the Markdown content as a string
   */
  public String generateMarkdown(final ReportData data) {
    final StringBuilder md = new StringBuilder();
    appendHeader(md, data);
    appendSummaryTable(md, data);
    appendAnalysisSummary(md, data);
    appendAnalysisHotspots(md, data);
    appendCoverage(md, data);
    appendTaskDetails(md, data);
    appendExecutionInfo(md, data);
    return md.toString();
  }

  private void appendHeader(final StringBuilder md, final ReportData data) {
    md.append("# ").append(MessageSource.getMessage("report.title")).append("\n\n");
    md.append("- **")
        .append(MessageSource.getMessage("report.label.project"))
        .append("**: ")
        .append(nullSafe(data.getProjectId()))
        .append("\n");
    md.append("- **")
        .append(MessageSource.getMessage("report.label.run_id"))
        .append("**: `")
        .append(nullSafe(data.getRunId()))
        .append("`\n");
    md.append("- **")
        .append(MessageSource.getMessage("report.label.generated"))
        .append("**: ")
        .append(formatTime(data.getTimestamp()))
        .append("\n");
    if (data.getDurationMs() > 0) {
      md.append("- **")
          .append(MessageSource.getMessage("report.label.duration"))
          .append("**: ")
          .append(formatDuration(data.getDurationMs()))
          .append("\n");
    }
    md.append("\n");
  }

  private void appendSummaryTable(final StringBuilder md, final ReportData data) {
    md.append("## ").append(MessageSource.getMessage("report.section.summary")).append("\n\n");
    md.append("| ")
        .append(MessageSource.getMessage("report.table.metric"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.value"))
        .append(" |\n");
    md.append("|--------|-------|\n");
    final GenerationSummary summary = data.getSummary();
    if (summary != null) {
      md.append("| ")
          .append(MessageSource.getMessage("report.metric.total_tasks"))
          .append(" | ")
          .append(summary.getTotalTasks())
          .append(" |\n");
      md.append("| **")
          .append(MessageSource.getMessage("report.metric.succeeded"))
          .append("** | ")
          .append(summary.getSucceeded())
          .append(" |\n");
      md.append("| **")
          .append(MessageSource.getMessage("report.metric.failed"))
          .append("** | ")
          .append(summary.getFailed())
          .append(" |\n");
      md.append("| ")
          .append(MessageSource.getMessage("report.metric.skipped"))
          .append(" | ")
          .append(summary.getSkipped())
          .append(" |\n");
      if (summary.getSuccessRate() != null) {
        md.append("| ")
            .append(MessageSource.getMessage("report.metric.success_rate"))
            .append(" | ")
            .append(pct(summary.getSuccessRate()))
            .append(" |\n");
      }
    } else {
      md.append("| ")
          .append(MessageSource.getMessage("report.metric.classes"))
          .append(" | ")
          .append(data.getTotalClassesAnalyzed())
          .append(" |\n");
      md.append("| ")
          .append(MessageSource.getMessage("report.metric.methods"))
          .append(" | ")
          .append(data.getTotalMethodsAnalyzed())
          .append(" |\n");
      md.append("| ")
          .append(MessageSource.getMessage("report.metric.selected"))
          .append(" | ")
          .append(data.getSelectedTasks().size())
          .append(" |\n");
      md.append("| ")
          .append(MessageSource.getMessage("report.metric.excluded"))
          .append(" | ")
          .append(data.getExcludedTaskCount())
          .append(" |\n");
    }
    md.append("\n");
  }

  private void appendCoverage(final StringBuilder md, final ReportData data) {
    if (data.getLineCoverage() == null && data.getBranchCoverage() == null) {
      return;
    }
    md.append("## ").append(MessageSource.getMessage("report.section.coverage")).append("\n\n");
    md.append("| ")
        .append(MessageSource.getMessage("report.table.metric"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.value"))
        .append(" |\n");
    md.append("|--------|-------|\n");
    if (data.getLineCoverage() != null) {
      md.append("| ")
          .append(MessageSource.getMessage("report.metric.line_coverage"))
          .append(" | ")
          .append(pct(data.getLineCoverage()))
          .append(" |\n");
    }
    if (data.getBranchCoverage() != null) {
      md.append("| ")
          .append(MessageSource.getMessage("report.metric.branch_coverage"))
          .append(" | ")
          .append(pct(data.getBranchCoverage()))
          .append(" |\n");
    }
    md.append("\n");
  }

  private void appendAnalysisSummary(final StringBuilder md, final ReportData data) {
    final String summary = data.getAnalysisHumanSummary();
    if (summary == null || summary.isBlank()) {
      return;
    }
    md.append("## ")
        .append(MessageSource.getMessage("report.section.analysis_human_summary"))
        .append("\n\n");
    md.append(summary.trim()).append("\n\n");
  }

  private void appendAnalysisHotspots(final StringBuilder md, final ReportData data) {
    final AnalysisResult analysisResult = data.getAnalysisResult();
    if (analysisResult == null
        || analysisResult.getClasses() == null
        || analysisResult.getClasses().isEmpty()) {
      return;
    }
    final List<ClassRisk> classRisks = buildClassRisks(analysisResult.getClasses());
    final List<MethodRisk> methodRisks = buildMethodRisks(analysisResult.getClasses());
    final int highComplexityMethods =
        (int)
            methodRisks.stream()
                .filter(risk -> risk.cyclomaticComplexity() >= HIGH_COMPLEXITY_THRESHOLD)
                .count();
    final int deadCodeCandidates = (int) methodRisks.stream().filter(MethodRisk::deadCode).count();
    final int duplicateCandidates =
        (int) methodRisks.stream().filter(MethodRisk::duplicate).count();
    final int cycleInvolvement = (int) methodRisks.stream().filter(MethodRisk::partOfCycle).count();
    md.append("## ")
        .append(MessageSource.getMessage("report.section.analysis_hotspots"))
        .append("\n\n");
    md.append("| ")
        .append(MessageSource.getMessage("report.table.metric"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.value"))
        .append(" |\n");
    md.append("|--------|-------|\n");
    md.append("| ")
        .append(MessageSource.getMessage("report.metric.high_complexity_methods"))
        .append(" (>= ")
        .append(HIGH_COMPLEXITY_THRESHOLD)
        .append(") | ")
        .append(highComplexityMethods)
        .append(" |\n");
    md.append("| ")
        .append(MessageSource.getMessage("report.metric.dead_code_candidates"))
        .append(" | ")
        .append(deadCodeCandidates)
        .append(" |\n");
    md.append("| ")
        .append(MessageSource.getMessage("report.metric.duplicate_code_candidates"))
        .append(" | ")
        .append(duplicateCandidates)
        .append(" |\n");
    md.append("| ")
        .append(MessageSource.getMessage("report.metric.methods_in_cycles"))
        .append(" | ")
        .append(cycleInvolvement)
        .append(" |\n\n");
    appendClassHotspots(md, classRisks);
    appendMethodHotspots(md, methodRisks);
    appendRecommendations(md, classRisks, methodRisks, highComplexityMethods);
  }

  private void appendClassHotspots(final StringBuilder md, final List<ClassRisk> classRisks) {
    if (classRisks.isEmpty()) {
      return;
    }
    md.append("## ")
        .append(MessageSource.getMessage("report.section.analyzed_classes"))
        .append("\n\n");
    md.append("| ")
        .append(MessageSource.getMessage("report.table.class"))
        .append(" | ")
        .append(MessageSource.getMessage("report.metric.methods"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.loc"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.max_complexity"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.avg_complexity"))
        .append(" |\n");
    md.append("|-------|---------|-----|----------------|----------------|\n");
    for (final ClassRisk risk : classRisks.stream().limit(TOP_CLASS_LIMIT).toList()) {
      md.append("| ")
          .append(formatClassLink(risk))
          .append(" | ")
          .append(risk.methodCount())
          .append(" | ")
          .append(risk.loc())
          .append(" | ")
          .append(risk.maxCyclomaticComplexity())
          .append(" | ")
          .append(formatDecimal(risk.avgCyclomaticComplexity()))
          .append(" |\n");
    }
    md.append("\n");
  }

  private void appendMethodHotspots(final StringBuilder md, final List<MethodRisk> methodRisks) {
    if (methodRisks.isEmpty()) {
      return;
    }
    md.append("## ")
        .append(MessageSource.getMessage("report.section.risky_methods"))
        .append("\n\n");
    md.append("| ")
        .append(MessageSource.getMessage("report.table.class"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.method"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.complexity"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.loc"))
        .append(" |\n");
    md.append("|-------|--------|------------|-----|\n");
    for (final MethodRisk risk : methodRisks.stream().limit(TOP_METHOD_LIMIT).toList()) {
      md.append("| ")
          .append(backtick(risk.className()))
          .append(" | ")
          .append(backtick(risk.methodName()))
          .append(" | ")
          .append(risk.cyclomaticComplexity())
          .append(" | ")
          .append(risk.loc())
          .append(" |\n");
    }
    md.append("\n");
  }

  private void appendRecommendations(
      final StringBuilder md,
      final List<ClassRisk> classRisks,
      final List<MethodRisk> methodRisks,
      final int highComplexityMethods) {
    md.append("## ")
        .append(MessageSource.getMessage("report.section.recommendations"))
        .append("\n\n");
    if (highComplexityMethods > 0) {
      final MethodRisk topMethod = methodRisks.getFirst();
      md.append("- ")
          .append(
              MessageSource.getMessage(
                  "report.recommendation.top_method",
                  topMethod.className(),
                  topMethod.methodName(),
                  topMethod.cyclomaticComplexity()))
          .append("\n");
    } else {
      md.append("- ")
          .append(MessageSource.getMessage("report.recommendation.no_hotspot"))
          .append("\n");
    }
    if (!classRisks.isEmpty()) {
      final ClassRisk topClass = classRisks.getFirst();
      md.append("- ")
          .append(
              MessageSource.getMessage(
                  "report.recommendation.top_class",
                  topClass.className(),
                  topClass.methodCount(),
                  topClass.maxCyclomaticComplexity()))
          .append("\n");
    }
    md.append("- ")
        .append(MessageSource.getMessage("report.recommendation.regression"))
        .append("\n\n");
  }

  private List<ClassRisk> buildClassRisks(final List<ClassInfo> classes) {
    final List<ClassRisk> risks = new ArrayList<>();
    for (final ClassInfo classInfo : classes) {
      if (classInfo == null) {
        continue;
      }
      final List<MethodInfo> methods =
          MethodSemantics.methodsExcludingImplicitDefaultConstructors(classInfo);
      final int methodCount = methods.size();
      int maxComplexity = 0;
      int complexitySum = 0;
      int methodWithMetrics = 0;
      for (final MethodInfo methodInfo : methods) {
        if (methodInfo == null) {
          continue;
        }
        final int complexity = Math.max(0, methodInfo.getCyclomaticComplexity());
        maxComplexity = Math.max(maxComplexity, complexity);
        complexitySum += complexity;
        methodWithMetrics++;
      }
      final double avgComplexity =
          methodWithMetrics > 0 ? (double) complexitySum / methodWithMetrics : 0.0;
      risks.add(
          new ClassRisk(
              normalizeText(classInfo.getFqn()),
              DocumentUtils.generateSourceAlignedReportPath(classInfo, ".html"),
              methodCount,
              Math.max(0, classInfo.getLoc()),
              maxComplexity,
              avgComplexity));
    }
    risks.sort(
        Comparator.comparingInt(ClassRisk::maxCyclomaticComplexity)
            .thenComparingDouble(ClassRisk::avgCyclomaticComplexity)
            .thenComparingInt(ClassRisk::loc)
            .reversed());
    return risks;
  }

  private List<MethodRisk> buildMethodRisks(final List<ClassInfo> classes) {
    final List<MethodRisk> risks = new ArrayList<>();
    for (final ClassInfo classInfo : classes) {
      if (classInfo == null) {
        continue;
      }
      final String className = normalizeText(classInfo.getFqn());
      for (final MethodInfo methodInfo :
          MethodSemantics.methodsExcludingImplicitDefaultConstructors(classInfo)) {
        if (methodInfo == null) {
          continue;
        }
        risks.add(
            new MethodRisk(
                className,
                normalizeText(methodInfo.getName()),
                Math.max(0, methodInfo.getCyclomaticComplexity()),
                Math.max(0, methodInfo.getLoc()),
                methodInfo.isDeadCode(),
                methodInfo.isDuplicate(),
                methodInfo.isPartOfCycle()));
      }
    }
    risks.sort(
        Comparator.comparingInt(MethodRisk::cyclomaticComplexity)
            .thenComparingInt(MethodRisk::loc)
            .reversed());
    return risks;
  }

  private String formatClassLink(final ClassRisk risk) {
    if (risk == null || risk.className() == null || risk.className().isBlank()) {
      return backtick(MessageSource.getMessage("report.value.unknown"));
    }
    final String className = risk.className();
    String reportPath = risk.classReportPath();
    if (reportPath == null || reportPath.isBlank()) {
      reportPath = DocumentUtils.generateFileName(className, ".html");
    }
    return "[" + backtick(className) + "](" + reportPath + ")";
  }

  private String normalizeText(final String value) {
    if (value == null || value.isBlank()) {
      return MessageSource.getMessage("report.value.unknown");
    }
    return value;
  }

  private void appendTaskDetails(final StringBuilder md, final ReportData data) {
    final List<GenerationTaskResult> results = data.getTaskResults();
    if (results.isEmpty()) {
      return;
    }
    appendFailures(md, results);
    appendSuccesses(md, results);
    appendSkipped(md, results);
  }

  private void appendFailures(final StringBuilder md, final List<GenerationTaskResult> results) {
    final var failures =
        results.stream().filter(r -> STATUS_FAILURE.equals(r.getStatus())).toList();
    if (failures.isEmpty()) {
      return;
    }
    md.append("## ")
        .append(MessageSource.getMessage("report.html.details.failures", failures.size()))
        .append("\n\n");
    md.append("| ")
        .append(MessageSource.getMessage(REPORT_TABLE_TASK_ID_KEY))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.class"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.method"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.model"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.tokens"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.error"))
        .append(" |\n");
    md.append("|---------|-------|--------|-------|--------|-------|\n");
    for (final var r : failures) {
      md.append("| ").append(backtick(r.getTaskId())).append(" | ");
      md.append(backtick(r.getClassFqn())).append(" | ");
      md.append(backtick(r.getMethodName())).append(" | ");
      md.append(escape(formatLlmModel(r))).append(" | ");
      md.append(escape(formatTokenUsage(r))).append(" | ");
      md.append(escape(r.getErrorMessage())).append(" |\n");
    }
    md.append("\n");
  }

  private void appendSuccesses(final StringBuilder md, final List<GenerationTaskResult> results) {
    final var successes =
        results.stream().filter(r -> STATUS_SUCCESS.equals(r.getStatus())).toList();
    if (successes.isEmpty()) {
      return;
    }
    md.append("## ")
        .append(MessageSource.getMessage("report.html.details.successes", successes.size()))
        .append("\n\n");
    md.append("<details><summary>")
        .append(MessageSource.getMessage("report.md.details.expand", successes.size()))
        .append("</summary>\n\n");
    md.append("| ")
        .append(MessageSource.getMessage(REPORT_TABLE_TASK_ID_KEY))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.class"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.method"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.model"))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.tokens"))
        .append(" |\n");
    md.append("|---------|-------|--------|-------|--------|\n");
    for (final var r : successes) {
      md.append("| ").append(backtick(r.getTaskId())).append(" | ");
      md.append(backtick(r.getClassFqn())).append(" | ");
      md.append(backtick(r.getMethodName())).append(" | ");
      md.append(escape(formatLlmModel(r))).append(" | ");
      md.append(escape(formatTokenUsage(r))).append(" |\n");
    }
    md.append("\n</details>\n\n");
  }

  private void appendSkipped(final StringBuilder md, final List<GenerationTaskResult> results) {
    final var skipped = results.stream().filter(r -> STATUS_SKIPPED.equals(r.getStatus())).toList();
    if (skipped.isEmpty()) {
      return;
    }
    md.append("## ")
        .append(MessageSource.getMessage("report.html.details.skipped", skipped.size()))
        .append("\n\n");
    md.append("<details><summary>")
        .append(MessageSource.getMessage("report.md.details.expand", skipped.size()))
        .append("</summary>\n\n");
    md.append("| ")
        .append(MessageSource.getMessage(REPORT_TABLE_TASK_ID_KEY))
        .append(" | ")
        .append(MessageSource.getMessage("report.table.reason"))
        .append(" |\n");
    md.append("|---------|--------|\n");
    for (final var r : skipped) {
      md.append("| ").append(backtick(r.getTaskId())).append(" | ");
      md.append(escape(r.getErrorMessage())).append(" |\n");
    }
    md.append("\n</details>\n\n");
  }

  private void appendExecutionInfo(final StringBuilder md, final ReportData data) {
    if (data.hasErrors() || data.hasWarnings()) {
      md.append("## ")
          .append(MessageSource.getMessage("report.section.execution_notes"))
          .append("\n\n");
      if (data.hasErrors()) {
        md.append("### ").append(MessageSource.getMessage("report.section.errors")).append("\n\n");
        for (final String error : data.getErrors()) {
          md.append("- ❌ ").append(escape(error)).append("\n");
        }
        md.append("\n");
      }
      if (data.hasWarnings()) {
        md.append("### ")
            .append(MessageSource.getMessage("report.section.warnings"))
            .append("\n\n");
        for (final String warning : data.getWarnings()) {
          md.append("- ⚠️ ").append(escape(warning)).append("\n");
        }
        md.append("\n");
      }
    }
  }

  private String formatLlmModel(final GenerationTaskResult result) {
    if (result == null || result.getGenerationResult() == null) {
      return "";
    }
    return nullSafe(result.getGenerationResult().getLlmModelUsed().orElse(null));
  }

  private String formatTokenUsage(final GenerationTaskResult result) {
    if (result == null || result.getGenerationResult() == null) {
      return "";
    }
    final int totalTokens = result.getGenerationResult().getTokenUsage();
    return totalTokens > 0 ? String.valueOf(totalTokens) : "";
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

  // === Utility Methods ===
  private String pct(final double value) {
    return formatDecimal(value * 100) + "%";
  }

  private String nullSafe(final String value) {
    return value != null ? value : MessageSource.getMessage("report.value.na");
  }

  private String backtick(final String s) {
    return s == null ? "" : "`" + s + "`";
  }

  private String escape(final String s) {
    if (s == null) {
      return "";
    }
    return s.replace("|", "\\|").replace("\n", " ");
  }

  private String formatTime(final Instant instant) {
    if (instant == null) {
      return MessageSource.getMessage("report.value.na");
    }
    return DATE_FORMAT.format(instant);
  }

  private String formatDuration(final long durationMs) {
    if (durationMs < 1000) {
      return MessageSource.getMessage("report.duration.ms", durationMs);
    } else if (durationMs < 60_000) {
      return MessageSource.getMessage(
          "report.duration.seconds", formatDecimal(durationMs / 1000.0));
    } else {
      final long minutes = durationMs / 60_000;
      final long seconds = (durationMs % 60_000) / 1000;
      return MessageSource.getMessage("report.duration.minutes_seconds", minutes, seconds);
    }
  }

  private String formatDecimal(final double value) {
    java.util.Locale locale = MessageSource.getLocale();
    if (locale == null) {
      locale = java.util.Locale.ROOT;
    }
    return String.format(locale, "%.1f", value);
  }

  /**
   * Returns the report format this writer produces.
   *
   * @return {@link ReportFormat#MARKDOWN}
   */
  @Override
  public ReportFormat getFormat() {
    return ReportFormat.MARKDOWN;
  }

  private record ClassRisk(
      String className,
      String classReportPath,
      int methodCount,
      int loc,
      int maxCyclomaticComplexity,
      double avgCyclomaticComplexity) {}

  private record MethodRisk(
      String className,
      String methodName,
      int cyclomaticComplexity,
      int loc,
      boolean deadCode,
      boolean duplicate,
      boolean partOfCycle) {}
}
