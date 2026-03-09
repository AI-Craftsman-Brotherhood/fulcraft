package com.craftsmanbro.fulcraft.kernel.pipeline.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StageTest {

  @TempDir Path tempDir;

  private RunContext context;

  @BeforeEach
  void setUp() {
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setId("test-project");
    context = new RunContext(tempDir, config, "test-run-id");
  }

  @Test
  void getName_shouldReturnNodeIdByDefault() {
    Stage stage = new StubStage(PipelineNodeIds.ANALYZE);

    assertThat(stage.getName()).isEqualTo(PipelineNodeIds.ANALYZE);
  }

  @Test
  void getName_shouldThrowWhenNodeIdIsNull() {
    Stage stage = new NullStepStage();

    NullPointerException thrown = assertThrows(NullPointerException.class, stage::getName);

    assertThat(thrown).hasMessage(Stage.ERROR_NODE_ID_NULL);
  }

  @Test
  void shouldSkip_shouldReturnFalseByDefault() {
    Stage stage = new StubStage(PipelineNodeIds.ANALYZE);

    assertThat(stage.shouldSkip(context)).isFalse();
  }

  @Test
  void shouldSkip_shouldThrowWhenContextIsNull() {
    Stage stage = new StubStage(PipelineNodeIds.ANALYZE);

    NullPointerException thrown =
        assertThrows(NullPointerException.class, () -> stage.shouldSkip(null));

    assertThat(thrown).hasMessage(Stage.ERROR_CONTEXT_NULL);
  }

  private static final class StubStage implements Stage {
    private final String step;

    private StubStage(String step) {
      this.step = step;
    }

    @Override
    public String getNodeId() {
      return step;
    }

    @Override
    public void execute(RunContext context) {}
  }

  private static final class NullStepStage implements Stage {
    @Override
    public String getNodeId() {
      return null;
    }

    @Override
    public void execute(RunContext context) {}
  }
}
