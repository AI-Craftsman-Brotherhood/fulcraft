package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.Main;
import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.ui.banner.StartupBannerSupport;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import picocli.CommandLine;

@Isolated
class BaseCliCommandTest {

  static class TestCommand extends BaseCliCommand {
    AtomicBoolean doCallExecuted = new AtomicBoolean(false);
    AtomicBoolean startupBannerPrinted = new AtomicBoolean(false);
    AtomicReference<Path> startupBannerConfigPath = new AtomicReference<>();
    AtomicReference<String> startupBannerModelName = new AtomicReference<>();
    AtomicReference<Config> capturedConfig = new AtomicReference<>();
    AtomicReference<Path> capturedRoot = new AtomicReference<>();

    @Override
    protected Integer doCall(Config config, Path projectRoot) {
      doCallExecuted.set(true);
      capturedConfig.set(config);
      capturedRoot.set(projectRoot);
      return 0;
    }

    // Override to avoid file system dependency
    @Override
    protected Config loadConfig(Path projectRoot) {
      rememberResolvedConfigPath(Path.of("custom-config.json"));
      return new Config();
    }

    @Override
    protected void printStartupBanner(Config config, Path projectRoot) {
      startupBannerPrinted.set(true);
      startupBannerConfigPath.set(resolveStartupBannerConfigPath(config, projectRoot));
      startupBannerModelName.set(resolveStartupBannerModelName(config));
    }
  }

  @Test
  void call_executesDoCall() {
    TestCommand command = new TestCommand();
    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(0);
    assertThat(command.doCallExecuted).isTrue();
    assertThat(command.startupBannerPrinted).isTrue();
    assertThat(command.startupBannerConfigPath.get())
        .isEqualTo(Path.of("custom-config.json").toAbsolutePath().normalize());
    assertThat(command.startupBannerModelName.get()).isEqualTo("unknown");
    assertThat(command.capturedConfig.get()).isNotNull();
    assertThat(command.capturedConfig.get().getExecution()).isNotNull();
    assertThat(command.capturedConfig.get().getExecution().getLogsRoot())
        .isEqualTo(
            Path.of(".")
                .toAbsolutePath()
                .normalize()
                .resolve(".ful")
                .resolve("runs")
                .normalize()
                .toString());
    // projectRoot might be resolved to "." or null depending on logic
  }

  @Test
  void call_usesLoadedConfigModelForStartupBanner() {
    TestCommand command =
        new TestCommand() {
          @Override
          protected Config loadConfig(Path projectRoot) {
            rememberResolvedConfigPath(Path.of("custom-config.json"));
            Config config = new Config();
            Config.LlmConfig llmConfig = new Config.LlmConfig();
            llmConfig.setModelName("gpt-5.2");
            config.setLlm(llmConfig);
            return config;
          }
        };

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(0);
    assertThat(command.startupBannerModelName.get()).isEqualTo("gpt-5.2");
  }

  @Test
  void call_overridesConfiguredLogsRootToExecutionDirectory() {
    TestCommand command =
        new TestCommand() {
          @Override
          protected Config loadConfig(Path projectRoot) {
            Config config = new Config();
            Config.ExecutionConfig execution = new Config.ExecutionConfig();
            execution.setLogsRoot("/tmp/external-runs");
            config.setExecution(execution);
            return config;
          }
        };

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(0);
    assertThat(command.capturedConfig.get().getExecution().getLogsRoot())
        .isEqualTo(
            Path.of(".")
                .toAbsolutePath()
                .normalize()
                .resolve(".ful")
                .resolve("runs")
                .normalize()
                .toString());
  }

  @Test
  void call_skipsStartupBannerWhenLogFormatIsStructured() {
    TestCommand command =
        new TestCommand() {
          @Override
          protected Config loadConfig(Path projectRoot) {
            Config config = new Config();
            Config.LogConfig logConfig = new Config.LogConfig();
            logConfig.setFormat("json");
            config.setLog(logConfig);
            return config;
          }
        };
    boolean originalJsonMode = Logger.isJsonMode();
    try {
      int exitCode = command.call();

      assertThat(exitCode).isEqualTo(0);
      assertThat(command.startupBannerPrinted).isFalse();
    } finally {
      Logger.setJsonMode(originalJsonMode);
    }
  }

  @Test
  void call_handlesException() {
    TestCommand command =
        new TestCommand() {
          @Override
          protected Integer doCall(Config config, Path projectRoot) {
            throw new RuntimeException("Test Error");
          }
        };

    int exitCode = command.call();
    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
  }

