package com.craftsmanbro.fulcraft.plugins.reporting.flow;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.RunDirectories;
import com.craftsmanbro.fulcraft.plugins.reporting.adapter.ReasonAggregatorAdapter;
import com.craftsmanbro.fulcraft.plugins.reporting.adapter.RunSummaryAggregatorAdapter;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportPort;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportMetadataKeys;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportingContext;
import com.craftsmanbro.fulcraft.plugins.reporting.core.model.TasksSnapshot;
import com.craftsmanbro.fulcraft.plugins.reporting.core.service.ReportingService;
import com.craftsmanbro.fulcraft.plugins.reporting.io.CoverageReportLoader;
import com.craftsmanbro.fulcraft.plugins.reporting.io.ReportHistoryStore;
import com.craftsmanbro.fulcraft.plugins.reporting.io.TasksSnapshotReader;
import com.craftsmanbro.fulcraft.plugins.reporting.io.adapter.JacocoXmlResultAdapter;
import com.craftsmanbro.fulcraft.plugins.reporting.io.contract.CoverageReader;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportHistory;
import com.craftsmanbro.fulcraft.plugins.reporting.model.RunSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.DefaultTaskEntriesSource;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import java.util.Map;
import java.util.Objects;

/**
 * Pipeline-level flow that orchestrates report generation.
 *
 * <p>This flow resolves infrastructure dependencies (task snapshots, coverage, history) and
 * delegates business logic to {@link ReportingService}.
 */
public class ReportFlow implements ReportPort {

  private static final String GENERATION_SUMMARY_KEY = "generation.summary";

  private record PreparedReport(GenerationSummary summary, ReportData data) {}

  private final Map<ReportFormat, ReportWriterPort> writersByFormat;

  private final ReportingContext reportingContext;

  private final ReportingService reportingService;

  private final TasksSnapshotReader tasksSnapshotReader;

  private final CoverageReportLoader coverageReportLoader;

  private final ReportHistoryStore reportHistoryStore;

  private final RunSummaryAggregatorAdapter runSummaryAggregatorAdapter;

  private final ReasonAggregatorAdapter reasonAggregatorAdapter;

  private final TaskEntriesSource taskEntriesSource;

