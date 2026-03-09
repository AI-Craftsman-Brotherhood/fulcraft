package com.craftsmanbro.fulcraft.kernel.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.testsupport.KernelPortTestExtension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
@ExtendWith(KernelPortTestExtension.class)
class PipelineExecutionOrderTest {

  @Test
  void run_executesStagesInRegistrationOrder_whenNoRangeSpecified() {
    final List<String> executed = new ArrayList<>();
    final Pipeline pipeline = new Pipeline();
    pipeline
        .registerStage(recordingStage(PipelineNodeIds.REPORT, executed))
        .registerStage(recordingStage(PipelineNodeIds.ANALYZE, executed))
        .registerStage(recordingStage(PipelineNodeIds.GENERATE, executed));

    final int exitCode = pipeline.run(newRunContext());

    assertThat(exitCode).isZero();
    assertThat(executed)
        .containsExactly(PipelineNodeIds.REPORT, PipelineNodeIds.ANALYZE, PipelineNodeIds.GENERATE);
  }

  @Test
  void run_resolvesFromToRangeByRegistrationOrder_notEnumOrder() {
    final List<String> executed = new ArrayList<>();
    final Pipeline pipeline = new Pipeline();
    pipeline
        .registerStage(recordingStage(PipelineNodeIds.REPORT, executed))
        .registerStage(recordingStage(PipelineNodeIds.ANALYZE, executed))
        .registerStage(recordingStage(PipelineNodeIds.GENERATE, executed));

    final int exitCode =
        pipeline.run(newRunContext(), null, PipelineNodeIds.REPORT, PipelineNodeIds.GENERATE);

    assertThat(exitCode).isZero();
    assertThat(executed)
        .containsExactly(PipelineNodeIds.REPORT, PipelineNodeIds.ANALYZE, PipelineNodeIds.GENERATE);
  }

  @Test
  void runNodes_executesDagOrder_byNodeDependencies() {
    final List<String> executed = new ArrayList<>();
    final Pipeline pipeline = new Pipeline();
    pipeline.registerStage(
        "report-node", recordingStage(PipelineNodeIds.REPORT, executed), List.of());
    pipeline.registerStage(
        "analyze-node", recordingStage(PipelineNodeIds.ANALYZE, executed), List.of("report-node"));
    pipeline.registerStage(
        "generate-node",
        recordingStage(PipelineNodeIds.GENERATE, executed),
        List.of("analyze-node"));

    final int exitCode = pipeline.runNodes(newRunContext(), null, null, null);

    assertThat(exitCode).isZero();
    assertThat(executed)
        .containsExactly(PipelineNodeIds.REPORT, PipelineNodeIds.ANALYZE, PipelineNodeIds.GENERATE);
  }

  private static Stage recordingStage(final String step, final List<String> executed) {
    return new Stage() {
      @Override
      public String getNodeId() {
        return step;
      }

      @Override
      public void execute(final RunContext context) throws StageException {
        executed.add(step);
      }
    };
  }

  private static RunContext newRunContext() {
    return new RunContext(Path.of("."), new Config(), "test-run", Path.of("build/tmp/test-run"));
  }
}
