package com.craftsmanbro.fulcraft.kernel.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PipelineRecoverableExceptionTest {

  @Test
  void recoverableStageException_allowsContinuationAndMarksFailure() {
    Pipeline pipeline = new Pipeline();
    AtomicBoolean secondStageRan = new AtomicBoolean(false);

    Stage failingStage =
        new Stage() {
          @Override
          public String getNodeId() {
            return PipelineNodeIds.ANALYZE;
          }

          @Override
          public void execute(RunContext context) throws StageException {
            throw new StageException(PipelineNodeIds.ANALYZE, "recoverable failure", true);
          }

          @Override
          public String getName() {
            return "Analyze";
          }
        };

    Stage secondStage =
        new Stage() {
          @Override
          public String getNodeId() {
            return PipelineNodeIds.REPORT;
          }

          @Override
          public void execute(RunContext context) {
            secondStageRan.set(true);
          }

          @Override
          public String getName() {
            return "Report";
          }
        };

    pipeline.registerStage(failingStage).registerStage(secondStage);

    RunContext context = new RunContext(Path.of("/tmp/test-project"), new Config(), "test-run");
    int exitCode =
        pipeline.run(context, List.of(PipelineNodeIds.ANALYZE, PipelineNodeIds.REPORT), null, null);

    assertEquals(1, exitCode);
    assertTrue(secondStageRan.get());
    assertTrue(context.hasErrors());
  }
}
