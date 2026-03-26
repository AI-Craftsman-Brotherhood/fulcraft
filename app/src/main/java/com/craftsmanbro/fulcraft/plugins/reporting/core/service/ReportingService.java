package com.craftsmanbro.fulcraft.plugins.reporting.core.service;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodSemantics;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportMetadataKeys;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportingContext;
import com.craftsmanbro.fulcraft.plugins.reporting.core.model.TasksSnapshot;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportHistory;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/** Business logic for report generation. */
public class ReportingService {

  private static final String STATUS_SKIPPED = "SKIPPED";

  private static final String STATUS_PROCESSED = "PROCESSED";

  private static final String STATUS_SUCCESS = "SUCCESS";

  private static final String STATUS_FAILURE = "FAILURE";

  private static final String METADATA_START_TIME = "startTime";

  private static final String SELECTED_TASKS_KEY = "tasks.selected";

  private static final String GENERATED_TASKS_KEY = "tasks.generated";

  private static final String GENERATION_SUMMARY_KEY = "generation.summary";

  private static final String KEY_TASK_RESULT_NOT_RECORDED = "report.task.result_not_recorded";

  private static final String KEY_TASK_DEFINITION_NULL = "report.task.definition_null";

  private static final String KEY_UNKNOWN_PROJECT = "report.value.unknown_project";

  private static final String KEY_UNKNOWN_CLASS = "report.value.unknown_class";

  private static final String KEY_UNKNOWN_METHOD = "report.value.unknown_method";

  private static final String LANGUAGE_JA = "ja";

  private static final int HIGH_COMPLEXITY_THRESHOLD = 15;

  private static final int LLM_SUMMARY_CLASS_LIMIT = 5;

  private static final int LLM_SUMMARY_METHOD_LIMIT = 8;

  private static final int LLM_SUMMARY_MAX_CHARS = 3000;

  private final ReportingContext reportingContext;

  private final LlmClientPort llmClient;

  public ReportingService(final ReportingContext reportingContext) {
    this(
        reportingContext,
        createDefaultLlmClient(
            Objects.requireNonNull(
                    reportingContext,
                    MessageSource.getMessage(
                        "report.common.error.argument_null", "reportingContext must not be null"))
                .getConfig()));
  }

  ReportingService(final ReportingContext reportingContext, final LlmClientPort llmClient) {
    this.reportingContext =
        Objects.requireNonNull(
            reportingContext,
            MessageSource.getMessage(
                "report.common.error.argument_null", "reportingContext must not be null"));
    this.llmClient = llmClient;
  }

  public GenerationSummary buildSummary(final TasksSnapshot snapshot) {
    final GenerationSummary summary = new GenerationSummary();
    // Set project metadata
    final String projectId = getProjectId();
    summary.setProjectId(projectId);
    summary.setRunId(runContext().getRunId());
    summary.setTimestamp(Instant.now().toEpochMilli());
    final TasksSnapshot resolvedSnapshot = snapshot != null ? snapshot : TasksSnapshot.empty();
    List<TaskRecord> tasks = new ArrayList<>(resolvedSnapshot.tasks());
    if (tasks.isEmpty()) {
      tasks = getTasks();
    }
    summary.setTotalTasks(tasks.size());
    final List<GenerationTaskResult> details = buildTaskResults(tasks);
    summary.setDetails(details);
    // Calculate statistics
    calculateStatistics(summary, details);
    // Calculate metadata (duration and generic counters)
    calculateMetadata(summary);
    return summary;
  }

  public ReportHistory applyTrendAnalysis(
      final GenerationSummary summary, final ReportHistory history) {
    final com.craftsmanbro.fulcraft.plugins.reporting.core.service.quality.TrendAnalyzer
        trendAnalyzer =
            new com.craftsmanbro.fulcraft.plugins.reporting.core.service.quality.TrendAnalyzer();
    return trendAnalyzer.updateSummaryWithTrend(summary, history);
  }

