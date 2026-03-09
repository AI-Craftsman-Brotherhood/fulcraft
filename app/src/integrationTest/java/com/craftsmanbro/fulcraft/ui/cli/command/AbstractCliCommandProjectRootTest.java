package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbstractCliCommandProjectRootTest {

  @TempDir Path tempDir;

  @Test
  void shouldUseProjectRootOption_whenOptionProvided() {
    TestCommand command = new TestCommand();
    command.projectRootOption = tempDir.resolve("option-root");
    command.projectRootPositional = tempDir.resolve("positional-root");

    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setRoot(tempDir.resolve("config-root").toString());
    config.setProject(projectConfig);

    assertThat(command.resolveProjectRoot(config)).isEqualTo(command.projectRootOption);
  }

  @Test
  void shouldUseConfigRoot_whenNoCliArgsProvided() {
    TestCommand command = new TestCommand();

    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    Path configRoot = tempDir.resolve("config-root");
    projectConfig.setRoot(configRoot.toString());
    config.setProject(projectConfig);

    assertThat(command.resolveProjectRoot(config)).isEqualTo(configRoot);
  }

  private static class TestCommand extends AbstractCliCommand {
    @Override
    protected List<String> getNodeIds() {
      return List.of();
    }

    @Override
    protected String getCommandDescription() {
      return "test";
    }
  }
}
