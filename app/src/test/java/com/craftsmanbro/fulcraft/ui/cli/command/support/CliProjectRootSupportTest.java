package com.craftsmanbro.fulcraft.ui.cli.command.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParameterException;

@Isolated
class CliProjectRootSupportTest {

  @TempDir Path tempDir;

  @Command(name = "dummy")
  static class DummyCommand implements Runnable {
    @Override
    public void run() {}
  }

  @Test
  void resolveProjectRootFromCli_prefersOptionThenPositional() {
    Path option = tempDir.resolve("option-root");
    Path positional = tempDir.resolve("positional-root");

    assertThat(CliProjectRootSupport.resolveProjectRootFromCli(option, positional))
        .isEqualTo(option);
    assertThat(CliProjectRootSupport.resolveProjectRootFromCli(null, positional))
        .isEqualTo(positional);
    assertThat(CliProjectRootSupport.resolveProjectRootFromCli(null, null)).isNull();
  }

  @Test
  void resolveProjectRoot_followsCliConfigFallbackOrder() {
    Config config = new Config();
    Config.ProjectConfig project = new Config.ProjectConfig();
    project.setRoot(tempDir.resolve("config-root").toString());
    config.setProject(project);
    Path option = tempDir.resolve("option-root");
    Path positional = tempDir.resolve("positional-root");
    Path cliFallback = tempDir.resolve("cli-fallback");

    assertThat(CliProjectRootSupport.resolveProjectRoot(config, option, positional, cliFallback))
        .isEqualTo(option);
    assertThat(CliProjectRootSupport.resolveProjectRoot(config, null, positional, cliFallback))
        .isEqualTo(positional);
    assertThat(CliProjectRootSupport.resolveProjectRoot(config, null, null, cliFallback))
        .isEqualTo(Path.of(project.getRoot()));

    project.setRoot("   ");
    assertThat(CliProjectRootSupport.resolveProjectRoot(config, null, null, cliFallback))
        .isEqualTo(cliFallback);
    assertThat(CliProjectRootSupport.resolveProjectRoot(null, null, null, null))
        .isEqualTo(Path.of("."));
  }

  @Test
  void applyProjectRootToConfig_initializesAndUpdatesProjectSection() {
    Config config = new Config();
    Path projectRoot = tempDir.resolve("project-root");

    CliProjectRootSupport.applyProjectRootToConfig(config, projectRoot);

    assertThat(config.getProject()).isNotNull();
    assertThat(config.getProject().getRoot()).isEqualTo(projectRoot.toString());

    CliProjectRootSupport.applyProjectRootToConfig(null, projectRoot);
    CliProjectRootSupport.applyProjectRootToConfig(config, null);
    assertThat(config.getProject().getRoot()).isEqualTo(projectRoot.toString());
  }

  @Test
  void validateProjectRoot_rejectsNonDirectoryPaths() throws IOException {
    Path file = Files.createFile(tempDir.resolve("not-a-directory.txt"));
    CommandLine.Model.CommandSpec spec = new CommandLine(new DummyCommand()).getCommandSpec();

    assertThatThrownBy(() -> CliProjectRootSupport.validateProjectRoot(file, spec))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining(file.toString());
    assertThatThrownBy(() -> CliProjectRootSupport.validateProjectRoot(file, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(file.toString());
  }

  @Test
  void validateProjectRoot_acceptsNullAndDirectories() throws IOException {
    Path directory = Files.createDirectories(tempDir.resolve("project"));

    CliProjectRootSupport.validateProjectRoot(null, null);
    CliProjectRootSupport.validateProjectRoot(directory, null);
  }

  @Test
  void resolveProjectConfigPath_prefersProjectConfigThenFulFallback() throws IOException {
    Path projectRoot = Files.createDirectories(tempDir.resolve("project"));
    Path configJson = Files.createFile(projectRoot.resolve("config.json"));
    Path fulConfig = projectRoot.resolve(".ful").resolve("config.json");

    assertThat(CliProjectRootSupport.resolveProjectConfigPath(projectRoot)).isEqualTo(configJson);

    Files.delete(configJson);
    Files.createDirectories(fulConfig.getParent());
    Files.createFile(fulConfig);

    assertThat(CliProjectRootSupport.resolveProjectConfigPath(projectRoot)).isEqualTo(fulConfig);
  }
}
