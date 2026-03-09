package com.craftsmanbro.fulcraft.plugins.exploration.stage;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.io.AnalysisResultReader;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.exploration.core.context.ExploreMetadataKeys;
import com.craftsmanbro.fulcraft.plugins.exploration.flow.ExploreFlow;
import java.nio.file.Path;
import java.util.Objects;

/** Stage that generates exploration artifacts from analysis output. */
public class ExploreStage implements Stage {

  public static final String METADATA_EXPLORE_OUTPUT_DIR = ExploreMetadataKeys.OUTPUT_DIRECTORY;

  public static final String METADATA_EXPLORE_INDEX_FILE = ExploreMetadataKeys.INDEX_FILE;

  public static final String METADATA_EXPLORE_SNAPSHOT_FILE = ExploreMetadataKeys.SNAPSHOT_FILE;

  public static final String METADATA_EXPLORE_CLASS_COUNT = ExploreMetadataKeys.CLASS_COUNT;

  public static final String METADATA_EXPLORE_PACKAGE_COUNT = ExploreMetadataKeys.PACKAGE_COUNT;

  public static final String METADATA_EXPLORE_METHOD_COUNT = ExploreMetadataKeys.METHOD_COUNT;

  public static final String METADATA_EXPLORE_GENERATED = "explore.generated";

  private final ExploreFlow exploreFlow;

  private final AnalysisResultReader analysisResultReader;

  public ExploreStage() {
    this(new ExploreFlow());
  }

  public ExploreStage(final ExploreFlow exploreFlow) {
    this(exploreFlow, new AnalysisResultReader());
  }

  ExploreStage(final ExploreFlow exploreFlow, final AnalysisResultReader analysisResultReader) {
    this.exploreFlow =
        Objects.requireNonNull(
            exploreFlow,
            MessageSource.getMessage("explore.common.error.argument_null", "exploreFlow"));
    this.analysisResultReader =
        Objects.requireNonNull(
            analysisResultReader,
            MessageSource.getMessage("explore.common.error.argument_null", "analysisResultReader"));
  }

  @Override
  public String getNodeId() {
    return PipelineNodeIds.EXPLORE;
  }

  @Override
  public void execute(final RunContext context) throws StageException {
    Objects.requireNonNull(
        context,
        MessageSource.getMessage(
            "explore.common.error.argument_null", "RunContext cannot be null"));
    Objects.requireNonNull(
        context.getProjectRoot(),
        MessageSource.getMessage(
            "explore.common.error.argument_null", "Project root cannot be null"));
    Objects.requireNonNull(
        context.getConfig(),
        MessageSource.getMessage("explore.common.error.argument_null", "Config cannot be null"));
    Logger.info(msg("explore.stage.start"));
    if (context.isDryRun()) {
      Logger.info(msg("explore.stage.dry_run"));
      context.putMetadata(METADATA_EXPLORE_GENERATED, false);
      return;
    }
    final AnalysisResult analysisResult = resolveAnalysisResult(context);
    if (analysisResult.getClasses().isEmpty()) {
      Logger.info(msg("explore.stage.no_classes"));
      context.putMetadata(METADATA_EXPLORE_GENERATED, false);
      context.putMetadata(METADATA_EXPLORE_CLASS_COUNT, 0);
      context.putMetadata(METADATA_EXPLORE_PACKAGE_COUNT, 0);
      context.putMetadata(METADATA_EXPLORE_METHOD_COUNT, 0);
      return;
    }
    try {
      final ExploreFlow.Result result = exploreFlow.generate(analysisResult, context);
      context.putMetadata(METADATA_EXPLORE_GENERATED, true);
      context.putMetadata(METADATA_EXPLORE_OUTPUT_DIR, result.outputDirectory().toString());
      context.putMetadata(METADATA_EXPLORE_INDEX_FILE, result.indexFile().toString());
      context.putMetadata(METADATA_EXPLORE_SNAPSHOT_FILE, result.snapshotFile().toString());
      context.putMetadata(METADATA_EXPLORE_CLASS_COUNT, result.classCount());
      context.putMetadata(METADATA_EXPLORE_PACKAGE_COUNT, result.packageCount());
      context.putMetadata(METADATA_EXPLORE_METHOD_COUNT, result.methodCount());
      Logger.info(msg("explore.stage.complete", result.outputDirectory()));
    } catch (Exception e) {
      context.putMetadata(METADATA_EXPLORE_GENERATED, false);
      if (e instanceof RuntimeException runtimeException) {
        throw new StageException(
            PipelineNodeIds.EXPLORE,
            msg("explore.stage.error.failed_type", runtimeException.getClass().getSimpleName()),
            e);
      }
      throw new StageException(
          PipelineNodeIds.EXPLORE, msg("explore.stage.error.failed", e.getMessage()), e);
    }
  }

  @Override
  public String getName() {
    return "Explore";
  }

  private AnalysisResult resolveAnalysisResult(final RunContext context) throws StageException {
    return AnalysisResultContext.get(context)
        .or(() -> loadAnalysisFromArtifacts(context))
        .orElseThrow(
            () ->
                new StageException(
                    PipelineNodeIds.EXPLORE, msg("explore.stage.error.no_analysis")));
  }

  private java.util.Optional<AnalysisResult> loadAnalysisFromArtifacts(final RunContext context) {
    final Path analysisDir =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .analysisDir();
    final java.util.Optional<AnalysisResult> loaded = analysisResultReader.readFrom(analysisDir);
    if (loaded.isPresent()) {
      AnalysisResultContext.set(context, loaded.get());
      Logger.info(msg("explore.stage.analysis.loaded", analysisDir));
    }
    return loaded;
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