  @Test
  void createConfigLoader_returnsDefaultLoader() {
    TestCommand command = new TestCommand();

    assertThat(command.createConfigLoader())
        .isInstanceOf(com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl.class);
  }

  @Test
  void resolveConfigPath_usesMainConfigFileWhenMainIsAvailable() {
    TestCommand command = new TestCommand();
    Main main = new Main();
    Path explicitConfig = Path.of("explicit-config.json");
    new CommandLine(main).parseArgs("-c", explicitConfig.toString());
    command.main = main;
    AtomicReference<Path> capturedExplicitPath = new AtomicReference<>();
    ConfigLoaderPort loader =
        new com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl() {
          @Override
          public Path resolveConfigPath(Path explicitPath) {
            capturedExplicitPath.set(explicitPath);
            return explicitPath;
          }
        };

    Path resolved = command.resolveConfigPath(loader, Path.of("."));

    assertThat(capturedExplicitPath.get()).isEqualTo(explicitConfig);
    assertThat(resolved).isEqualTo(explicitConfig);
  }

  @Test
  void resolveConfigPath_returnsDefaultConfigFileForNonDefaultLoaderWithoutMainConfig() {
    TestCommand command = new TestCommand();
    ConfigLoaderPort loader =
        new ConfigLoaderPort() {
          @Override
          public Config load(Path configFile) {
            return new Config();
          }

          @Override
          public Config load(
              Path configFile, com.craftsmanbro.fulcraft.config.ConfigOverride... overrides) {
            return new Config();
          }

          @Override
          public void applyOverrides(
              Config config, com.craftsmanbro.fulcraft.config.ConfigOverride... overrides) {}
        };

    Path resolved = command.resolveConfigPath(loader, Path.of("."));

    assertThat(resolved).isEqualTo(ConfigLoaderImpl.DEFAULT_CONFIG_FILE);
  }

  @Test
  void resolveProjectRoot_singleArgumentDelegatesToCliResolvedRoot() {
    Path expected = Path.of("delegated-root");
    TestCommand command =
        new TestCommand() {
          @Override
          protected Path resolveProjectRootFromCli() {
            return expected;
          }

          @Override
          protected Path resolveProjectRoot(Config config, Path projectRootFromCli) {
            return projectRootFromCli;
          }
        };

    assertThat(command.resolveProjectRoot(new Config())).isEqualTo(expected);
  }

  @Test
  void loadConfig_returnsDefaultConfigWhenLoadingIsDisabled() {
    AtomicBoolean createConfigLoaderCalled = new AtomicBoolean(false);
    BaseCliCommand command =
        new BaseCliCommand() {
          @Override
          protected boolean shouldLoadConfig() {
            return false;
          }

          @Override
          protected ConfigLoaderPort createConfigLoader() {
            createConfigLoaderCalled.set(true);
            return super.createConfigLoader();
          }

          @Override
          protected Integer doCall(Config config, Path projectRoot) {
            return 0;
          }
        };

    Config config = command.loadConfig(Path.of("."));

    assertThat(createConfigLoaderCalled).isFalse();
    assertThat(config).isNotNull();
    assertThat(command.getResolvedConfigPath()).isNull();
  }

  @Test
  void call_skipsProjectRootApplyAndValidationWhenDisabled() {
    AtomicReference<Config> captured = new AtomicReference<>();
    TestCommand command =
        new TestCommand() {
          @Override
          protected Config loadConfig(Path projectRoot) {
            return new Config();
          }

          @Override
          protected Path resolveProjectRootFromCli() {
            return Path.of(".");
          }

          @Override
          protected boolean shouldApplyProjectRootToConfig() {
            return false;
          }

          @Override
          protected boolean shouldValidateProjectRoot() {
            return false;
          }

          @Override
          protected boolean shouldDisplayStartupBanner(Config config, Path projectRoot) {
            return false;
          }

          @Override
          protected Integer doCall(Config config, Path projectRoot) {
            captured.set(config);
            return 0;
          }
        };

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(0);
    assertThat(captured.get()).isNotNull();
    assertThat(captured.get().getProject()).isNull();
  }

  @Test
  void printStartupBanner_prefersExplicitProjectRootAndFallsBackToCurrentDirectory() {
    BaseCliCommand command = noOpCommand();
    Path explicitRoot = Path.of("x");

    String explicitOutput =
        captureStdout(() -> command.printStartupBanner(new Config(), explicitRoot));
    String fallbackOutput = captureStdout(() -> command.printStartupBanner(null, null));

    assertThat(explicitOutput)
        .contains("directory: " + StartupBannerSupport.formatDirectory(explicitRoot));
    assertThat(fallbackOutput)
        .contains(
            "directory: "
                + StartupBannerSupport.formatDirectory(Path.of(".").toAbsolutePath().normalize()));
  }

