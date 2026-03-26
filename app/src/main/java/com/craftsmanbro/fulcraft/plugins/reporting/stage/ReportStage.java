package com.craftsmanbro.fulcraft.plugins.reporting.stage;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportMetadataKeys;
import com.craftsmanbro.fulcraft.plugins.reporting.flow.ReportFlow;
import com.craftsmanbro.fulcraft.plugins.reporting.io.TasksFileLoader;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.DefaultTaskEntriesSource;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Stage that orchestrates report generation from test generation results.
 *
 * <p>This stage is a thin orchestrator that delegates all report generation logic to {@link
 * ReportFlow}. Its responsibilities are limited to:
 *
 * <ul>
 *   <li>Validating preconditions (context, project root)
 *   <li>Creating the ReportFlow with the current context
 *   <li>Handling dry-run mode
 *   <li>Delegating to the report generation service
 *   <li>Storing report metadata in the context
 *   <li>Logging CLI-friendly output messages
 * </ul>
 *
 * <h2>Pipeline Position</h2>
 *
 * <pre>
 *   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────────────┐
 *   │ ANALYZE  │ → │  SELECT  │ → │ GENERATE │ → │     REPORT       │
 *   │  Stage   │   │  Stage   │   │  Stage   │   │     Stage        │
 *   └──────────┘   └──────────┘   └──────────┘   └────────┬─────────┘
 *                                                          │
 *                                                          ▼
 *                                             ┌────────────────────────┐
 *                                             │       ReportFlow       │
 *                                             │   (entry point)        │
 *                                             └────────────────────────┘
 * </pre>
 *
 * <h2>Metadata Keys</h2>
 *
 * <p>This stage stores the following metadata in the context:
 *
 * <dl>
 *   <dt>{@code reportOutputDirectory}
 *   <dd>Path to the directory where reports were written
 *   <dt>{@code reportGenerated}
 *   <dd>Boolean indicating whether reports were successfully generated
 * </dl>
 *
 * @see ReportFlow
 * @see com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort
 */
public class ReportStage implements Stage {

  /** Metadata key for the report output directory path. */
  public static final String METADATA_REPORT_OUTPUT_DIR = "reportOutputDirectory";

  /** Metadata key indicating whether reports were generated. */
  public static final String METADATA_REPORT_GENERATED = "reportGenerated";

  private final BiFunction<RunContext, Config, ReportFlow> serviceFactory;

  private final Config config;

  private final TaskEntriesSource taskEntriesSource;

  // For testing: allow injecting a pre-built service
  private ReportFlow injectedService;

  private ReportFlow createdService;

  /**
   * Creates a ReportStage with the specified configuration.
   *
   * <p>The ReportFlow will be created at execution time using the RunContext.
   *
   * @param config the application configuration
   * @throws NullPointerException if config is null
   */
  public ReportStage(final Config config) {
    this.config =
        Objects.requireNonNull(
            config,
            MessageSource.getMessage("report.common.error.argument_null", "Config cannot be null"));
    this.serviceFactory = ReportStage::createDefaultService;
    this.taskEntriesSource = new DefaultTaskEntriesSource();
  }

  /**
   * Creates a ReportStage with a custom service factory.
   *
   * <p>This constructor allows for dependency injection of the service creation logic, useful for
   * testing or custom configurations.
   *
   * @param config the application configuration
   * @param serviceFactory a factory function that creates the ReportFlow
   * @throws NullPointerException if either argument is null
   */
  public ReportStage(
      final Config config, final BiFunction<RunContext, Config, ReportFlow> serviceFactory) {
    this.config =
        Objects.requireNonNull(
            config,
            MessageSource.getMessage("report.common.error.argument_null", "Config cannot be null"));
    this.serviceFactory =
        Objects.requireNonNull(
            serviceFactory,
            MessageSource.getMessage(
                "report.common.error.argument_null", "ServiceFactory cannot be null"));
    this.taskEntriesSource = new DefaultTaskEntriesSource();
  }

  /**
   * Creates a ReportStage with a pre-built flow (for testing).
   *
   * @param reportFlow the service to use
   * @throws NullPointerException if reportFlow is null
   */
  public ReportStage(final ReportFlow reportFlow) {
    Objects.requireNonNull(
        reportFlow,
        MessageSource.getMessage("report.common.error.argument_null", "ReportFlow cannot be null"));
    this.injectedService = reportFlow;
    this.config = null;
    this.serviceFactory = null;
    this.taskEntriesSource = new DefaultTaskEntriesSource();
  }

  @Override
  public String getNodeId() {
    return PipelineNodeIds.REPORT;
  }

