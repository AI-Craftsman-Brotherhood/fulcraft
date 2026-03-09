package com.craftsmanbro.fulcraft.plugins.exploration.stage;

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
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.exploration.flow.ExploreFlow;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExploreStageTest {

  @TempDir Path tempDir;

  @Test
  void defaultConstructor_createsExploreStage() {
    ExploreStage stage = new ExploreStage();

    assertThat(stage.getName()).isEqualTo("Explore");
    assertThat(stage.getNodeId()).isEqualTo(PipelineNodeIds.EXPLORE);
  }

  @Test
  void getNameAndStep_returnExploreMetadata() {
    ExploreStage stage = new ExploreStage(mock(ExploreFlow.class));

    assertThat(stage.getName()).isEqualTo("Explore");
    assertThat(stage.getNodeId()).isEqualTo(PipelineNodeIds.EXPLORE);
  }

  @Test
  void constructor_throwsWhenExploreFlowIsNull() {
    assertThatThrownBy(() -> new ExploreStage((ExploreFlow) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("exploreFlow");
  }

  @Test
  void constructor_throwsWhenAnalysisResultReaderIsNull() {
    ExploreFlow flow = mock(ExploreFlow.class);

    assertThatThrownBy(() -> new ExploreStage(flow, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("analysisResultReader");
  }

  @Test
  void execute_skipsFlowWhenDryRun() throws Exception {
    ExploreFlow flow = mock(ExploreFlow.class);
    ExploreStage stage = new ExploreStage(flow);
    RunContext context = new RunContext(tempDir, baseConfig(), "run-dry").withDryRun(true);

    stage.execute(context);

    verifyNoInteractions(flow);
    assertThat(context.getMetadata(ExploreStage.METADATA_EXPLORE_GENERATED, Boolean.class))
        .contains(false);
  }

  @Test
  void execute_usesAnalysisFromContextAndStoresMetadata() throws Exception {
    ExploreFlow flow = mock(ExploreFlow.class);
    ExploreStage stage = new ExploreStage(flow);
    RunContext context = new RunContext(tempDir, baseConfig(), "run-ok");
    AnalysisResult analysisResult = sampleAnalysisResult();
    AnalysisResultContext.set(context, analysisResult);

    Path outputDir =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .runRoot()
            .resolve("explore");
    ExploreFlow.Result flowResult =
        new ExploreFlow.Result(
            outputDir,
            outputDir.resolve("index.html"),
            outputDir.resolve("explore_snapshot.json"),
            1,
            1,
            1);
    when(flow.generate(eq(analysisResult), eq(context))).thenReturn(flowResult);

    stage.execute(context);

    verify(flow).generate(eq(analysisResult), eq(context));
    assertThat(context.getMetadata(ExploreStage.METADATA_EXPLORE_GENERATED, Boolean.class))
        .contains(true);
    assertThat(context.getMetadata(ExploreStage.METADATA_EXPLORE_OUTPUT_DIR, String.class))
        .contains(outputDir.toString());
    assertThat(context.getMetadata(ExploreStage.METADATA_EXPLORE_CLASS_COUNT, Integer.class))
        .contains(1);
  }

  @Test
  void execute_marksNotGeneratedWhenAnalysisHasNoClasses() throws Exception {
    ExploreFlow flow = mock(ExploreFlow.class);
    ExploreStage stage = new ExploreStage(flow);
    RunContext context = new RunContext(tempDir, baseConfig(), "run-no-classes");
    AnalysisResultContext.set(context, new AnalysisResult("test-project"));

    stage.execute(context);

    verifyNoInteractions(flow);
    assertThat(context.getMetadata(ExploreStage.METADATA_EXPLORE_GENERATED, Boolean.class))
        .contains(false);
    assertThat(context.getMetadata(ExploreStage.METADATA_EXPLORE_CLASS_COUNT, Integer.class))
        .contains(0);
    assertThat(context.getMetadata(ExploreStage.METADATA_EXPLORE_PACKAGE_COUNT, Integer.class))
        .contains(0);
    assertThat(context.getMetadata(ExploreStage.METADATA_EXPLORE_METHOD_COUNT, Integer.class))
        .contains(0);
  }

  @Test
  void execute_loadsAnalysisUsingInjectedReaderWhenMissingFromContext() throws Exception {
    ExploreFlow flow = mock(ExploreFlow.class);
    AnalysisResultReader reader = mock(AnalysisResultReader.class);
    ExploreStage stage = new ExploreStage(flow, reader);
    RunContext context = new RunContext(tempDir, baseConfig(), "run-load");
    AnalysisResult analysisResult = sampleAnalysisResult();
    Path analysisDir =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .analysisDir();
    when(reader.readFrom(analysisDir)).thenReturn(Optional.of(analysisResult));
    when(flow.generate(any(), eq(context)))
        .thenReturn(
            new ExploreFlow.Result(
                analysisDir.getParent().resolve("explore"),
                analysisDir.getParent().resolve("explore/index.html"),
                analysisDir.getParent().resolve("explore/explore_snapshot.json"),
                1,
                1,
                1));

    stage.execute(context);

    verify(reader).readFrom(analysisDir);
    verify(flow).generate(eq(analysisResult), eq(context));
    assertThat(AnalysisResultContext.get(context)).contains(analysisResult);
  }

  @Test
  void execute_throwsWhenNoAnalysisAvailable() {
    ExploreFlow flow = mock(ExploreFlow.class);
    ExploreStage stage = new ExploreStage(flow);
    RunContext context = new RunContext(tempDir, baseConfig(), "run-no-analysis");

    assertThatThrownBy(() -> stage.execute(context))
        .isInstanceOf(StageException.class)
        .extracting(ex -> ((StageException) ex).getNodeId())
        .isEqualTo(PipelineNodeIds.EXPLORE);
  }

  @Test
  void execute_wrapsRuntimeExceptionFromFlow() throws Exception {
    ExploreFlow flow = mock(ExploreFlow.class);
    ExploreStage stage = new ExploreStage(flow);
    RunContext context = new RunContext(tempDir, baseConfig(), "run-error");
    AnalysisResult analysisResult = sampleAnalysisResult();
    AnalysisResultContext.set(context, analysisResult);
    when(flow.generate(eq(analysisResult), eq(context)))
        .thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> stage.execute(context))
        .isInstanceOf(StageException.class)
        .satisfies(
            thrown -> {
              StageException stageException = (StageException) thrown;
              assertThat(stageException.getNodeId()).isEqualTo(PipelineNodeIds.EXPLORE);
              assertThat(stageException.getMessage()).contains("IllegalStateException");
            });

    assertThat(context.getMetadata(ExploreStage.METADATA_EXPLORE_GENERATED, Boolean.class))
        .contains(false);
  }

  @Test
  void execute_wrapsCheckedExceptionFromFlow() throws Exception {
    ExploreFlow flow = mock(ExploreFlow.class);
    ExploreStage stage = new ExploreStage(flow);
    RunContext context = new RunContext(tempDir, baseConfig(), "run-io-error");
    AnalysisResult analysisResult = sampleAnalysisResult();
    AnalysisResultContext.set(context, analysisResult);
    when(flow.generate(eq(analysisResult), eq(context))).thenThrow(new IOException("io boom"));

    assertThatThrownBy(() -> stage.execute(context))
        .isInstanceOf(StageException.class)
        .satisfies(
            thrown -> {
              StageException stageException = (StageException) thrown;
              assertThat(stageException.getNodeId()).isEqualTo(PipelineNodeIds.EXPLORE);
              assertThat(stageException.getMessage()).contains("io boom");
              assertThat(stageException.getCause()).isInstanceOf(IOException.class);
            });

    assertThat(context.getMetadata(ExploreStage.METADATA_EXPLORE_GENERATED, Boolean.class))
        .contains(false);
  }

  private Config baseConfig() {
    Config config = Config.createDefault();
    config.getProject().setId("test-project");
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
