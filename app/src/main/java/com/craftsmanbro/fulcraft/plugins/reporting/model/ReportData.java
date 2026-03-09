package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodSemantics;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportMetadataKeys;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Main data model for report generation, aggregating all pipeline stage results.
 *
 * <p>This class collects and consolidates data from all phases of the FUL pipeline:
 *
 * <ul>
 *   <li><strong>Analysis Results</strong> - Class and method structure from the ANALYZE phase
 *   <li><strong>Selection Results</strong> - Selected test targets from the SELECT phase
 *   <li><strong>Generation Results</strong> - Per-method test generation outcomes from GENERATE
 *       phase
 *   <li><strong>Verification Results</strong> - Test and quality signals carried into REPORT phase
 *   <li><strong>Coverage Information</strong> - Line/branch coverage from JaCoCo integration
 * </ul>
 *
 * <h2>Usage in Pipeline</h2>
 *
 * <p>This data class is typically populated by the {@code ReportStage} after gathering results from
 * all preceding pipeline stages using the {@link #fromContext(RunContext)} factory method:
 *
 * <pre>
 *   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────────────┐
 *   │ Analyze  │ → │  Select  │ → │ Generate │ → │      Report      │
 *   │  Phase   │   │  Phase   │   │  Phase   │   │      Phase       │
 *   └──────────┘   └──────────┘   └──────────┘   └──────────────────┘
 *        │              │              │                    │
 *        └──────────────┴──────────────┴────────────────────┘
 *                                      │
 *                                      ▼
 *                              ┌────────────────────┐
 *                              │    ReportData      │
 *                              │  (aggregated data) │
 *                              └────────────────────┘
 * </pre>
 *
 * <h2>Builder Pattern</h2>
 *
 * <p>Use the {@link #builder()} method to construct instances:
 *
 * <pre>{@code
 * ReportData data = ReportData.builder()
 *     .summary(summary)
 *     .analysisResult(analysisResult)
 *     .selectedTasks(selectedTasks)
 *     .lineCoverage(0.85)
 *     .branchCoverage(0.72)
 *     .build();
 * }</pre>
 *
 * @see ReportFormat
 * @see com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort
 * @see RunContext
 */
public final class ReportData {

  private static final String UNKNOWN_PROJECT = "unknown";

  private static final String SELECTED_TASKS_KEY = "tasks.selected";

  private static final String GENERATED_TASKS_KEY = "tasks.generated";

  private static final String GENERATION_SUMMARY_KEY = "generation.summary";

  // --- Identification ---
  private final String runId;

  private final String projectId;

  private final Instant timestamp;

  // --- Analysis Phase Results ---
  private final AnalysisResult analysisResult;

  private final String analysisHumanSummary;

  private final int totalClassesAnalyzed;

  private final int totalMethodsAnalyzed;

  // --- Selection Phase Results ---
  private final List<TaskRecord> selectedTasks;

  private final int excludedTaskCount;

  // --- Generation Phase Results ---
  private final GenerationSummary summary;

  private final List<GenerationTaskResult> taskResults;

  private final List<TaskRecord> generatedTests;

  // --- Verification Results ---
  private final List<ReportTaskResult> reportTaskResults;

  private final boolean brittlenessDetected;

  // --- Coverage Information ---
  private final Double lineCoverage;

  private final Double branchCoverage;

  // --- Execution Metadata ---
  private final long durationMs;

  private final List<String> errors;

  private final List<String> warnings;

  private final Map<String, Object> extensions;

  private ReportData(final Builder builder) {
    this.runId = builder.runId;
    this.projectId = builder.projectId;
    this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
    this.analysisResult = builder.analysisResult;
    this.analysisHumanSummary = builder.analysisHumanSummary;
    this.totalClassesAnalyzed = builder.totalClassesAnalyzed;
    this.totalMethodsAnalyzed = builder.totalMethodsAnalyzed;
    this.selectedTasks =
        builder.selectedTasks != null ? List.copyOf(builder.selectedTasks) : List.of();
    this.excludedTaskCount = builder.excludedTaskCount;
    this.summary = builder.summary;
    this.taskResults = builder.taskResults != null ? List.copyOf(builder.taskResults) : List.of();
    this.generatedTests =
        builder.generatedTests != null ? List.copyOf(builder.generatedTests) : List.of();
    this.reportTaskResults =
        builder.reportTaskResults != null ? List.copyOf(builder.reportTaskResults) : List.of();
    this.brittlenessDetected = builder.brittlenessDetected;
    this.lineCoverage = builder.lineCoverage;
    this.branchCoverage = builder.branchCoverage;
    this.durationMs = builder.durationMs;
    this.errors = builder.errors != null ? List.copyOf(builder.errors) : List.of();
    this.warnings = builder.warnings != null ? List.copyOf(builder.warnings) : List.of();
    this.extensions = copyExtensions(builder.extensions);
  }

  // --- Factory Methods ---
  /**
   * Creates a new builder for constructing {@code ReportData} instances.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a {@code ReportData} instance from a {@link RunContext}.
   *
   * <p>This is the primary factory method for creating report data. It extracts all relevant
   * information from the pipeline context including analysis results, selected tasks, generation
   * outcomes, verification results, and execution metadata.
   *
   * @param context the run context containing all pipeline stage results
   * @return a new ReportData instance
   * @throws NullPointerException if context is null
   */
  public static ReportData fromContext(final RunContext context) {
    Objects.requireNonNull(
        context,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "context must not be null"));
    final Builder builder =
        builder()
            .runId(context.getRunId())
            .selectedTasks(getTasks(context, SELECTED_TASKS_KEY))
            .generatedTests(getTasks(context, GENERATED_TASKS_KEY))
            .reportTaskResults(context.getReportTaskResults())
            .brittlenessDetected(context.isBrittlenessDetected())
            .errors(context.getErrors())
            .warnings(context.getWarnings());
    extractProjectId(context, builder);
    extractAnalysisResult(context, builder);
    extractAnalysisSummary(context, builder);
    extractSummary(context, builder);
    extractExtensions(context, builder);
    builder.excludedTaskCount(countExcludedTasks(getTasks(context, SELECTED_TASKS_KEY)));
    return builder.build();
  }

  private static void extractProjectId(final RunContext context, final Builder builder) {
    if (context.getConfig() != null && context.getConfig().getProject() != null) {
      final String projectId = context.getConfig().getProject().getId();
      if (projectId != null && !projectId.isBlank()) {
        builder.projectId(projectId);
        return;
      }
    }
    builder.projectId(UNKNOWN_PROJECT);
  }

  private static void extractAnalysisResult(final RunContext context, final Builder builder) {
    AnalysisResultContext.get(context)
        .ifPresent(
            analysis -> {
              builder.analysisResult(analysis);
              builder.totalClassesAnalyzed(countClasses(analysis));
              builder.totalMethodsAnalyzed(countMethods(analysis));
            });
  }

  private static void extractAnalysisSummary(final RunContext context, final Builder builder) {
    context
        .getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class)
        .filter(summary -> !summary.isBlank())
        .ifPresent(builder::analysisHumanSummary);
  }

  private static int countClasses(final AnalysisResult analysis) {
    return analysis.getClasses() != null ? analysis.getClasses().size() : 0;
  }

  private static int countMethods(final AnalysisResult analysis) {
    if (analysis.getClasses() == null) {
      return 0;
    }
    int count = 0;
    for (final var classInfo : analysis.getClasses()) {
      count += MethodSemantics.countMethodsExcludingImplicitDefaultConstructors(classInfo);
    }
    return count;
  }

  private static void extractSummary(final RunContext context, final Builder builder) {
    final GenerationSummary summary =
        context
            .getMetadata(GENERATION_SUMMARY_KEY, Object.class)
            .map(FeatureModelMapper::toGenerationSummary)
            .orElse(null);
    if (summary == null) {
      return;
    }
    builder
        .summary(summary)
        .taskResults(summary.getDetails())
        .lineCoverage(summary.getLineCoverage())
        .branchCoverage(summary.getBranchCoverage())
        .durationMs(summary.getDurationMs());
  }

  private static void extractExtensions(final RunContext context, final Builder builder) {
    final Object raw =
        context.getMetadata(ReportMetadataKeys.REPORT_EXTENSIONS, Object.class).orElse(null);
    if (raw == null) {
      return;
    }
    builder.extensions(toExtensionMap(raw));
  }

  private static Map<String, Object> toExtensionMap(final Object raw) {
    if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
      return Map.of();
    }
    final Map<String, Object> extensions = new TreeMap<>();
    for (final Map.Entry<?, ?> entry : rawMap.entrySet()) {
      final Object key = entry.getKey();
      if (key instanceof String extensionKey && !extensionKey.isBlank()) {
        extensions.put(extensionKey, entry.getValue());
      }
    }
    if (extensions.isEmpty()) {
      return Map.of();
    }
    return extensions;
  }

  private static Map<String, Object> copyExtensions(final Map<String, Object> input) {
    if (input == null || input.isEmpty()) {
      return Map.of();
    }
    return Map.copyOf(new TreeMap<>(input));
  }

  private static List<TaskRecord> getTasks(final RunContext context, final String key) {
    final Object raw = context.getMetadata(key, Object.class).orElse(null);
    return FeatureModelMapper.toTaskRecords(raw);
  }

  private static int countExcludedTasks(final List<TaskRecord> tasks) {
    int excluded = 0;
    for (final TaskRecord task : tasks) {
      if (!Boolean.TRUE.equals(task.getSelected())) {
        excluded++;
      }
    }
    return excluded;
  }

  /**
   * Creates a {@code ReportData} instance from an existing {@link GenerationSummary}.
   *
   * <p>This is a convenience method for creating minimal report data from just a summary.
   *
   * @param summary the generation summary to convert
   * @return a new ReportData instance
   * @throws NullPointerException if summary is null
   */
  public static ReportData fromSummary(final GenerationSummary summary) {
    Objects.requireNonNull(
        summary,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "summary must not be null"));
    return builder()
        .summary(summary)
        .runId(summary.getRunId())
        .projectId(summary.getProjectId())
        .taskResults(summary.getDetails())
        .lineCoverage(summary.getLineCoverage())
        .branchCoverage(summary.getBranchCoverage())
        .durationMs(summary.getDurationMs())
        .build();
  }

  // --- Getters: Identification ---
  /** Returns the unique run identifier for this pipeline execution. */
  public String getRunId() {
    return runId;
  }

  /** Returns the project identifier. */
  public String getProjectId() {
    return projectId;
  }

  /** Returns the timestamp when this report data was created. */
  public Instant getTimestamp() {
    return timestamp;
  }

  // --- Getters: Analysis Phase ---
  /** Returns the analysis result, if available. */
  public AnalysisResult getAnalysisResult() {
    return analysisResult;
  }

  /** Returns the LLM-generated human-readable analysis summary, if available. */
  public String getAnalysisHumanSummary() {
    return analysisHumanSummary;
  }

  /** Returns the total number of classes analyzed. */
  public int getTotalClassesAnalyzed() {
    return totalClassesAnalyzed;
  }

  /** Returns the total number of methods analyzed. */
  public int getTotalMethodsAnalyzed() {
    return totalMethodsAnalyzed;
  }

  // --- Getters: Selection Phase ---
  /** Returns the list of selected (and excluded) tasks. */
  public List<TaskRecord> getSelectedTasks() {
    return selectedTasks;
  }

  /** Returns the count of tasks that were excluded from generation. */
  public int getExcludedTaskCount() {
    return excludedTaskCount;
  }

  // --- Getters: Generation Phase ---
  /** Returns the generation summary containing overall statistics. */
  public GenerationSummary getSummary() {
    return summary;
  }

  /** Returns the list of per-task generation results. */
  public List<GenerationTaskResult> getTaskResults() {
    return taskResults;
  }

  /** Returns the list of generated test definitions. */
  public List<TaskRecord> getGeneratedTests() {
    return generatedTests;
  }

  // --- Getters: Verification Phase ---
  /** Returns the list of aggregated per-task report outcomes. */
  public List<ReportTaskResult> getReportTaskResults() {
    return reportTaskResults;
  }

  /** Returns whether brittleness was detected in generated tests. */
  public boolean isBrittlenessDetected() {
    return brittlenessDetected;
  }

  // --- Getters: Coverage ---
  /** Returns the line coverage percentage (0.0-1.0 range), or null if not available. */
  public Double getLineCoverage() {
    return lineCoverage;
  }

  /** Returns the branch coverage percentage (0.0-1.0 range), or null if not available. */
  public Double getBranchCoverage() {
    return branchCoverage;
  }

  // --- Getters: Execution Metadata ---
  /** Returns the total pipeline execution duration in milliseconds. */
  public long getDurationMs() {
    return durationMs;
  }

  /** Returns the list of errors encountered during pipeline execution. */
  public List<String> getErrors() {
    return errors;
  }

  /** Returns the list of warnings encountered during pipeline execution. */
  public List<String> getWarnings() {
    return warnings;
  }

  /** Returns plugin-specific reporting extensions keyed by plugin id. */
  public Map<String, Object> getExtensions() {
    return extensions;
  }

  /** Returns whether any errors were encountered. */
  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  /** Returns whether any warnings were encountered. */
  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  // --- Builder ---
  /**
   * Builder for constructing {@code ReportData} instances.
   *
   * <p>All fields are optional except when using specific factory methods that require them.
   */
  public static class Builder {

    private String runId;

    private String projectId;

    private Instant timestamp;

    private AnalysisResult analysisResult;

    private String analysisHumanSummary;

    private int totalClassesAnalyzed;

    private int totalMethodsAnalyzed;

    private List<TaskRecord> selectedTasks;

    private int excludedTaskCount;

    private GenerationSummary summary;

    private List<GenerationTaskResult> taskResults;

    private List<TaskRecord> generatedTests;

    private List<ReportTaskResult> reportTaskResults;

    private boolean brittlenessDetected;

    private Double lineCoverage;

    private Double branchCoverage;

    private long durationMs;

    private List<String> errors;

    private List<String> warnings;

    private Map<String, Object> extensions;

    // --- Identification ---
    public Builder runId(final String runId) {
      this.runId = runId;
      return this;
    }

    public Builder projectId(final String projectId) {
      this.projectId = projectId;
      return this;
    }

    public Builder timestamp(final Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    // --- Analysis Phase ---
    public Builder analysisResult(final AnalysisResult analysisResult) {
      this.analysisResult = analysisResult;
      return this;
    }

    public Builder analysisHumanSummary(final String analysisHumanSummary) {
      this.analysisHumanSummary = analysisHumanSummary;
      return this;
    }

    public Builder totalClassesAnalyzed(final int totalClassesAnalyzed) {
      this.totalClassesAnalyzed = totalClassesAnalyzed;
      return this;
    }

    public Builder totalMethodsAnalyzed(final int totalMethodsAnalyzed) {
      this.totalMethodsAnalyzed = totalMethodsAnalyzed;
      return this;
    }

    // --- Selection Phase ---
    public Builder selectedTasks(final List<TaskRecord> selectedTasks) {
      this.selectedTasks = selectedTasks;
      return this;
    }

    public Builder excludedTaskCount(final int excludedTaskCount) {
      this.excludedTaskCount = excludedTaskCount;
      return this;
    }

    // --- Generation Phase ---
    public Builder summary(final GenerationSummary summary) {
      this.summary = summary;
      return this;
    }

    public Builder taskResults(final List<GenerationTaskResult> taskResults) {
      this.taskResults = taskResults;
      return this;
    }

    public Builder generatedTests(final List<TaskRecord> generatedTests) {
      this.generatedTests = generatedTests;
      return this;
    }

    // --- Verification Phase ---
    public Builder reportTaskResults(final List<ReportTaskResult> reportTaskResults) {
      this.reportTaskResults = reportTaskResults;
      return this;
    }

    public Builder brittlenessDetected(final boolean brittlenessDetected) {
      this.brittlenessDetected = brittlenessDetected;
      return this;
    }

    // --- Coverage ---
    public Builder lineCoverage(final Double lineCoverage) {
      this.lineCoverage = lineCoverage;
      return this;
    }

    public Builder branchCoverage(final Double branchCoverage) {
      this.branchCoverage = branchCoverage;
      return this;
    }

    // --- Execution Metadata ---
    public Builder durationMs(final long durationMs) {
      this.durationMs = durationMs;
      return this;
    }

    public Builder errors(final List<String> errors) {
      this.errors = errors;
      return this;
    }

    public Builder warnings(final List<String> warnings) {
      this.warnings = warnings;
      return this;
    }

    public Builder extensions(final Map<String, Object> extensions) {
      this.extensions = extensions;
      return this;
    }

    /**
     * Builds the {@code ReportData} instance.
     *
     * @return a new ReportData instance
     */
    public ReportData build() {
      return new ReportData(this);
    }
  }
}
