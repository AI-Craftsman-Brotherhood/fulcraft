package com.craftsmanbro.fulcraft.kernel.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.RunDirectories;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineRunnerTest {

  @TempDir Path tempDir;

  @Test
  void run_createsRunRootDirectory() {
    RunContext context = newContext("run-setup");

    Pipeline pipeline = new Pipeline();
    pipeline.registerStage(new NoopStage());
    PipelineRunner runner = new PipelineRunner(pipeline);

    runner.run(context, List.of(PipelineNodeIds.ANALYZE), null, null);

    RunDirectories runDirectories = RunDirectories.from(context);
    assertThat(Files.isDirectory(runDirectories.runRoot())).isTrue();
  }

  @Test
  void constructor_rejectsNullPipeline() {
    assertThatThrownBy(() -> new PipelineRunner(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("pipeline must not be null");
  }

  @Test
  void addListener_delegatesToPipelineAndReturnsSelf() {
    Pipeline pipeline = mock(Pipeline.class);
    PipelineRunner runner = new PipelineRunner(pipeline);
    Pipeline.PipelineListener listener = new Pipeline.PipelineListener() {};

    PipelineRunner returned = runner.addListener(listener);

    assertThat(returned).isSameAs(runner);
    verify(pipeline).addListener(listener);
  }

  @Test
  void run_passesThroughGenerateStep() {
    Pipeline pipeline = mockPipelineReturningSuccess();
    PipelineRunner runner = new PipelineRunner(pipeline);
    RunContext context = newContext("run-generate");

    int exitCode = runner.run(context, List.of(PipelineNodeIds.GENERATE), null, null);

    assertThat(exitCode).isZero();
    verify(pipeline).run(eq(context), eq(List.of(PipelineNodeIds.GENERATE)), isNull(), isNull());
  }

  @Test
  void run_preservesRequestedOrderForOfficialSteps() {
    Pipeline pipeline = mockPipelineReturningSuccess();
    PipelineRunner runner = new PipelineRunner(pipeline);
    RunContext context = newContext("run-order");

    int exitCode =
        runner.run(
            context,
            List.of(
                PipelineNodeIds.ANALYZE,
                PipelineNodeIds.DOCUMENT,
                PipelineNodeIds.REPORT,
                PipelineNodeIds.EXPLORE),
            null,
            null);

    assertThat(exitCode).isZero();
    verify(pipeline)
        .run(
            eq(context),
            eq(
                List.of(
                    PipelineNodeIds.ANALYZE,
                    PipelineNodeIds.DOCUMENT,
                    PipelineNodeIds.REPORT,
                    PipelineNodeIds.EXPLORE)),
            isNull(),
            isNull());
  }

  @Test
  void run_passesThroughGenerateRange() {
    Pipeline pipeline = mockPipelineReturningSuccess();
    PipelineRunner runner = new PipelineRunner(pipeline);
    RunContext context = newContext("run-generate-range");

    int exitCode = runner.run(context, null, PipelineNodeIds.GENERATE, PipelineNodeIds.REPORT);

    assertThat(exitCode).isZero();
    verify(pipeline)
        .run(eq(context), isNull(), eq(PipelineNodeIds.GENERATE), eq(PipelineNodeIds.REPORT));
  }

  @Test
  void run_withoutExplicitSteps_passesThroughNulls() {
    Pipeline pipeline = mockPipelineReturningSuccess();
    PipelineRunner runner = new PipelineRunner(pipeline);
    RunContext context = newContext("run-config-generate");

    int exitCode = runner.run(context, null, null, null);

    assertThat(exitCode).isZero();
    verify(pipeline).run(eq(context), isNull(), isNull(), isNull());
  }

  private Pipeline mockPipelineReturningSuccess() {
    Pipeline pipeline = mock(Pipeline.class);
    when(pipeline.run(any(), any(), any(), any())).thenReturn(0);
    return pipeline;
  }

  private RunContext newContext(String runId) {
    return newContext(runId, null);
  }

  private RunContext newContext(String runId, List<String> stages) {
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setId("test-project");
    if (stages != null) {
      Config.PipelineConfig pipelineConfig = new Config.PipelineConfig();
      pipelineConfig.setStages(stages);
      config.setPipeline(pipelineConfig);
    }
    return new RunContext(tempDir, config, runId);
  }

  private static final class NoopStage implements Stage {

    @Override
    public String getNodeId() {
      return PipelineNodeIds.ANALYZE;
    }

    @Override
    public String getName() {
      return "Noop";
    }

    @Override
    public void execute(RunContext context) throws StageException {
      // Intentionally no-op.
    }
  }
}
