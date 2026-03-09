package com.craftsmanbro.fulcraft.ui.cli.command.run;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.io.AnalysisResultWriter;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.document.flow.DocumentFlow;
import com.craftsmanbro.fulcraft.plugins.document.stage.DocumentStage;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import com.craftsmanbro.fulcraft.ui.cli.command.RunCommand;
import com.craftsmanbro.fulcraft.ui.cli.command.support.AnalysisReportExecutionSupport;
import com.craftsmanbro.fulcraft.ui.cli.wiring.ServiceFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Executes ANALYZE+REPORT shortcut flow (optionally with DOCUMENT) used by {@link RunCommand}. */
public final class RunAnalysisReportExecutor {

  public interface StageListener {

    default void onStageStarted(final String nodeId) {}

    default void onStageCompleted(final String nodeId) {}

    default void onStageSkipped(final String nodeId) {}
  }

  private static final StageListener NOOP_STAGE_LISTENER = new StageListener() {};

  private final ServiceFactory services;

  private final String engineType;

  public RunAnalysisReportExecutor(final ServiceFactory services, final String engineType) {
    this.services = Objects.requireNonNull(services, "services is required");
    this.engineType = engineType;
  }

  public int execute(final RunContext context) throws IOException, ReportWriteException {
    return execute(context, false);
  }

  public int execute(final RunContext context, final boolean includeDocument)
      throws IOException, ReportWriteException {
    return execute(context, includeDocument, NOOP_STAGE_LISTENER);
  }

  public int execute(
      final RunContext context, final boolean includeDocument, final StageListener stageListener)
      throws IOException, ReportWriteException {
    Objects.requireNonNull(context, "RunContext is required");
    Objects.requireNonNull(stageListener, "stageListener is required");
    final Config config = Objects.requireNonNull(context.getConfig(), "Config is required");
    final Path projectRoot =
        Objects.requireNonNull(context.getProjectRoot(), "projectRoot is required");
    if (context.isDryRun()) {
      stageListener.onStageStarted(PipelineNodeIds.ANALYZE);
      stageListener.onStageCompleted(PipelineNodeIds.ANALYZE);
      stageListener.onStageStarted(PipelineNodeIds.REPORT);
      stageListener.onStageCompleted(PipelineNodeIds.REPORT);
      if (includeDocument) {
        stageListener.onStageStarted(PipelineNodeIds.DOCUMENT);
        stageListener.onStageCompleted(PipelineNodeIds.DOCUMENT);
      }
      if (includeDocument) {
        UiLogger.info(MessageSource.getMessage("run.dry_run.analyze_report_docs"));
      } else {
        UiLogger.info(MessageSource.getMessage("run.dry_run.analyze_report"));
      }
      return 0;
    }
    final RunPaths runDirectories =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId());
    try {
      runDirectories.ensureDirectories();
    } catch (Exception e) {
      UiLogger.warn(MessageSource.getMessage("run.error.init_dirs_failed", e.getMessage()));
    }
    UiLogger.configureRunLogging(config, runDirectories.runRoot(), context.getRunId());
    UiLogger.info(MessageSource.getMessage("run.analysis_report.starting"));
    stageListener.onStageStarted(PipelineNodeIds.ANALYZE);
    final AnalysisResult result =
        services.createAnalysisPort(engineType).analyze(projectRoot, config);
    stageListener.onStageCompleted(PipelineNodeIds.ANALYZE);
    final Path analysisDir = runDirectories.analysisDir();
    new AnalysisResultWriter()
        .saveAnalysisResult(result, analysisDir, projectRoot, config.getProject().getId());
    if (result.getClasses().isEmpty()) {
      stageListener.onStageSkipped(PipelineNodeIds.REPORT);
      if (includeDocument) {
        stageListener.onStageSkipped(PipelineNodeIds.DOCUMENT);
      }
      UiLogger.stdout(MessageSource.getMessage("analysis_report.no_classes"));
      return 0;
    }
    stageListener.onStageStarted(PipelineNodeIds.REPORT);
    final Path outputDir = runDirectories.reportDir();
    AnalysisReportExecutionSupport.generateReports(
        context,
        result,
        new AnalysisReportExecutionSupport.ReportOutput(outputDir, null),
        false,
        null);
    stageListener.onStageCompleted(PipelineNodeIds.REPORT);
    if (includeDocument) {
      stageListener.onStageStarted(PipelineNodeIds.DOCUMENT);
      generateDocuments(context, config, projectRoot, runDirectories, result);
      stageListener.onStageCompleted(PipelineNodeIds.DOCUMENT);
    }
    return 0;
  }

  private void generateDocuments(
      final RunContext context,
      final Config config,
      final Path projectRoot,
      final RunPaths runDirectories,
      final AnalysisResult result)
      throws IOException {
    final DocumentFlow documentFlow = new DocumentFlow(services::createDecoratedLlmClient);
    final Path docsOutputDir = runDirectories.runRoot().resolve("docs");
    try {
      final DocumentFlow.Result documentResult =
          documentFlow.generate(result, config, projectRoot, docsOutputDir);
      context.putMetadata(DocumentStage.METADATA_DOCUMENT_GENERATED, true);
      context.putMetadata(DocumentStage.METADATA_DOCUMENT_COUNT, documentResult.totalCount());
      context.putMetadata(
          DocumentStage.METADATA_DOCUMENT_OUTPUT_DIR, documentResult.outputPath().toString());
      UiLogger.info(
          MessageSource.getMessage(
              "run.document.generated", documentResult.totalCount(), documentResult.outputPath()));
    } catch (DocumentFlow.ValidationException e) {
      throw new IOException(MessageSource.getMessage("run.document.failed", e.getMessage()), e);
    }
  }
}