  public void applyCoverageIntegration(
      final GenerationSummary summary,
      final com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary coverageSummary) {
    final com.craftsmanbro.fulcraft.plugins.reporting.core.service.quality.CoverageIntegrator
        coverageIntegrator =
            new com.craftsmanbro.fulcraft.plugins.reporting.core.service.quality
                .CoverageIntegrator();
    coverageIntegrator.updateSummaryWithCoverage(summary, coverageSummary);
  }

  public ReportData buildReportData(final GenerationSummary summary) {
    runContext().putMetadata(GENERATION_SUMMARY_KEY, summary);
    return ReportData.fromContext(runContext());
  }

  /**
   * Generates an LLM-based, human-readable summary for analysis results when LLM is configured.
   *
   * <p>Failures are downgraded to warnings so that report generation can continue.
   */
  public void generateHumanReadableAnalysisSummary(final GenerationSummary generationSummary) {
    runContext().removeMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY);
    if (!isLlmSummaryEnabled()) {
      return;
    }
    final AnalysisResult analysisResult =
        com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext.get(runContext())
            .orElse(null);
    if (analysisResult == null || analysisResult.getClasses().isEmpty()) {
      return;
    }
    final String prompt = buildAnalysisSummaryPrompt(analysisResult, generationSummary);
    if (prompt.isBlank()) {
      return;
    }
    try {
      final String raw = llmClient.generateTest(prompt, config().getLlm());
      final String normalized = normalizeSummaryText(raw);
      if (!normalized.isBlank()) {
        runContext().putMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, normalized);
      }
    } catch (RuntimeException e) {
      final String warning =
          MessageSource.getMessage("report.analysis_summary.failed", e.getMessage());
      Logger.warn(warning);
      runContext().addWarning(warning);
    } finally {
      llmClient.clearContext();
    }
  }

  public void generateAllFormats(
      final ReportData reportData, final Map<ReportFormat, ReportWriterPort> writersByFormat)
      throws ReportWriteException {
    final Path outputDir = getOutputDirectory();
    // Determine which format to use from config
    final ReportFormat primaryFormat = getConfiguredFormat();
    final ReportWriterPort writer = writersByFormat.get(primaryFormat);
    if (writer == null) {
      if (writersByFormat.isEmpty()) {
        throw new ReportWriteException(MessageSource.getMessage("report.error.no_writers"));
      }
      throw new ReportWriteException(
          MessageSource.getMessage("report.error.no_writer", primaryFormat)
              + ". "
              + writersByFormat.keySet());
    }
    writer.writeReport(reportData, config());
    Logger.info(
        MessageSource.getMessage(
            "report.service.written", outputDir.resolve(primaryFormat.getDefaultFilename())));
  }

  public void printSummary(final GenerationSummary summary) {
    Logger.stdout(MessageSource.getMessage("report.console.blank_line"));
    Logger.stdout(MessageSource.getMessage("report.console.header"));
    Logger.stdout(MessageSource.getMessage("report.console.project", summary.getProjectId()));
    Logger.stdout(MessageSource.getMessage("report.console.run_id", summary.getRunId()));
    Logger.stdout(MessageSource.getMessage("report.console.total_tasks", summary.getTotalTasks()));
    int processed = 0;
    int skipped = 0;
    for (final GenerationTaskResult detail : summary.getDetails()) {
      if (detail == null) {
        continue;
      }
      final String status = detail.getStatus();
      if (STATUS_SKIPPED.equals(status)) {
        skipped++;
      } else if (STATUS_SUCCESS.equals(status)
          || STATUS_FAILURE.equals(status)
          || STATUS_PROCESSED.equals(status)) {
        processed++;
      }
    }
    Logger.stdout(MessageSource.getMessage("report.console.processed", processed));
    Logger.stdout(MessageSource.getMessage("report.console.skipped", skipped));
    if (summary.getSucceeded() > 0 || summary.getFailed() > 0) {
      Logger.stdout(MessageSource.getMessage("report.console.succeeded", summary.getSucceeded()));
      Logger.stdout(MessageSource.getMessage("report.console.failed", summary.getFailed()));
    }
    if (summary.getLineCoverage() != null) {
      Logger.stdout(
          MessageSource.getMessage(
              "report.console.line_coverage",
              String.format("%.1f%%", summary.getLineCoverage() * 100)));
    }
    if (summary.getBranchCoverage() != null) {
      Logger.stdout(
          MessageSource.getMessage(
              "report.console.branch_coverage",
              String.format("%.1f%%", summary.getBranchCoverage() * 100)));
    }
    final long durationMs = summary.getDurationMs();
    if (durationMs > 0) {
      Logger.stdout(
          MessageSource.getMessage(
              "report.console.duration", String.format("%.2fs", durationMs / 1000.0)));
    }
    Logger.stdout(MessageSource.getMessage("report.console.separator"));
  }

  public Path getOutputDirectory() {
    return reportingContext.resolveOutputDirectory();
  }

  private RunContext runContext() {
    return reportingContext.getRunContext();
  }

  private Config config() {
    return reportingContext.getConfig();
  }

  private boolean isLlmSummaryEnabled() {
    return llmClient != null && hasLlmConfiguration(config());
  }

  private static boolean hasLlmConfiguration(final Config config) {
    return config != null
        && config.getLlm() != null
        && StringUtils.isNotBlank(config.getLlm().getProvider());
  }

  private static LlmClientPort createDefaultLlmClient(final Config config) {
    if (!hasLlmConfiguration(config)) {
      return null;
    }
    final com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort infrastructureClient =
        new com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator
            .LlmGovernanceEnforcingClient(
            new com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.OverrideAwareLlmClient(
                config.getLlm()),
            config.getGovernance());
    return com.craftsmanbro.fulcraft.plugins.analysis.adapter.llm.LlmContractAdapter.toFeature(
        infrastructureClient);
  }

  private String getProjectId() {
    if (config().getProject() != null && config().getProject().getId() != null) {
      return config().getProject().getId();
    }
    return MessageSource.getMessage(KEY_UNKNOWN_PROJECT);
  }

  private String buildAnalysisSummaryPrompt(
      final AnalysisResult analysisResult, final GenerationSummary generationSummary) {
    final List<ClassRisk> classRisks = collectClassRisks(analysisResult);
    final List<MethodRisk> methodRisks = collectMethodRisks(analysisResult);
    final int classCount = analysisResult.getClasses().size();
    final int methodCount = methodRisks.size();
    final long highComplexityCount =
        methodRisks.stream()
            .filter(risk -> risk.cyclomaticComplexity() >= HIGH_COMPLEXITY_THRESHOLD)
            .count();
    final StringBuilder prompt = new StringBuilder(2200);
    prompt.append("You are a senior software architect.\n");
    prompt.append(
        isJapaneseLocale()
            ? "必ず日本語で、開発者に伝わる自然な文章で要約してください。\n"
            : "Write the summary in clear English for software engineers.\n");
    prompt.append("Summarize the static analysis result in a human-friendly way.\n");
    prompt.append("Output requirements:\n");
    prompt.append("1) Start with one overall assessment sentence.\n");
    prompt.append("2) Add 3 to 5 bullet points about technical hotspots and risks.\n");
    prompt.append("3) End with exactly 2 actionable testing recommendations.\n");
    prompt.append("4) Do not use code blocks.\n\n");
    prompt.append("Project: ").append(getProjectId()).append('\n');
    prompt.append("Analyzed classes: ").append(classCount).append('\n');
    prompt.append("Analyzed methods: ").append(methodCount).append('\n');
    prompt
        .append("High complexity methods (>= ")
        .append(HIGH_COMPLEXITY_THRESHOLD)
        .append("): ")
        .append(highComplexityCount)
        .append('\n');
    if (generationSummary != null) {
      prompt
          .append("Generation total tasks: ")
          .append(generationSummary.getTotalTasks())
          .append('\n');
      prompt.append("Generation succeeded: ").append(generationSummary.getSucceeded()).append('\n');
      prompt.append("Generation failed: ").append(generationSummary.getFailed()).append('\n');
      prompt.append("Generation skipped: ").append(generationSummary.getSkipped()).append('\n');
      if (generationSummary.getSuccessRate() != null) {
        prompt
            .append("Generation success rate: ")
            .append(generationSummary.getSuccessRate())
            .append('\n');
      }
    }
    prompt.append("\nTop complex classes:\n");
    for (final ClassRisk risk : classRisks.stream().limit(LLM_SUMMARY_CLASS_LIMIT).toList()) {
      prompt
          .append("- ")
          .append(risk.className())
          .append(" (methods=")
          .append(risk.methodCount())
          .append(", maxCC=")
          .append(risk.maxCyclomaticComplexity())
          .append(", avgCC=")
          .append(String.format(java.util.Locale.ROOT, "%.1f", risk.avgCyclomaticComplexity()))
          .append(", loc=")
          .append(risk.loc())
          .append(")\n");
    }
    prompt.append("\nTop complex methods:\n");
    for (final MethodRisk risk : methodRisks.stream().limit(LLM_SUMMARY_METHOD_LIMIT).toList()) {
      prompt
          .append("- ")
          .append(risk.className())
          .append("#")
          .append(risk.methodName())
          .append(" (CC=")
          .append(risk.cyclomaticComplexity())
          .append(", loc=")
          .append(risk.loc())
          .append(")\n");
    }
    return prompt.toString();
  }

  private List<ClassRisk> collectClassRisks(final AnalysisResult analysisResult) {
    final List<ClassRisk> risks = new ArrayList<>();
    for (final ClassInfo classInfo : analysisResult.getClasses()) {
      if (classInfo == null) {
        continue;
      }
      final List<MethodInfo> methods =
          MethodSemantics.methodsExcludingImplicitDefaultConstructors(classInfo);
      final int methodCount = methods.size();
      int maxComplexity = 0;
      int complexitySum = 0;
      for (final MethodInfo method : methods) {
        if (method == null) {
          continue;
        }
        final int complexity = Math.max(0, method.getCyclomaticComplexity());
        maxComplexity = Math.max(maxComplexity, complexity);
        complexitySum += complexity;
      }
      final double avgComplexity = methodCount > 0 ? (double) complexitySum / methodCount : 0.0;
      risks.add(
          new ClassRisk(
              normalizeText(classInfo.getFqn(), MessageSource.getMessage(KEY_UNKNOWN_CLASS)),
              methodCount,
              maxComplexity,
              avgComplexity,
              Math.max(0, classInfo.getLoc())));
    }
    risks.sort(
        Comparator.comparingInt(ClassRisk::maxCyclomaticComplexity)
            .thenComparingDouble(ClassRisk::avgCyclomaticComplexity)
            .thenComparingInt(ClassRisk::methodCount)
            .reversed());
    return risks;
  }

  private List<MethodRisk> collectMethodRisks(final AnalysisResult analysisResult) {
    final List<MethodRisk> risks = new ArrayList<>();
    for (final ClassInfo classInfo : analysisResult.getClasses()) {
      if (classInfo == null) {
        continue;
      }
      final String className =
          normalizeText(classInfo.getFqn(), MessageSource.getMessage(KEY_UNKNOWN_CLASS));
      for (final MethodInfo methodInfo :
          MethodSemantics.methodsExcludingImplicitDefaultConstructors(classInfo)) {
        if (methodInfo == null) {
          continue;
        }
        risks.add(
            new MethodRisk(
                className,
                normalizeText(methodInfo.getName(), MessageSource.getMessage(KEY_UNKNOWN_METHOD)),
                Math.max(0, methodInfo.getCyclomaticComplexity()),
                Math.max(0, methodInfo.getLoc())));
      }
    }
    risks.sort(
        Comparator.comparingInt(MethodRisk::cyclomaticComplexity)
            .thenComparingInt(MethodRisk::loc)
            .reversed());
    return risks;
  }

  private String normalizeSummaryText(final String raw) {
    if (raw == null) {
      return "";
    }
    String text = raw.trim().replace("\r\n", "\n");
    if (text.startsWith("```")) {
      final int firstLineEnd = text.indexOf('\n');
      final int lastFence = text.lastIndexOf("```");
      if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
        text = text.substring(firstLineEnd + 1, lastFence).trim();
      }
    }
    if (text.length() > LLM_SUMMARY_MAX_CHARS) {
      text = text.substring(0, LLM_SUMMARY_MAX_CHARS).trim();
    }
    return text;
  }

  private boolean isJapaneseLocale() {
    return LANGUAGE_JA.equalsIgnoreCase(MessageSource.getLocale().getLanguage());
  }

  private String normalizeText(final String value, final String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value;
  }

  private List<TaskRecord> getTasks() {
    List<TaskRecord> tasks = getTasksFromMetadata(GENERATED_TASKS_KEY);
    if (tasks.isEmpty()) {
      tasks = getTasksFromMetadata(SELECTED_TASKS_KEY);
    }
    return tasks;
  }

  private List<TaskRecord> getTasksFromMetadata(final String key) {
    final Object raw = runContext().getMetadata(key, Object.class).orElse(null);
    return com.craftsmanbro.fulcraft.plugins.reporting.model.FeatureModelMapper.toTaskRecords(raw);
  }

  private List<GenerationTaskResult> buildTaskResults(final List<TaskRecord> tasks) {
    final List<GenerationTaskResult> details = new ArrayList<>();
    for (final TaskRecord task : tasks) {
      final GenerationTaskResult result = new GenerationTaskResult();
      if (task == null) {
        result.setStatus(STATUS_SKIPPED);
        result.setErrorMessage(MessageSource.getMessage(KEY_TASK_DEFINITION_NULL));
        details.add(result);
        continue;
      }
      result.setTaskId(task.getTaskId());
      result.setClassFqn(task.getClassFqn());
      result.setMethodName(task.getMethodName());
      result.setComplexityStrategy(task.getComplexityStrategy());
      result.setHighComplexity(task.getHighComplexity());
      if (Boolean.TRUE.equals(task.getSelected())) {
        result.setStatus(STATUS_SKIPPED);
        result.setErrorMessage(MessageSource.getMessage(KEY_TASK_RESULT_NOT_RECORDED));
      } else {
        result.setStatus(STATUS_SKIPPED);
        result.setErrorMessage(task.getExclusionReason());
      }
      details.add(result);
    }
    return details;
  }

  private void calculateStatistics(
      final GenerationSummary summary, final List<GenerationTaskResult> details) {
    final long skipped = details.stream().filter(d -> STATUS_SKIPPED.equals(d.getStatus())).count();
    summary.setSkipped((int) skipped);
    final long succeeded =
        details.stream().filter(d -> STATUS_SUCCESS.equals(d.getStatus())).count();
    final long failed =
        details.stream()
            .filter(
                d -> STATUS_FAILURE.equals(d.getStatus()) || STATUS_PROCESSED.equals(d.getStatus()))
            .count();
    final long totalProcessed = succeeded + failed;
    if (totalProcessed > 0) {
      summary.setSucceeded((int) succeeded);
      summary.setFailed((int) failed);
      summary.setSuccessRate((double) succeeded / totalProcessed);
    }
    // Aggregate error categories
    final Map<String, Integer> errorCounts = new java.util.HashMap<>();
    for (final GenerationTaskResult detail : details) {
      final String category = detail.getErrorCategory();
      if (category != null && !category.isEmpty()) {
        errorCounts.merge(category, 1, (a, b) -> a + b);
      }
    }
    if (!errorCounts.isEmpty()) {
      summary.setErrorCategoryCounts(errorCounts);
    }
  }

  private void calculateMetadata(final GenerationSummary summary) {
    // Calculate duration
    final Long startTime = runContext().getMetadata(METADATA_START_TIME, Long.class).orElse(null);
    if (startTime != null) {
      summary.setDurationMs(Instant.now().toEpochMilli() - startTime);
    }
  }

  private ReportFormat getConfiguredFormat() {
    if (config().getOutput() != null) {
      final String formatStr = config().getOutput().getReportFormat();
      return ReportFormat.fromStringOrDefault(formatStr, ReportFormat.MARKDOWN);
    }
    return ReportFormat.MARKDOWN;
  }

  private record ClassRisk(
      String className,
      int methodCount,
      int maxCyclomaticComplexity,
      double avgCyclomaticComplexity,
      int loc) {}

  private record MethodRisk(
      String className, String methodName, int cyclomaticComplexity, int loc) {}
}
