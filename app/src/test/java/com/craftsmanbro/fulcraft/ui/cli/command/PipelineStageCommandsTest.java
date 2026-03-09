package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import org.junit.jupiter.api.Test;

class PipelineStageCommandsTest {

  @Test
  void analyzeCommand_exposesAnalyzeStepAndDescription() {
    AnalyzeCommand command = new AnalyzeCommand();

    assertThat(command.getNodeIds()).containsExactly(PipelineNodeIds.ANALYZE);
    assertThat(command.getCommandDescription())
        .isEqualTo(MessageSource.getMessage("analyze.command.description"));
  }
}
