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
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

@Isolated
class CliProjectRootSupportIntegrationTest {

  @Test
  void resolveProjectRootFromCli_returnsOption_whenOptionProvided() {
    Path option = Path.of("option");
    Path positional = Path.of("positional");
    Path result = CliProjectRootSupport.resolveProjectRootFromCli(option, positional);
    assertThat(result).isEqualTo(option);
  }

  @Test
  void resolveProjectRootFromCli_returnsPositional_whenOptionMissing() {
    Path positional = Path.of("positional");
    Path result = CliProjectRootSupport.resolveProjectRootFromCli(null, positional);
    assertThat(result).isEqualTo(positional);
  }

  @Test
  void resolveProjectRootFromCli_returnsNull_whenNoCliOptionProvided() {
    Path result = CliProjectRootSupport.resolveProjectRootFromCli(null, null);
    assertThat(result).isNull();
  }

  @Test
  void resolveProjectRoot_prioritizesOption() {
    Path option = Path.of("option");
    Path result = CliProjectRootSupport.resolveProjectRoot(null, option, null, null);
    assertThat(result).isEqualTo(option);
  }

  @Test
  void resolveProjectRoot_usesPositional_whenOptionMissing() {
    Path positional = Path.of("positional");
    Path result = CliProjectRootSupport.resolveProjectRoot(null, null, positional, null);
    assertThat(result).isEqualTo(positional);
  }

  @Test
  void resolveProjectRoot_fallsBackToConfig() {
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setRoot("configRoot");

    Path result = CliProjectRootSupport.resolveProjectRoot(config, null, null, null);
    assertThat(result).isEqualTo(Path.of("configRoot"));
  }

  @Test
  void resolveProjectRoot_fallsBackToCliResolvedValue_whenConfigRootIsBlank() {
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setRoot("   ");

    Path cliRoot = Path.of("cliRoot");
    Path result = CliProjectRootSupport.resolveProjectRoot(config, null, null, cliRoot);

    assertThat(result).isEqualTo(cliRoot);
  }

  @Test
  void resolveProjectRoot_fallsBackToCliResolvedValue_whenConfigProjectSectionMissing() {
    Config config = new Config();
    Path cliRoot = Path.of("cliRoot");

    Path result = CliProjectRootSupport.resolveProjectRoot(config, null, null, cliRoot);

    assertThat(result).isEqualTo(cliRoot);
  }

  @Test
  void resolveProjectRoot_fallsBackToDot_whenNoCandidateExists() {
    Path result = CliProjectRootSupport.resolveProjectRoot(null, null, null, null);
    assertThat(result).isEqualTo(Path.of("."));
  }

  @Test
  void applyProjectRootToConfig_createsProjectConfigWhenMissing() {
    Config config = new Config();
    Path root = Path.of("newRoot");

    CliProjectRootSupport.applyProjectRootToConfig(config, root);

    assertThat(config.getProject().getRoot()).isEqualTo("newRoot");
  }

  @Test
  void applyProjectRootToConfig_overwritesExistingProjectRoot() {
    Config config = new Config();
    Config.ProjectConfig project = new Config.ProjectConfig();
    project.setRoot("oldRoot");
    config.setProject(project);

    CliProjectRootSupport.applyProjectRootToConfig(config, Path.of("newRoot"));

    assertThat(config.getProject()).isSameAs(project);
    assertThat(config.getProject().getRoot()).isEqualTo("newRoot");
  }

  @Test
  void applyProjectRootToConfig_ignoresNullInputs() {
    Config config = new Config();

    CliProjectRootSupport.applyProjectRootToConfig(null, Path.of("ignored"));
    CliProjectRootSupport.applyProjectRootToConfig(config, null);

    assertThat(config.getProject()).isNull();
  }

  @Test
  void validateProjectRoot_throwsIllegalArgumentException_whenInvalidAndSpecMissing(
      @TempDir Path tempDir) {
    Path invalidPath = tempDir.resolve("missing");

    assertThatThrownBy(() -> CliProjectRootSupport.validateProjectRoot(invalidPath, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validateProjectRoot_throwsParameterException_whenSpecProvided(@TempDir Path tempDir) {
    Path invalidPath = tempDir.resolve("missing");
    CommandLine commandLine = new CommandLine(new TestCommand());
    CommandSpec spec = commandLine.getCommandSpec();

    assertThatThrownBy(() -> CliProjectRootSupport.validateProjectRoot(invalidPath, spec))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("Project root must be an existing directory");
  }

  @Test
  void validateProjectRoot_acceptsNullAndDirectory(@TempDir Path tempDir) {
    CliProjectRootSupport.validateProjectRoot(null, null);
    CliProjectRootSupport.validateProjectRoot(tempDir, null);
  }

  @Test
  void resolveProjectConfigPath_prefersConfigJson_whenPresent(@TempDir Path tempDir)
      throws IOException {
    Path configJson = tempDir.resolve("config.json");
    Files.writeString(configJson, "{}");

    Path result = CliProjectRootSupport.resolveProjectConfigPath(tempDir);

    assertThat(result).isEqualTo(configJson);
  }

  @Test
  void resolveProjectConfigPath_fallsBackToDotFulConfig_whenConfigJsonMissing(@TempDir Path tempDir)
      throws IOException {
    Path fallback = tempDir.resolve(".ful").resolve("config.json");
    Files.createDirectories(fallback.getParent());
    Files.writeString(fallback, "{}");

    Path result = CliProjectRootSupport.resolveProjectConfigPath(tempDir);

    assertThat(result).isEqualTo(fallback);
  }

  @Test
  void resolveProjectConfigPath_returnsFallbackPathEvenWhenFileMissing(@TempDir Path tempDir) {
    Path result = CliProjectRootSupport.resolveProjectConfigPath(tempDir);
    assertThat(result).isEqualTo(tempDir.resolve(".ful").resolve("config.json"));
  }

  @Test
  void resolveProjectConfigPath_usesCurrentDirectory_whenProjectRootIsNull() {
    Path result = CliProjectRootSupport.resolveProjectConfigPath(null);
    Path configInCurrentDir = Path.of(".").resolve("config.json");
    Path expected =
        Files.exists(configInCurrentDir)
            ? configInCurrentDir
            : Path.of(".").resolve(".ful").resolve("config.json");
    assertThat(result).isEqualTo(expected);
  }

  @Command(name = "test")
  static class TestCommand implements Runnable {
    @Override
    public void run() {}
  }
}
