package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class StepsCommandTest {

  @Test
  void doCall_printsConfiguredWorkflowNodesAndUsage() {
    RecordingStepsCommand command = new RecordingStepsCommand();
    Config config = new Config();

    int exitCode = command.doCall(config, Path.of("."));

    assertThat(exitCode).isEqualTo(0);
    assertThat(command.lines).contains(MessageSource.getMessage("steps.configured_nodes"));
    assertThat(command.lines).contains(MessageSource.getMessage("steps.usage_examples"));
    assertThat(nodeIds(command.lines)).contains("analyze", "report", "document", "explore");
  }

  @Test
  void doCall_filtersConfiguredStagesAndKeepsRequiredDependencies() {
    RecordingStepsCommand command = new RecordingStepsCommand();
    Config config = new Config();
    config.getPipeline().setStages(List.of("report"));

    int exitCode = command.doCall(config, Path.of("."));

    assertThat(exitCode).isEqualTo(0);
    assertThat(nodeIds(command.lines)).containsExactly("analyze", "report");
  }

  private static List<String> nodeIds(List<String> lines) {
    return lines.stream()
        .filter(line -> line.contains("plugin="))
        .map(StepsCommandTest::extractNodeId)
        .toList();
  }

  private static String extractNodeId(String line) {
    return line.strip().split("\\s+", 2)[0];
  }

  private static final class RecordingStepsCommand extends StepsCommand {
    private final List<String> lines = new ArrayList<>();

    @Override
    protected void print(String message) {
      lines.add(message);
    }
  }
}
