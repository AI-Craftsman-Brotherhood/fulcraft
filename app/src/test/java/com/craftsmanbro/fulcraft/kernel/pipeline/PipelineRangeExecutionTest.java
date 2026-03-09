package com.craftsmanbro.fulcraft.kernel.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineRangeExecutionTest {

  @Test
  void runsReportAndDocumentWhenRangeIsReportToDocument() {
    List<String> executed = new ArrayList<>();
    Pipeline pipeline = new Pipeline();
    pipeline.registerStage(new RecordingStage(PipelineNodeIds.REPORT, executed));
    pipeline.registerStage(new RecordingStage(PipelineNodeIds.DOCUMENT, executed));

    RunContext context = new RunContext(Path.of("/tmp/test-project"), new Config(), "test-run");

    int exitCode = pipeline.run(context, null, PipelineNodeIds.REPORT, PipelineNodeIds.DOCUMENT);

    assertThat(exitCode).isZero();
    assertThat(executed).containsExactly(PipelineNodeIds.REPORT, PipelineNodeIds.DOCUMENT);
  }

  private static final class RecordingStage implements Stage {
    private final String step;
    private final List<String> executed;

    private RecordingStage(String step, List<String> executed) {
      this.step = step;
      this.executed = executed;
    }

    @Override
    public String getNodeId() {
      return step;
    }

    @Override
    public void execute(RunContext context) throws StageException {
      executed.add(step);
    }
  }
}