  @Override
  public void execute(final RunContext context) throws StageException {
    Objects.requireNonNull(
        context,
        MessageSource.getMessage("report.common.error.argument_null", "RunContext cannot be null"));
    Objects.requireNonNull(
        context.getProjectRoot(),
        MessageSource.getMessage(
            "report.common.error.argument_null", "projectRoot cannot be null"));
    Logger.info(MessageSource.getMessage("report.stage.start"));
    // Handle dry-run mode
    if (context.isDryRun()) {
      Logger.info(MessageSource.getMessage("report.stage.dry_run"));
      context.putMetadata(METADATA_REPORT_GENERATED, false);
      return;
    }
    try {
      // Get or create the service
      final ReportFlow service = getOrCreateService(context);
      // Load aggregated test results if missing
      loadTestResultsIfMissing(context);
      // Delegate to the report generation service
      service.generateReports();
      final Path outputDirectory = service.getOutputDirectory();
      generateSourceLevelHtmlReports(context, outputDirectory);
      // Store metadata about generated reports
      context.putMetadata(METADATA_REPORT_OUTPUT_DIR, outputDirectory.toString());
      context.putMetadata(METADATA_REPORT_GENERATED, true);
      // Log completion message for CLI
      Logger.info(MessageSource.getMessage("report.stage.completed"));
      Logger.info(MessageSource.getMessage("report.stage.output_dir", outputDirectory));
    } catch (Exception e) {
      context.putMetadata(METADATA_REPORT_GENERATED, false);
      if (e instanceof ReportWriteException) {
        throw new StageException(
            PipelineNodeIds.REPORT,
            MessageSource.getMessage("report.stage.error.generation_failed", e.getMessage()),
            e);
      }
      throw new StageException(
          PipelineNodeIds.REPORT,
          MessageSource.getMessage(
              "report.stage.error.generation_failed_unexpected", e.getClass().getSimpleName()),
          e);
    }
  }

  @Override
  public String getName() {
    return "Report";
  }

  /**
   * Returns the underlying report generation flow.
   *
   * <p>Note: This may return null if called before execute(), as the service is created lazily.
   *
   * @return the report generation flow, or null if not yet created
   */
  public ReportFlow getReportFlow() {
    if (injectedService != null) {
      return injectedService;
    }
    return createdService;
  }

  private ReportFlow getOrCreateService(final RunContext context) {
    if (injectedService != null) {
      return injectedService;
    }
    final Config resolvedConfig = config != null ? config : context.getConfig();
    if (createdService == null) {
      createdService = serviceFactory.apply(context, resolvedConfig);
    }
    return createdService;
  }

  private void loadTestResultsIfMissing(final RunContext context) {
    if (!context.getReportTaskResults().isEmpty()) {
      return;
    }
    final Path tasksFile = resolveTasksFile(context);
    if (tasksFile == null) {
      Logger.debug(MessageSource.getMessage("report.stage.tasks_file_missing"));
      return;
    }
    try {
      final TasksFileLoader loader = new TasksFileLoader();
      final List<com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord> tasks =
          loader.loadTasks(tasksFile);
      final com.craftsmanbro.fulcraft.plugins.reporting.core.service.aggregator.Aggregator
          aggregator =
              new com.craftsmanbro.fulcraft.plugins.reporting.core.service.aggregator.Aggregator(
                  new com.craftsmanbro.fulcraft.plugins.reporting.adapter
                      .DefaultReportFileAccessor());
      final List<ReportTaskResult> results =
          aggregator.aggregateResults(tasks, context.getProjectRoot(), context.getRunId());
      context.setReportTaskResults(results);
      Logger.info(MessageSource.getMessage("report.stage.results_loaded", results.size()));
    } catch (IOException e) {
      final String message =
          MessageSource.getMessage("report.stage.aggregate_results_failed", e.getMessage());
      Logger.warn(message);
      context.addWarning(message);
    }
  }

  private void generateSourceLevelHtmlReports(
      final RunContext context, final Path outputDirectory) {
    if (!isHtmlReportConfigured(context)) {
      return;
    }
    final Optional<AnalysisResult> analysis = resolveAnalysisResult(context);
    if (analysis.isEmpty() || analysis.get().getClasses().isEmpty()) {
      Logger.debug(MessageSource.getMessage("report.stage.source_html.skip_no_analysis"));
      return;
    }
    try {
      final AnalysisResult analysisResult = analysis.get();
      final com.craftsmanbro.fulcraft.plugins.document.adapter.HtmlDocumentGenerator
          documentGenerator =
              new com.craftsmanbro.fulcraft.plugins.document.adapter.HtmlDocumentGenerator();
      final int generated =
          documentGenerator.generate(analysisResult, outputDirectory, context.getConfig());
      final com.craftsmanbro.fulcraft.plugins.reporting.adapter.AnalysisVisualReportWriter
          visualReportWriter =
              new com.craftsmanbro.fulcraft.plugins.reporting.adapter.AnalysisVisualReportWriter();
      visualReportWriter.writeReport(analysisResult, context, outputDirectory, context.getConfig());
      Logger.info(
          MessageSource.getMessage(
              "report.stage.source_html.generated", generated, outputDirectory));
    } catch (IOException | ReportWriteException e) {
      final String message =
          MessageSource.getMessage("report.stage.source_html.failed", e.getMessage());
      Logger.warn(message);
      context.addWarning(message);
    }
  }

