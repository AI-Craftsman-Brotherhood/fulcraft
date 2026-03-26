package com.craftsmanbro.fulcraft.ui.cli.command.support;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

/** Support for project root resolution, propagation, and validation. */
public final class CliProjectRootSupport {

  private CliProjectRootSupport() {}

  public static Path resolveProjectRootFromCli(final Path option, final Path positional) {
    if (option != null) {
      return option;
    }
    if (positional != null) {
      return positional;
    }
    return null;
  }

  public static Path resolveProjectRoot(
      final Config config,
      final Path option,
      final Path positional,
      final Path projectRootFromCli) {
    if (option != null) {
      return option;
    }
    if (positional != null) {
      return positional;
    }
    if (config != null && config.getProject() != null && config.getProject().getRoot() != null) {
      final String configRoot = config.getProject().getRoot();
      if (!configRoot.isBlank()) {
        return Path.of(configRoot);
      }
    }
    if (projectRootFromCli != null) {
      return projectRootFromCli;
    }
    return Path.of(".");
  }

  public static void applyProjectRootToConfig(final Config config, final Path projectRoot) {
    if (config == null || projectRoot == null) {
      return;
    }
    if (config.getProject() == null) {
      config.setProject(new Config.ProjectConfig());
    }
    config.getProject().setRoot(projectRoot.toString());
  }

  public static void validateProjectRoot(final Path projectRoot, final CommandSpec spec) {
    if (projectRoot == null || Files.isDirectory(projectRoot)) {
      return;
    }
    final String message =
        MessageSource.getMessage("cli.error.project_root_not_directory", projectRoot);
    if (spec != null) {
      throw new ParameterException(spec.commandLine(), message);
    }
    throw new IllegalArgumentException(message);
  }

  public static Path resolveProjectConfigPath(final Path projectRoot) {
    final Path base = projectRoot != null ? projectRoot : Path.of(".");
    Path configFile = base.resolve("config.json");
    if (!Files.exists(configFile)) {
      configFile = base.resolve(".ful").resolve("config.json");
    }
    return configFile;
  }
}
