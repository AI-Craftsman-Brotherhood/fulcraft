package com.craftsmanbro.fulcraft.plugins.document.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.io.AnalysisResultReader;
import com.craftsmanbro.fulcraft.plugins.analysis.io.AnalysisResultWriter;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.document.flow.DocumentFlow;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentStageTest {

  @TempDir Path tempDir;

  @Test
  void getNameAndStep_returnDocumentMetadata() {
    DocumentStage stage = new DocumentStage(mock(DocumentFlow.class));

    assertThat(stage.getName()).isEqualTo("Document");
    assertThat(stage.getNodeId()).isEqualTo(PipelineNodeIds.DOCUMENT);
  }

  @Test
  void constructor_throwsWhenDocumentFlowIsNull() {
    assertThatThrownBy(() -> new DocumentStage((DocumentFlow) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("documentFlow");
  }

  @Test
  void constructor_throwsWhenAnalysisResultReaderIsNull() {
    DocumentFlow flow = mock(DocumentFlow.class);

    assertThatThrownBy(() -> new DocumentStage(flow, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("analysisResultReader");
  }

  @Test
  void execute_throwsWhenContextIsNull() {
    DocumentStage stage = new DocumentStage(mock(DocumentFlow.class));

    assertThatThrownBy(() -> stage.execute(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("RunContext cannot be null");
  }

  @Test
  void execute_skipsFlowWhenDryRun() throws Exception {
    DocumentFlow flow = mock(DocumentFlow.class);
    DocumentStage stage = new DocumentStage(flow);
    RunContext context = new RunContext(tempDir, baseConfig(), "run-1").withDryRun(true);

    stage.execute(context);

    verifyNoInteractions(flow);
    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_GENERATED, Boolean.class))
        .contains(false);
  }

  @Test
  void execute_usesAnalysisFromContext() throws Exception {
    DocumentFlow flow = mock(DocumentFlow.class);
    DocumentStage stage = new DocumentStage(flow);
    Config config = baseConfig();
    RunContext context = new RunContext(tempDir, config, "run-2");
    Path outputPath =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .runRoot()
            .resolve("docs");
    when(flow.generate(any(), any(), any(), eq(outputPath), any()))
        .thenReturn(new DocumentFlow.Result(2, outputPath));
    AnalysisResult analysisResult = sampleAnalysisResult();
    AnalysisResultContext.set(context, analysisResult);

    stage.execute(context);

    verify(flow).generate(eq(analysisResult), eq(config), eq(tempDir), eq(outputPath), any());
    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_GENERATED, Boolean.class))
        .contains(true);
    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_COUNT, Integer.class))
        .contains(2);
    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_OUTPUT_DIR, String.class))
        .contains(outputPath.toString());
  }

  @Test
  void execute_loadsAnalysisArtifactsWhenContextIsMissingResult() throws Exception {
    DocumentFlow flow = mock(DocumentFlow.class);
    Config config = baseConfig();
    RunContext context = new RunContext(tempDir, config, "run-3");
    Path outputPath =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .runRoot()
            .resolve("docs");
    when(flow.generate(any(), any(), any(), eq(outputPath), any()))
        .thenReturn(new DocumentFlow.Result(1, outputPath));
    AnalysisResult analysisResult = sampleAnalysisResult();
    Path analysisDir =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .analysisDir();
    new AnalysisResultWriter()
        .saveAnalysisResult(analysisResult, analysisDir, tempDir, "test-project");

    DocumentStage stage = new DocumentStage(flow);
    stage.execute(context);

    verify(flow).generate(any(), eq(config), eq(tempDir), eq(outputPath), any());
    assertThat(AnalysisResultContext.get(context)).isPresent();
  }

  @Test
  void execute_loadsAnalysisUsingInjectedReaderWhenContextIsMissingResult() throws Exception {
    DocumentFlow flow = mock(DocumentFlow.class);
    AnalysisResultReader reader = mock(AnalysisResultReader.class);
    DocumentStage stage = new DocumentStage(flow, reader);
    Config config = baseConfig();
    RunContext context = new RunContext(tempDir, config, "run-4");
    AnalysisResult analysisResult = sampleAnalysisResult();
    Path analysisDir =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .analysisDir();
    Path outputPath =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .runRoot()
            .resolve("docs");
    when(reader.readFrom(analysisDir)).thenReturn(Optional.of(analysisResult));
    when(flow.generate(any(), any(), any(), eq(outputPath), any()))
        .thenReturn(new DocumentFlow.Result(1, outputPath));

    stage.execute(context);

    verify(reader).readFrom(analysisDir);
    verify(flow).generate(eq(analysisResult), eq(config), eq(tempDir), eq(outputPath), any());
    assertThat(AnalysisResultContext.get(context)).contains(analysisResult);
  }

  @Test
  void execute_skipsFlowWhenAnalysisHasNoClasses() throws Exception {
    DocumentFlow flow = mock(DocumentFlow.class);
    DocumentStage stage = new DocumentStage(flow);
    RunContext context = new RunContext(tempDir, baseConfig(), "run-5");
    AnalysisResult emptyResult = new AnalysisResult("test-project");
    AnalysisResultContext.set(context, emptyResult);

    stage.execute(context);

    verifyNoInteractions(flow);
    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_GENERATED, Boolean.class))
        .contains(false);
    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_COUNT, Integer.class))
        .contains(0);
    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_OUTPUT_DIR, String.class))
        .isEmpty();
  }

  @Test
  void execute_wrapsValidationExceptionFromFlow() throws Exception {
    DocumentFlow flow = mock(DocumentFlow.class);
    DocumentStage stage = new DocumentStage(flow);
    Config config = baseConfig();
    RunContext context = new RunContext(tempDir, config, "run-6");
    Path outputPath =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .runRoot()
            .resolve("docs");
    AnalysisResultContext.set(context, sampleAnalysisResult());
    DocumentFlow.ValidationException validationException =
        new DocumentFlow.ValidationException("invalid-doc-format");
    when(flow.generate(any(), any(), any(), eq(outputPath), any())).thenThrow(validationException);

    assertThatThrownBy(() -> stage.execute(context))
        .isInstanceOf(StageException.class)
        .satisfies(
            thrown -> {
              StageException stageException = (StageException) thrown;
              assertThat(stageException.getNodeId()).isEqualTo(PipelineNodeIds.DOCUMENT);
              assertThat(stageException.getCause()).isSameAs(validationException);
              assertThat(stageException.getMessage()).contains("invalid-doc-format");
            });

    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_GENERATED, Boolean.class))
        .contains(false);
  }

  @Test
  void execute_wrapsUnexpectedExceptionFromFlow() throws Exception {
    DocumentFlow flow = mock(DocumentFlow.class);
    DocumentStage stage = new DocumentStage(flow);
    Config config = baseConfig();
    RunContext context = new RunContext(tempDir, config, "run-7");
    Path outputPath =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .runRoot()
            .resolve("docs");
    AnalysisResultContext.set(context, sampleAnalysisResult());
    IllegalStateException cause = new IllegalStateException("boom");
    when(flow.generate(any(), any(), any(), eq(outputPath), any())).thenThrow(cause);

    assertThatThrownBy(() -> stage.execute(context))
        .isInstanceOf(StageException.class)
        .satisfies(
            thrown -> {
              StageException stageException = (StageException) thrown;
              assertThat(stageException.getNodeId()).isEqualTo(PipelineNodeIds.DOCUMENT);
              assertThat(stageException.getCause()).isSameAs(cause);
              assertThat(stageException.getMessage()).contains("IllegalStateException");
            });

    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_GENERATED, Boolean.class))
        .contains(false);
  }

  @Test
  void execute_throwsWhenNoAnalysisIsAvailable() {
    DocumentFlow flow = mock(DocumentFlow.class);
    DocumentStage stage = new DocumentStage(flow);
    RunContext context = new RunContext(tempDir, baseConfig(), "run-8");

    assertThatThrownBy(() -> stage.execute(context))
        .isInstanceOf(StageException.class)
        .extracting(ex -> ((StageException) ex).getNodeId())
        .isEqualTo(PipelineNodeIds.DOCUMENT);
  }

  private Config baseConfig() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setId("test-project");
    config.setProject(projectConfig);
    config.setDocs(new Config.DocsConfig());
    return config;
  }

  private AnalysisResult sampleAnalysisResult() {
    AnalysisResult result = new AnalysisResult("test-project");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    result.setClasses(List.of(classInfo));
    return result;
  }
}
