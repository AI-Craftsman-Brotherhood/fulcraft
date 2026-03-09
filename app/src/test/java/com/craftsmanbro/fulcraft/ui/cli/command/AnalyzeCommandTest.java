package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class AnalyzeCommandTest {

  @Test
  void getSteps_returnsAnalyzeStep() {
    AnalyzeCommand command = new AnalyzeCommand();
    assertThat(command.getNodeIds()).containsExactly(PipelineNodeIds.ANALYZE);
  }

  @Test
  void getCommandDescription_returnsDescription() {
    AnalyzeCommand command = new AnalyzeCommand();
    assertThat(command.getCommandDescription())
        .isEqualTo(MessageSource.getMessage("analyze.command.description"));
  }
}
