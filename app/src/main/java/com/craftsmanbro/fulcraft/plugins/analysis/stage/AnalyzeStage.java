package com.craftsmanbro.fulcraft.plugins.analysis.stage;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.plugins.analysis.config.AnalysisConfig;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.index.ProjectSymbolIndex;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess.SourcePreprocessor;
import com.craftsmanbro.fulcraft.plugins.analysis.flow.AnalysisFlow;
import com.craftsmanbro.fulcraft.plugins.analysis.flow.AnalysisReportFlow;
import com.craftsmanbro.fulcraft.plugins.analysis.flow.BrittlenessDetectionFlow;
import com.craftsmanbro.fulcraft.plugins.analysis.flow.SourcePreprocessingFlow;
import com.craftsmanbro.fulcraft.plugins.analysis.io.AnalysisResultWriter;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stage that orchestrates source code analysis.
 *
 * <p>This stage coordinates analysis services to perform static analysis of the source code,
 * extracting information about classes, methods, dependencies, and metrics. It follows the
 * orchestration pattern, delegating all work to specialized services.
 *
 * <p>Orchestration steps:
 *
 * <ol>
 *   <li>Preprocess sources (delombok, APT)
 *   <li>Execute static analysis
 *   <li>Validate analysis result
 *   <li>Detect brittleness signals
 *   <li>Save analysis artifacts
 *   <li>Generate quality report
 * </ol>
 */
public class AnalyzeStage implements Stage {

  private final AnalysisFlow analysisFlow;

  private final SourcePreprocessingFlow sourcePreprocessingFlow;

  private final BrittlenessDetectionFlow brittlenessDetectionFlow;

  private final AnalysisResultWriter resultSaver;

  private final AnalysisReportFlow analysisReportFlow;

