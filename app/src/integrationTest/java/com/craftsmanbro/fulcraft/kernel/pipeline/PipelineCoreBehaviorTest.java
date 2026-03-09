package com.craftsmanbro.fulcraft.kernel.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineCoreBehaviorTest {

  @TempDir Path tempDir;

  @Test
  void run_specificStepsContainingNull_ignoresNullEntries() {
    Pipeline pipeline = new Pipeline();
    List<String> executed = new ArrayList<>();
    pipeline.registerStage(
        new RecordingStage(PipelineNodeIds.ANALYZE, () -> executed.add(PipelineNodeIds.ANALYZE)));
    RunContext context = newContext("run-null-step");

    int exitCode = pipeline.run(context, Arrays.asList(PipelineNodeIds.ANALYZE, null), null, null);

    assertThat(exitCode).isZero();
    assertThat(executed).containsExactly(PipelineNodeIds.ANALYZE);
    assertThat(context.getErrors()).isEmpty();
  }

  @Test
  void run_rejectsInvalidRange() {
    Pipeline pipeline = new Pipeline();
    pipeline.registerStage(new RecordingStage(PipelineNodeIds.ANALYZE, () -> {}));
    pipeline.registerStage(new RecordingStage(PipelineNodeIds.REPORT, () -> {}));
    RunContext context = newContext("run-invalid-range");

    assertThatThrownBy(
            () -> pipeline.run(context, null, PipelineNodeIds.REPORT, PipelineNodeIds.ANALYZE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("--from must be <= --to");
  }

  @Test
  void run_missingStageForSpecificSteps_marksErrorAndReturnsFailure() {
    Pipeline pipeline = new Pipeline();
    RunContext context = newContext("run-missing-specific");

    int exitCode = pipeline.run(context, List.of(PipelineNodeIds.ANALYZE), null, null);

    assertThat(exitCode).isEqualTo(1);
    assertThat(context.getErrors())
        .singleElement()
        .satisfies(
            message -> assertThat(message).contains("No stage registered for node: analyze"));
    assertThat(context.getWarnings()).isEmpty();
  }

  @Test
  void run_missingStageForDefaultRangeOnEmptyPipeline_returnsSuccessWithoutMessages() {
    Pipeline pipeline = new Pipeline();
    RunContext context = newContext("run-missing-range");

    int exitCode = pipeline.run(context, null, PipelineNodeIds.ANALYZE, PipelineNodeIds.ANALYZE);

    assertThat(exitCode).isZero();
    assertThat(context.getWarnings()).isEmpty();
    assertThat(context.getErrors()).isEmpty();
  }

  @Test
  void run_failFastStopsOnFirstMissingSpecificStage() {
    Pipeline pipeline = new Pipeline();
    RunContext context = newContext("run-fail-fast").withFailFast(true);

    int exitCode =
        pipeline.run(context, List.of(PipelineNodeIds.ANALYZE, PipelineNodeIds.REPORT), null, null);

    assertThat(exitCode).isEqualTo(1);
    assertThat(context.getErrors())
        .singleElement()
        .satisfies(
            message -> assertThat(message).contains("No stage registered for node: analyze"));
  }

  @Test
  void run_listenerFailureAddsWarningAndStageStillExecutes() {
    Pipeline pipeline = new Pipeline();
    AtomicBoolean executed = new AtomicBoolean(false);
    pipeline.registerStage(new RecordingStage(PipelineNodeIds.ANALYZE, () -> executed.set(true)));
    pipeline.addListener(
        new Pipeline.PipelineListener() {
          @Override
          public void onStageStarted(RunContext context, Stage stage) {
            throw new RuntimeException("listener boom");
          }
        });
    RunContext context = newContext("run-listener-warning");

    int exitCode = pipeline.run(context, List.of(PipelineNodeIds.ANALYZE), null, null);

    assertThat(exitCode).isZero();
    assertThat(executed.get()).isTrue();
    assertThat(context.getWarnings())
        .anySatisfy(
            warning -> assertThat(warning).contains("Listener failed:").contains("onStageStarted"));
  }

  @Test
  void run_specificStepsExecutesInRequestedOrder() {
    Pipeline pipeline = new Pipeline();
    List<String> executed = new ArrayList<>();
    pipeline.registerStage(
        new RecordingStage(PipelineNodeIds.DOCUMENT, () -> executed.add(PipelineNodeIds.DOCUMENT)));
    pipeline.registerStage(
        new RecordingStage(PipelineNodeIds.REPORT, () -> executed.add(PipelineNodeIds.REPORT)));

    RunContext context = newContext("run-specific-order");
    int exitCode =
        pipeline.run(
            context, List.of(PipelineNodeIds.DOCUMENT, PipelineNodeIds.REPORT), null, null);

    assertThat(exitCode).isZero();
    assertThat(executed).containsExactly(PipelineNodeIds.DOCUMENT, PipelineNodeIds.REPORT);
  }

  private RunContext newContext(String runId) {
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setId("test-project");
    return new RunContext(tempDir, config, runId);
  }

  private static final class RecordingStage implements Stage {
    private final String step;
    private final Runnable onExecute;

    private RecordingStage(String step, Runnable onExecute) {
      this.step = step;
      this.onExecute = onExecute;
    }

    @Override
    public String getNodeId() {
      return step;
    }

    @Override
    public void execute(RunContext context) throws StageException {
      onExecute.run();
    }
  }
}