  @Test
  void printStartupBanner_fallsBackWhenConfiguredRootIsMalformed() {
    BaseCliCommand command = noOpCommand();
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setRoot("invalid\0path");
    config.setProject(projectConfig);

    String output = captureStdout(() -> command.printStartupBanner(config, null));

    assertThat(output)
        .contains(
            "directory: "
                + StartupBannerSupport.formatDirectory(Path.of(".").toAbsolutePath().normalize()));
  }

  @Test
  void printStartupBanner_fallsBackWhenConfiguredRootIsNullOrBlank() {
    BaseCliCommand command = noOpCommand();
    Config nullRootConfig = new Config();
    Config.ProjectConfig nullRootProject = new Config.ProjectConfig();
    nullRootProject.setRoot(null);
    nullRootConfig.setProject(nullRootProject);
    Config blankRootConfig = new Config();
    Config.ProjectConfig blankRootProject = new Config.ProjectConfig();
    blankRootProject.setRoot("   ");
    blankRootConfig.setProject(blankRootProject);

    String nullRootOutput = captureStdout(() -> command.printStartupBanner(nullRootConfig, null));
    String blankRootOutput = captureStdout(() -> command.printStartupBanner(blankRootConfig, null));

    assertThat(nullRootOutput)
        .contains(
            "directory: "
                + StartupBannerSupport.formatDirectory(Path.of(".").toAbsolutePath().normalize()));
    assertThat(blankRootOutput)
        .contains(
            "directory: "
                + StartupBannerSupport.formatDirectory(Path.of(".").toAbsolutePath().normalize()));
  }

  @Test
  void printStartupBanner_fallsBackWhenProjectSectionIsMissing() {
    BaseCliCommand command = noOpCommand();

    String output = captureStdout(() -> command.printStartupBanner(new Config(), null));

    assertThat(output)
        .contains(
            "directory: "
                + StartupBannerSupport.formatDirectory(Path.of(".").toAbsolutePath().normalize()));
  }

  @Test
  void startupBannerMetadata_usesFallbacksForNullAndBlankValues() {
    TestCommand command = new TestCommand();
    Config blank = new Config();
    blank.setAppName("   ");
    blank.setVersion("   ");
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setModelName("   ");
    blank.setLlm(llmConfig);

    assertThat(command.resolveStartupBannerApplicationName(blank))
        .isEqualTo(StartupBannerSupport.resolveApplicationName());
    assertThat(command.resolveStartupBannerApplicationVersion(blank))
        .isEqualTo(StartupBannerSupport.resolveApplicationVersion());
    assertThat(command.resolveStartupBannerModelName(blank)).isEqualTo("unknown");
    assertThat(command.resolveStartupBannerApplicationName(null))
        .isEqualTo(StartupBannerSupport.resolveApplicationName());
    assertThat(command.resolveStartupBannerApplicationVersion(null))
        .isEqualTo(StartupBannerSupport.resolveApplicationVersion());
    assertThat(command.resolveStartupBannerModelName(null)).isEqualTo("unknown");

    Config nullModel = new Config();
    nullModel.setLlm(new Config.LlmConfig());
    assertThat(command.resolveStartupBannerModelName(nullModel)).isEqualTo("unknown");
  }

  @Test
  void enforceExecutionLocalRunsRoot_returnsWhenConfigIsNull() throws Exception {
    TestCommand command = new TestCommand();
    var method =
        BaseCliCommand.class.getDeclaredMethod("enforceExecutionLocalRunsRoot", Config.class);
    method.setAccessible(true);

    method.invoke(command, new Object[] {null});
  }

  private static String captureStdout(Runnable action) {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    boolean originalJsonMode = Logger.isJsonMode();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try {
      Logger.setJsonMode(false);
      UiLogger.setOutput(
          new PrintStream(stdout, true, StandardCharsets.UTF_8),
          new PrintStream(stderr, true, StandardCharsets.UTF_8));
      action.run();
      return stdout.toString(StandardCharsets.UTF_8);
    } finally {
      Logger.setJsonMode(originalJsonMode);
      UiLogger.setOutput(originalOut, originalErr);
    }
  }

  private static BaseCliCommand noOpCommand() {
    return new BaseCliCommand() {
      @Override
      protected Integer doCall(Config config, Path projectRoot) {
        return 0;
      }
    };
  }
}