  /**
   * Creates an AnalyzeStage with the specified analysis port.
   *
   * @param analysisPort the port to use for analysis
   */
  public AnalyzeStage(final AnalysisPort analysisPort) {
    Objects.requireNonNull(
        analysisPort,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "AnalysisPort cannot be null"));
    this.analysisFlow = new AnalysisFlow(analysisPort);
    this.sourcePreprocessingFlow = new SourcePreprocessingFlow();
    this.brittlenessDetectionFlow = new BrittlenessDetectionFlow();
    this.resultSaver = new AnalysisResultWriter();
    this.analysisReportFlow = new AnalysisReportFlow();
  }

  /**
   * Constructor for dependency injection (testing or custom configuration).
   *
   * @param analysisFlow flow for static analysis execution
   * @param sourcePreprocessingFlow flow for source preprocessing
   * @param brittlenessDetectionFlow flow for brittleness detection
   * @param resultSaver service for saving analysis results
   * @param analysisReportFlow flow for generating reports
   */
  public AnalyzeStage(
      final AnalysisFlow analysisFlow,
      final SourcePreprocessingFlow sourcePreprocessingFlow,
      final BrittlenessDetectionFlow brittlenessDetectionFlow,
      final AnalysisResultWriter resultSaver,
      final AnalysisReportFlow analysisReportFlow) {
    this.analysisFlow = Objects.requireNonNull(analysisFlow);
    this.sourcePreprocessingFlow = Objects.requireNonNull(sourcePreprocessingFlow);
    this.brittlenessDetectionFlow = Objects.requireNonNull(brittlenessDetectionFlow);
    this.resultSaver = Objects.requireNonNull(resultSaver);
    this.analysisReportFlow = Objects.requireNonNull(analysisReportFlow);
  }

  @Override
  public String getNodeId() {
    return PipelineNodeIds.ANALYZE;
  }

  @Override
  public void execute(final RunContext context) throws StageException {
    Objects.requireNonNull(
        context,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "RunContext cannot be null"));
    final Path projectRoot =
        Objects.requireNonNull(
            context.getProjectRoot(),
            MessageSource.getMessage(
                "analysis.common.error.argument_null", "Project root cannot be null"));
    Logger.debug(MessageSource.getMessage("analysis.stage.log.start", projectRoot));
    if (context.isDryRun()) {
      Logger.info(MessageSource.getMessage("analysis.stage.dry_run", projectRoot));
      return;
    }
    try {
      final Config config =
          Objects.requireNonNull(
              context.getConfig(),
              MessageSource.getMessage(
                  "analysis.common.error.argument_null", "Config cannot be null"));
      // Step 1: Preprocess sources
      final SourcePreprocessor.Result preprocessResult = preprocess(context, config);
      // Step 2: Execute static analysis
      final AnalysisResult result = analyze(projectRoot, config, preprocessResult);
      // Step 3: Validate result
      validateResult(result, context);
      // Step 4: Detect brittleness and store result
      detectBrittleness(result, context);
      // Step 5: Log statistics
      analysisFlow.logStats(result);
      // Step 6: Save artifacts
      final Path analysisDir = resolveAnalysisDir(context);
      saveArtifacts(result, context, config, preprocessResult, analysisDir);
      // Step 7: Generate report
      final boolean reportGenerated = analysisReportFlow.generateQualityReport(analysisDir);
      logArtifactsSummary(result, analysisDir, reportGenerated);
    } catch (IOException e) {
      throw new StageException(
          PipelineNodeIds.ANALYZE,
          MessageSource.getMessage("analysis.stage.error.failed", projectRoot),
          e);
    }
  }

  @Override
  public String getName() {
    return "Analyze";
  }

  // --- Orchestration helper methods ---
  private SourcePreprocessor.Result preprocess(final RunContext context, final Config config)
      throws StageException {
    final SourcePreprocessor.Result result =
        sourcePreprocessingFlow.preprocess(
            context.getProjectRoot(), config, resolveAnalysisDir(context));
    if (sourcePreprocessingFlow.isStrictModeFailure(result)) {
      throw new StageException(
          PipelineNodeIds.ANALYZE,
          MessageSource.getMessage(
              "analysis.stage.error.preprocess_strict",
              sourcePreprocessingFlow.getFailureReason(result)));
    }
    return result;
  }

  private AnalysisResult analyze(
      final Path projectRoot, final Config config, final SourcePreprocessor.Result preprocessResult)
      throws IOException {
    if (preprocessResult == null || !preprocessResult.shouldUsePreprocessed()) {
      return analysisFlow.analyze(projectRoot, config);
    }
    final AnalysisConfig analysisConfig = config.getAnalysis();
    if (analysisConfig == null) {
      return analysisFlow.analyze(projectRoot, config);
    }
    // Deep copy config to avoid side effects
    final Config effectiveConfig = deepCopyConfig(config);
    final AnalysisConfig effectiveAnalysisConfig = effectiveConfig.getAnalysis();
    final List<String> preprocessedRoots =
        preprocessResult.getSourceRootsAfter().stream().map(Path::toString).toList();
    effectiveAnalysisConfig.setSourceRootPaths(preprocessedRoots);
    if (!effectiveAnalysisConfig.isStrictMode()) {
      effectiveAnalysisConfig.setSourceRootMode("STRICT");
    }
    return analysisFlow.analyze(projectRoot, effectiveConfig);
  }

  private Config deepCopyConfig(final Config config) throws IOException {
    final tools.jackson.databind.ObjectMapper mapper =
        tools.jackson.databind.json.JsonMapper.builderWithJackson2Defaults()
            .configure(
                tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
    return mapper.readValue(mapper.writeValueAsString(config), Config.class);
  }

  private void validateResult(final AnalysisResult result, final RunContext context)
      throws StageException {
    final var validation = analysisFlow.validate(result);
    if (!validation.valid()) {
      throw new StageException(PipelineNodeIds.ANALYZE, validation.warning());
    }
    if (validation.hasWarning()) {
      context.addWarning(validation.warning());
    }
    // Store result in context
    AnalysisResultContext.set(context, result);
  }

  private void detectBrittleness(final AnalysisResult result, final RunContext context) {
    final boolean hasBrittleness = brittlenessDetectionFlow.detectBrittleness(result);
    context.setBrittlenessDetected(hasBrittleness);
    if (hasBrittleness) {
      brittlenessDetectionFlow.logDetectionResults(result);
    }
  }

  private void saveArtifacts(
      final AnalysisResult result,
      final RunContext context,
      final Config config,
      final SourcePreprocessor.Result preprocessResult,
      final Path outputDir) {
    final String projectId = config.getProject().getId();
    final AnalysisConfig analysisConfig = config.getAnalysis();
    final Path projectRoot = context.getProjectRoot();
    // Save analysis result
    resultSaver.saveAnalysisResult(result, outputDir, projectRoot, projectId);
    // Conditionally save file list
    if (analysisConfig != null && analysisConfig.getDumpFileList()) {
      resultSaver.saveAnalyzedFileList(result, outputDir, projectRoot);
    }
    // Save dynamic features
    resultSaver.saveDynamicFeatures(result, outputDir, projectRoot);
    // Build symbol index and save dynamic resolutions
    final ProjectSymbolIndex symbolIndex =
        sourcePreprocessingFlow.buildProjectSymbolIndex(projectRoot, config, preprocessResult);
    final Map<String, String> externalConfigValues =
        sourcePreprocessingFlow.loadExternalConfigValues(projectRoot, config);
    resultSaver.saveDynamicResolutions(
        result, outputDir, projectRoot, config, symbolIndex, externalConfigValues); // Persist enriched method-level dynamic_resolutions to analysis shards for artifact reload paths.
    resultSaver.saveAnalysisShards(result, outputDir, projectId);
    // Save preprocess result
    resultSaver.savePreprocessResult(preprocessResult, outputDir, projectRoot, config);
  }

  private void logArtifactsSummary(
      final AnalysisResult result, final Path outputDir, final boolean reportGenerated) {
    final int classCount =
        result != null && result.getClasses() != null ? result.getClasses().size() : 0;
    final String reportSuffix =
        reportGenerated
            ? MessageSource.getMessage("analysis.stage.artifacts_saved.report_suffix")
            : "";
    Logger.info(
        MessageSource.getMessage(
            "analysis.stage.artifacts_saved", outputDir, classCount, reportSuffix));
  }

  private Path resolveAnalysisDir(final RunContext context) {
    return RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
        .analysisDir();
  }
}