  /**
   * Dependencies record to group constructor parameters. This reduces the constructor parameter
   * count while keeping explicit wiring for tests.
   */
  record Dependencies(
      ReportingService reportingService,
      TasksSnapshotReader tasksSnapshotReader,
      CoverageReportLoader coverageReportLoader,
      ReportHistoryStore reportHistoryStore,
      RunSummaryAggregatorAdapter runSummaryAggregatorAdapter,
      ReasonAggregatorAdapter reasonAggregatorAdapter) {

    Dependencies {
      Objects.requireNonNull(
          reportingService,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "report.common.error.argument_null", "reportingService must not be null"));
      Objects.requireNonNull(
          tasksSnapshotReader,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "report.common.error.argument_null", "tasksSnapshotReader must not be null"));
      Objects.requireNonNull(
          coverageReportLoader,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "report.common.error.argument_null", "coverageReportLoader must not be null"));
      Objects.requireNonNull(
          reportHistoryStore,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "report.common.error.argument_null", "reportHistoryStore must not be null"));
      Objects.requireNonNull(
          runSummaryAggregatorAdapter, "runSummaryAggregatorAdapter must not be null");
      Objects.requireNonNull(
          reasonAggregatorAdapter,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "report.common.error.argument_null", "reasonAggregatorAdapter must not be null"));
    }
  }

  /**
   * Creates a new ReportFlow with a single report writer.
   *
   * @param writer the report writer to use
   * @param context the run context containing pipeline results
   * @param config the configuration
   */
  public ReportFlow(final ReportWriterPort writer, final RunContext context, final Config config) {
    this(createSingleWriterMap(writer), context, config, null);
  }

  public ReportFlow(final ReportWriterPort writer, final ReportingContext reportingContext) {
    this(createSingleWriterMap(writer), reportingContext);
  }

  /**
   * Creates a new ReportFlow with multiple format-specific writers.
   *
   * @param writersByFormat map of report format to writer implementations
   * @param context the run context containing pipeline results
   * @param config the configuration
   */
  public ReportFlow(
      final Map<ReportFormat, ReportWriterPort> writersByFormat,
      final RunContext context,
      final Config config) {
    this(writersByFormat, context, config, null);
  }

  /**
   * Creates a new ReportFlow with full configuration.
   *
   * @param writersByFormat map of report format to writer implementations
   * @param context the run context containing pipeline results
   * @param config the configuration
   * @param outputDirectory custom output directory (null for default)
   */
  public ReportFlow(
      final Map<ReportFormat, ReportWriterPort> writersByFormat,
      final RunContext context,
      final Config config,
      final java.nio.file.Path outputDirectory) {
    this(writersByFormat, context, config, outputDirectory, createDefaultCoverageReader());
  }

  public ReportFlow(
      final Map<ReportFormat, ReportWriterPort> writersByFormat,
      final RunContext context,
      final Config config,
      final java.nio.file.Path outputDirectory,
      final CoverageReader coverageReader) {
    this(writersByFormat, new ReportingContext(context, config, outputDirectory, coverageReader));
  }

  public ReportFlow(
      final Map<ReportFormat, ReportWriterPort> writersByFormat,
      final ReportingContext reportingContext) {
    this(writersByFormat, reportingContext, createDefaultDependencies(reportingContext));
  }

  ReportFlow(
      final Map<ReportFormat, ReportWriterPort> writersByFormat,
      final ReportingContext reportingContext,
      final Dependencies dependencies) {
    this.writersByFormat =
        Objects.requireNonNull(
            writersByFormat,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "writersByFormat must not be null"));
    this.reportingContext =
        Objects.requireNonNull(
            reportingContext,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "reportingContext must not be null"));
    final Dependencies deps =
        Objects.requireNonNull(
            dependencies,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "dependencies must not be null"));
    this.reportingService = deps.reportingService();
    this.tasksSnapshotReader = deps.tasksSnapshotReader();
    this.coverageReportLoader = deps.coverageReportLoader();
    this.reportHistoryStore = deps.reportHistoryStore();
    this.runSummaryAggregatorAdapter = deps.runSummaryAggregatorAdapter();
    this.reasonAggregatorAdapter = deps.reasonAggregatorAdapter();
    this.taskEntriesSource = new DefaultTaskEntriesSource();
  }

  /**
   * Generates reports based on the configured format(s).
   *
   * @throws ReportWriteException if report generation or file writing fails
   */
  @Override
  public void generateReports() throws ReportWriteException {
    Logger.info(MessageSource.getMessage("report.flow.generate.start"));
    if (runContext().isDryRun()) {
      Logger.info(MessageSource.getMessage("report.flow.dry_run"));
      return;
    }
    final PreparedReport prepared = prepareReportData();
    reportingService.generateAllFormats(prepared.data(), writersByFormat);
    if (runContext().isShowSummary()) {
      reportingService.printSummary(prepared.summary());
    }
  }

  /**
   * Generates a report in a specific format.
   *
   * @param format the report format to generate
   * @throws ReportWriteException if the format is not supported or writing fails
   */
  @Override
  public void generateReport(final ReportFormat format) throws ReportWriteException {
    final ReportWriterPort writer = writersByFormat.get(format);
    if (writer == null) {
      throw new ReportWriteException(MessageSource.getMessage("report.error.no_writer", format));
    }
    final PreparedReport prepared = prepareReportData();
    writer.writeReport(prepared.data(), config());
    Logger.info(MessageSource.getMessage("report.flow.generated", format.getDefaultFilename()));
  }

  private PreparedReport prepareReportData() {
    final TasksSnapshot snapshot = tasksSnapshotReader.load(runContext());
    final GenerationSummary summary = reportingService.buildSummary(snapshot);
    aggregateRunAndReasonSummaries();
    final java.nio.file.Path historyRoot = resolveHistoryLogsRoot();
    final ReportHistory history = reportHistoryStore.loadHistory(historyRoot);
    final ReportHistory updatedHistory = reportingService.applyTrendAnalysis(summary, history);
    reportHistoryStore.saveHistory(historyRoot, updatedHistory);
    final com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary coverageSummary =
        coverageReportLoader.loadCoverage(
            runContext(), config(), reportingContext.getCoverageReader());
    reportingService.applyCoverageIntegration(summary, coverageSummary);
    reportingService.generateHumanReadableAnalysisSummary(summary);
    runContext().putMetadata(GENERATION_SUMMARY_KEY, summary);
    final ReportData reportData = reportingService.buildReportData(summary);
    return new PreparedReport(summary, reportData);
  }

  @Override
  public java.nio.file.Path getOutputDirectory() {
    return reportingService.getOutputDirectory();
  }

  private static CoverageReader createDefaultCoverageReader() {
    return new JacocoXmlResultAdapter();
  }

  private static Dependencies createDefaultDependencies(final ReportingContext reportingContext) {
    return new Dependencies(
        new ReportingService(reportingContext),
        new TasksSnapshotReader(),
        new CoverageReportLoader(),
        new ReportHistoryStore(),
        new RunSummaryAggregatorAdapter(),
        new ReasonAggregatorAdapter());
  }

  private static Map<ReportFormat, ReportWriterPort> createSingleWriterMap(
      final ReportWriterPort writer) {
    Objects.requireNonNull(
        writer,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "writer must not be null"));
    final Map<ReportFormat, ReportWriterPort> map = new java.util.EnumMap<>(ReportFormat.class);
    final ReportFormat format =
        Objects.requireNonNull(
            writer.getFormat(),
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "writer.getFormat() must not be null"));
    map.put(format, writer);
    return map;
  }

  private RunContext runContext() {
    return reportingContext.getRunContext();
  }

  private Config config() {
    return reportingContext.getConfig();
  }

  private void aggregateRunAndReasonSummaries() {
    final java.nio.file.Path tasksFile = resolveTasksFile();
    if (tasksFile == null) {
      return;
    }
    final com.craftsmanbro.fulcraft.plugins.reporting.model.DynamicSelectionReport dynamicReport =
        runContext()
            .getMetadata(ReportMetadataKeys.DYNAMIC_SELECTION_REPORT, Object.class)
            .map(
                com.craftsmanbro.fulcraft.plugins.reporting.model.FeatureModelMapper
                    ::toDynamicSelectionReport)
            .orElse(null);
    try {
      final RunSummary runSummary =
          runSummaryAggregatorAdapter.aggregate(
              tasksFile, runContext().getRunId(), runContext().isDryRun(), dynamicReport);
      runContext().putMetadata(ReportMetadataKeys.RUN_SUMMARY, runSummary);
    } catch (java.io.IOException e) {
      Logger.warn(
          MessageSource.getMessage("report.flow.aggregate_run_summary_failed", e.getMessage()));
    }
    try {
      final ReasonSummary reasonSummary =
          reasonAggregatorAdapter.aggregate(
              tasksFile, runContext().getRunId(), runContext().isDryRun());
      runContext().putMetadata(ReportMetadataKeys.REASON_SUMMARY, reasonSummary);
    } catch (java.io.IOException e) {
      Logger.warn(
          MessageSource.getMessage("report.flow.aggregate_reason_summary_failed", e.getMessage()));
    }
  }

  private java.nio.file.Path resolveTasksFile() {
    final java.nio.file.Path override =
        runContext()
            .getMetadata(ReportMetadataKeys.TASKS_FILE_OVERRIDE, java.nio.file.Path.class)
            .orElse(null);
    if (override != null) {
      return override;
    }
    final java.nio.file.Path logsDir =
        RunPaths.from(
                runContext().getConfig(), runContext().getProjectRoot(), runContext().getRunId())
            .planDir();
    return taskEntriesSource.resolveExistingTasksFile(logsDir);
  }

  private java.nio.file.Path resolveHistoryLogsRoot() {
    return RunDirectories.resolveRunsRoot(config(), runContext().getProjectRoot());
  }
}