  private boolean isHtmlReportConfigured(final RunContext context) {
    final Config resolvedConfig = config != null ? config : context.getConfig();
    if (resolvedConfig == null || resolvedConfig.getOutput() == null) {
      return false;
    }
    final ReportFormat format =
        ReportFormat.fromStringOrDefault(
            resolvedConfig.getOutput().getReportFormat(), ReportFormat.MARKDOWN);
    return format == ReportFormat.HTML;
  }

  private Optional<AnalysisResult> resolveAnalysisResult(final RunContext context) {
    final Optional<AnalysisResult> inContext = AnalysisResultContext.get(context);
    if (inContext.isPresent()) {
      return inContext;
    }
    final Path analysisDir =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .analysisDir();
    final com.craftsmanbro.fulcraft.plugins.analysis.io.AnalysisResultReader reader =
        new com.craftsmanbro.fulcraft.plugins.analysis.io.AnalysisResultReader();
    final Optional<AnalysisResult> loaded = reader.readFrom(analysisDir);
    loaded.ifPresent(result -> AnalysisResultContext.set(context, result));
    return loaded;
  }

  private Path resolveTasksFile(final RunContext context) {
    final Path override =
        context.getMetadata(ReportMetadataKeys.TASKS_FILE_OVERRIDE, Path.class).orElse(null);
    if (override != null) {
      return override;
    }
    final Path logsDir =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId()).planDir();
    return taskEntriesSource.resolveExistingTasksFile(logsDir);
  }

  /**
   * Creates the default ReportFlow with standard writers.
   *
   * @param context the run context
   * @param config the configuration
   * @return a new ReportFlow
   */
  private static ReportFlow createDefaultService(final RunContext context, final Config config) {
    final ReportOutput output = resolveOutputOverride(context);
    final Path outputDirectory = output.outputDirectory();
    final ReportFormat primaryFormat =
        ReportFormat.fromStringOrDefault(
            config.getOutput().getReportFormat(), ReportFormat.MARKDOWN);
    final Map<ReportFormat, ReportWriterPort> writers = new EnumMap<>(ReportFormat.class);
    writers.put(
        ReportFormat.MARKDOWN,
        new com.craftsmanbro.fulcraft.plugins.reporting.adapter.MarkdownReportWriter(
            outputDirectory,
            resolveFilenameOverride(
                primaryFormat, ReportFormat.MARKDOWN, output.filenameOverride())));
    writers.put(
        ReportFormat.JSON,
        new com.craftsmanbro.fulcraft.plugins.reporting.adapter.JsonReportWriter(
            outputDirectory,
            resolveFilenameOverride(primaryFormat, ReportFormat.JSON, output.filenameOverride()),
            true));
    writers.put(
        ReportFormat.HTML,
        new com.craftsmanbro.fulcraft.plugins.reporting.adapter.HtmlReportWriter(
            outputDirectory,
            resolveFilenameOverride(primaryFormat, ReportFormat.HTML, output.filenameOverride()),
            null));
    writers.put(
        ReportFormat.YAML,
        new com.craftsmanbro.fulcraft.plugins.reporting.adapter.YamlReportWriter(
            outputDirectory,
            resolveFilenameOverride(primaryFormat, ReportFormat.YAML, output.filenameOverride()),
            true));
    return new ReportFlow(writers, context, config, outputDirectory);
  }

  private static ReportOutput resolveOutputOverride(final RunContext context) {
    final Path override =
        context.getMetadata(ReportMetadataKeys.OUTPUT_PATH_OVERRIDE, Path.class).orElse(null);
    if (override == null) {
      final Path defaultDir =
          RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
              .reportDir();
      return new ReportOutput(defaultDir, null);
    }
    if (Files.exists(override) && Files.isDirectory(override)) {
      return new ReportOutput(override, null);
    }
    Path parent = override.getParent();
    if (parent == null) {
      parent = context.getProjectRoot();
    }
    final Path fileName = override.getFileName();
    final String filename = fileName != null ? fileName.toString() : null;
    return new ReportOutput(parent, filename);
  }

  private static String resolveFilenameOverride(
      final ReportFormat primaryFormat,
      final ReportFormat candidate,
      final String overrideFilename) {
    if (overrideFilename == null) {
      return null;
    }
    return primaryFormat == candidate ? overrideFilename : null;
  }

  private record ReportOutput(Path outputDirectory, String filenameOverride) {}
}
