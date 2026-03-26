package com.craftsmanbro.fulcraft.ui.cli.command.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.Pipeline;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineRunner;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.testsupport.KernelPortTestExtension;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KernelPortTestExtension.class)
class RunPipelineLoggingTest {

  private static final List<String> ANALYZE_NODE_IDS = List.of(PipelineNodeIds.ANALYZE);
  private static final Path PROJECT_ROOT = Path.of(".").toAbsolutePath();
  private final PrintStream originalStdout = System.out;
  private final PrintStream originalStderr = System.err;
  private ByteArrayOutputStream capturedStdout;

  @BeforeEach
  void setUp() {
    capturedStdout = new ByteArrayOutputStream();
    UiLogger.setOutput(
        new PrintStream(capturedStdout, true, StandardCharsets.UTF_8), originalStderr);
    UiLogger.setColorEnabled(false);
    UiLogger.setJsonMode(false);
  }

  @AfterEach
  void tearDown() {
    UiLogger.setOutput(originalStdout, originalStderr);
  }

  @Test
  void attach_emitsStartAndCompleteLogs() {
    PipelineRunner runner = new PipelineRunner(pipelineWithAnalyzeStage(context -> {}));
    RunContext context = new RunContext(PROJECT_ROOT, new Config(), "run-test");

    RunPipelineLogging.attach(runner);
    int exitCode = runner.run(context, ANALYZE_NODE_IDS, null, null);
    String output = capturedStdout.toString(StandardCharsets.UTF_8);

    assertThat(exitCode).isZero();
    assertThat(output).contains("Pipeline starting with 1 nodes");
    assertThat(output).contains("Pipeline completed successfully");
  }

  @Test
  void attach_emitsErrorLogWhenPipelineFails() {
    PipelineRunner runner =
        new PipelineRunner(
            pipelineWithAnalyzeStage(
                context -> {
                  throw new IllegalStateException("boom");
                }));
    RunContext context = new RunContext(PROJECT_ROOT, new Config(), "run-fail");

    RunPipelineLogging.attach(runner);
    int exitCode = runner.run(context, ANALYZE_NODE_IDS, null, null);
    String output = capturedStdout.toString(StandardCharsets.UTF_8);

    assertThat(exitCode).isNotZero();
    assertThat(output).contains("Pipeline completed with errors");
  }

  @Test
  void attach_rejectsNullRunner() {
    assertThatThrownBy(() -> RunPipelineLogging.attach(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("runner is required");
  }

  private static Pipeline pipelineWithAnalyzeStage(final Consumer<RunContext> stageAction) {
    Pipeline pipeline = new Pipeline();
    pipeline.registerStage(
        new Stage() {
          @Override
          public String getNodeId() {
            return PipelineNodeIds.ANALYZE;
          }

          @Override
          public void execute(final RunContext context) {
            stageAction.accept(context);
          }
        });
    return pipeline;
  }
}
