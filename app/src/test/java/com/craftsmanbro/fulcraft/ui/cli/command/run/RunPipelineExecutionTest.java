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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KernelPortTestExtension.class)
class RunPipelineExecutionTest {

  private static final List<String> ANALYZE_NODE_IDS = List.of(PipelineNodeIds.ANALYZE);
  private static final Path PROJECT_ROOT = Path.of(".").toAbsolutePath();
  private static final String RUN_ID = "run-test";

  @Test
  void execute_runsPipelineAndInvokesDiagnosticsReporter() {
    RunPipelineExecution execution =
        new RunPipelineExecution(new PipelineRunner(pipelineWithAnalyzeStage(context -> {})));
    RunContext context = new RunContext(PROJECT_ROOT, new Config(), RUN_ID);
    AtomicInteger diagnosticsCalls = new AtomicInteger();

    int exitCode =
        execution.execute(
            context,
            ANALYZE_NODE_IDS,
            null,
            null,
            c -> {
              assertThat(c).isSameAs(context);
              diagnosticsCalls.incrementAndGet();
            });

    assertThat(exitCode).isZero();
    assertThat(diagnosticsCalls.get()).isEqualTo(1);
  }

  @Test
  void execute_invokesDiagnosticsReporterEvenWhenPipelineFails() {
    RunPipelineExecution execution =
        new RunPipelineExecution(
            new PipelineRunner(
                pipelineWithAnalyzeStage(
                    context -> {
                      throw new IllegalStateException("boom");
                    })));
    RunContext context = new RunContext(PROJECT_ROOT, new Config(), RUN_ID);
    AtomicInteger diagnosticsCalls = new AtomicInteger();

    int exitCode =
        execution.execute(
            context,
            ANALYZE_NODE_IDS,
            null,
            null,
            c -> {
              assertThat(c).isSameAs(context);
              diagnosticsCalls.incrementAndGet();
            });

    assertThat(exitCode).isNotZero();
    assertThat(diagnosticsCalls.get()).isEqualTo(1);
  }

  @Test
  void execute_runsSpecificNodesInPipelineOrder() {
    List<String> executedNodeIds = new ArrayList<>();
    Pipeline pipeline = new Pipeline();
    pipeline.registerStage(
        stageWithNodeId(
            PipelineNodeIds.ANALYZE, context -> executedNodeIds.add(PipelineNodeIds.ANALYZE)));
    pipeline.registerStage(
        stageWithNodeId(
            PipelineNodeIds.REPORT, context -> executedNodeIds.add(PipelineNodeIds.REPORT)));

    RunPipelineExecution execution = new RunPipelineExecution(new PipelineRunner(pipeline));
    RunContext context = new RunContext(PROJECT_ROOT, new Config(), RUN_ID);

    int exitCode =
        execution.execute(
            context, List.of(PipelineNodeIds.REPORT, PipelineNodeIds.ANALYZE), null, null, null);

    assertThat(exitCode).isZero();
    assertThat(executedNodeIds).containsExactly(PipelineNodeIds.ANALYZE, PipelineNodeIds.REPORT);
  }

  @Test
  void execute_allowsNullDiagnosticsReporter() {
    RunPipelineExecution execution =
        new RunPipelineExecution(new PipelineRunner(pipelineWithAnalyzeStage(context -> {})));
    RunContext context = new RunContext(PROJECT_ROOT, new Config(), RUN_ID);

    int exitCode = execution.execute(context, ANALYZE_NODE_IDS, null, null, null);

    assertThat(exitCode).isZero();
  }

  @Test
  void constructor_rejectsNullRunner() {
    assertThatThrownBy(() -> new RunPipelineExecution(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("runner is required");
  }

  @Test
  void execute_rejectsNullContext() {
    PipelineRunner runner = new PipelineRunner(new Pipeline());
    RunPipelineExecution execution = new RunPipelineExecution(runner);

    assertThatThrownBy(() -> execution.execute(null, ANALYZE_NODE_IDS, null, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("context is required");
  }

  private static Pipeline pipelineWithAnalyzeStage(final Consumer<RunContext> stageAction) {
    Pipeline pipeline = new Pipeline();
    pipeline.registerStage(stageWithNodeId(PipelineNodeIds.ANALYZE, stageAction));
    return pipeline;
  }

  private static Stage stageWithNodeId(
      final String nodeId, final Consumer<RunContext> stageAction) {
    return new Stage() {
      @Override
      public String getNodeId() {
        return nodeId;
      }

      @Override
      public void execute(final RunContext context) {
        stageAction.accept(context);
      }
    };
  }
}
