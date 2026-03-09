package com.craftsmanbro.fulcraft.plugins.document.stage;

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
import com.craftsmanbro.fulcraft.plugins.document.flow.DocumentFlow;
import java.nio.file.Path;
import java.util.Objects;

/** Stage that generates project documentation from analysis results. */
public class DocumentStage implements Stage {

  public static final String METADATA_DOCUMENT_OUTPUT_DIR = "documentOutputDirectory";

  public static final String METADATA_DOCUMENT_GENERATED = "documentGenerated";

  public static final String METADATA_DOCUMENT_COUNT = "documentCount";

  private final DocumentFlow documentFlow;

  private final AnalysisResultReader analysisResultReader;

  public DocumentStage(final DocumentFlow documentFlow) {
    this(documentFlow, new AnalysisResultReader());
  }

  DocumentStage(final DocumentFlow documentFlow, final AnalysisResultReader analysisResultReader) {
    this.documentFlow =
        Objects.requireNonNull(
            documentFlow,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "documentFlow"));
    this.analysisResultReader =
        Objects.requireNonNull(
            analysisResultReader,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "analysisResultReader"));
  }

  @Override
  public String getNodeId() {
    return PipelineNodeIds.DOCUMENT;
  }

  @Override
  public void execute(final RunContext context) throws StageException {
    Objects.requireNonNull(
        context,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "RunContext cannot be null"));
    Objects.requireNonNull(
        context.getProjectRoot(),
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "Project root cannot be null"));
    Objects.requireNonNull(
        context.getConfig(),
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "Config cannot be null"));
    Logger.info(msg("document.stage.start"));
    if (context.isDryRun()) {
      Logger.info(msg("document.stage.dry_run"));
      context.putMetadata(METADATA_DOCUMENT_GENERATED, false);
      return;
    }
    final AnalysisResult analysisResult = resolveAnalysisResult(context);
    if (analysisResult.getClasses().isEmpty()) {
      Logger.info(MessageSource.getMessage("document.no_classes"));
      context.putMetadata(METADATA_DOCUMENT_GENERATED, false);
      context.putMetadata(METADATA_DOCUMENT_COUNT, 0);
      return;
    }
    try {
      final Path outputDir =
          RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
              .runRoot()
              .resolve("docs");
      final DocumentFlow.Result result =
          documentFlow.generate(
              analysisResult,
              context.getConfig(),
              context.getProjectRoot(),
              outputDir,
              new LoggingProgressListener());
      context.putMetadata(METADATA_DOCUMENT_GENERATED, true);
      context.putMetadata(METADATA_DOCUMENT_COUNT, result.totalCount());
      context.putMetadata(METADATA_DOCUMENT_OUTPUT_DIR, result.outputPath().toString());
      Logger.info(msg("document.stage.complete", result.totalCount(), result.outputPath()));
    } catch (DocumentFlow.ValidationException e) {
      context.putMetadata(METADATA_DOCUMENT_GENERATED, false);
      throw new StageException(
          PipelineNodeIds.DOCUMENT,
          msg("document.stage.error.generation_failed", e.getMessage()),
          e);
    } catch (Exception e) {
      context.putMetadata(METADATA_DOCUMENT_GENERATED, false);
      throw new StageException(
          PipelineNodeIds.DOCUMENT,
          msg("document.stage.error.generation_failed_type", e.getClass().getSimpleName()),
          e);
    }
  }

  @Override
  public String getName() {
    return "Document";
  }

  private AnalysisResult resolveAnalysisResult(final RunContext context) throws StageException {
    return AnalysisResultContext.get(context)
        .or(() -> loadAnalysisFromArtifacts(context))
        .orElseThrow(
            () ->
                new StageException(
                    PipelineNodeIds.DOCUMENT, msg("document.stage.error.no_analysis")));
  }

  private java.util.Optional<AnalysisResult> loadAnalysisFromArtifacts(final RunContext context) {
    final Path analysisDir =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .analysisDir();
    final java.util.Optional<AnalysisResult> loaded = analysisResultReader.readFrom(analysisDir);
    if (loaded.isPresent()) {
      AnalysisResultContext.set(context, loaded.get());
      Logger.info(msg("document.stage.analysis.loaded", analysisDir));
    }
    return loaded;
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  private static final class LoggingProgressListener implements DocumentFlow.ProgressListener {

    @Override
    public void onMarkdownGenerating() {
      Logger.info(msg("document.markdown.generating"));
    }

    @Override
    public void onMarkdownComplete(final int count) {
      Logger.info(msg("document.markdown.complete", count));
    }

    @Override
    public void onHtmlGenerating() {
      Logger.info(msg("document.html.generating"));
    }

    @Override
    public void onPdfGenerating() {
      Logger.info(msg("document.pdf.generating"));
    }

    @Override
    public void onDiagramGenerating() {
      Logger.info(msg("document.diagram.generating"));
    }

    @Override
    public void onSingleFileComplete(final Path outputFile) {
      Logger.info(msg("document.single.complete", outputFile));
    }

    @Override
    public void onLlmGenerating() {
      Logger.info(msg("document.llm.generating"));
    }

    @Override
    public void onLlmComplete(final int count, final Path outputPath) {
      Logger.info(msg("document.llm.complete", count, outputPath));
    }
  }
}
