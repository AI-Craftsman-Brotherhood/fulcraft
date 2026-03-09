package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.config.ConfigOverride;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.CommonOverrides;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.ui.banner.StartupBannerSupport;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Isolated
class BaseCliCommandCoverageTest {

  private PrintStream originalStdout;
  private PrintStream originalStderr;
  private boolean originalJsonMode;
  private boolean originalColorEnabled;

  @BeforeEach
  void setUp() {
    originalStdout = System.out;
    originalStderr = System.err;
    originalJsonMode = Logger.isJsonMode();
    originalColorEnabled = Logger.isColorEnabled();
    UiLogger.setOutput(originalStdout, originalStderr);
    UiLogger.setJsonMode(false);
    UiLogger.setColorEnabled(false);
  }

  @AfterEach
  void tearDown() {
    UiLogger.setOutput(originalStdout, originalStderr);
    UiLogger.setJsonMode(originalJsonMode);
    UiLogger.setColorEnabled(originalColorEnabled);
    Logger.clearContext();
  }

  @Test
  void defaultFlagsAreEnabledAndBannerDependsOnJsonMode() {
    BaseCliCommand command = noOpCommand();
    assertThat(command.shouldLoadConfig()).isTrue();
    assertThat(command.shouldResolveProjectRoot()).isTrue();
    assertThat(command.shouldApplyProjectRootToConfig()).isTrue();
    assertThat(command.shouldValidateProjectRoot()).isTrue();

    UiLogger.setJsonMode(false);
    assertThat(command.shouldDisplayStartupBanner(new Config(), Path.of("."))).isTrue();

    UiLogger.setJsonMode(true);
    assertThat(command.shouldDisplayStartupBanner(new Config(), Path.of("."))).isFalse();
  }

  @Test
  void rememberResolvedConfigPathNormalizesAndCanBeCleared() {
    BaseCliCommand command = noOpCommand();
    command.rememberResolvedConfigPath(Path.of("tmp", "..", "config.json"));
    assertThat(command.getResolvedConfigPath())
        .isEqualTo(Path.of("tmp", "..", "config.json").toAbsolutePath().normalize());

    command.rememberResolvedConfigPath(null);
    assertThat(command.getResolvedConfigPath()).isNull();
  }

  @Test
  void loadConfigUsesLoaderAndStoresResolvedConfigPath() {
    Config loadedConfig = new Config();
    RecordingConfigLoader loader =
        new RecordingConfigLoader(Path.of("custom-config.json"), loadedConfig);
    BaseCliCommand command =
        new BaseCliCommand() {
          @Override
          protected Integer doCall(Config config, Path projectRoot) {
            return 0;
          }

          @Override
          protected ConfigLoaderPort createConfigLoader() {
            return loader;
          }
        };

    Config result = command.loadConfig(Path.of("."));

    assertThat(result).isSameAs(loadedConfig);
    assertThat(loader.getResolveConfigPathInput()).isNull();
    assertThat(loader.getLoadPathInput()).isEqualTo(Path.of("custom-config.json"));
    assertThat(loader.getLoadOverrides()).hasSize(1);
    assertThat(command.getResolvedConfigPath())
        .isEqualTo(Path.of("custom-config.json").toAbsolutePath().normalize());
  }

  @Test
  void createConfigLoaderAndSingleArgumentResolveProjectRootUseDefaultBehavior() {
    BaseCliCommand command = noOpCommand();

    assertThat(command.createConfigLoader()).isNotNull();
    assertThat(command.resolveProjectRoot(new Config())).isEqualTo(Path.of("."));
  }

  @Test
  void loadConfigReturnsDefaultWhenDisabled() {
    BaseCliCommand command =
        new BaseCliCommand() {
          @Override
          protected Integer doCall(Config config, Path projectRoot) {
            return 0;
          }

          @Override
          protected boolean shouldLoadConfig() {
            return false;
          }

          @Override
          protected ConfigLoaderPort createConfigLoader() {
            throw new AssertionError("createConfigLoader must not be called");
          }
        };

    Config config = command.loadConfig(Path.of("."));

    assertThat(config).isNotNull();
    assertThat(command.getResolvedConfigPath()).isNull();
  }

  @Test
  void buildConfigOverridesSkipsNullCommonOverrides() {
    BaseCliCommand command =
        new BaseCliCommand() {
          @Override
          protected Integer doCall(Config config, Path projectRoot) {
            return 0;
          }

          @Override
          protected CommonOverrides buildCommonOverrides() {
            return null;
          }
        };

    assertThat(command.buildConfigOverrides(Path.of("."))).isEmpty();
  }

  @Test
  void formatExceptionMessageUsesMessageOrExceptionToString() {
    BaseCliCommand command = noOpCommand();
    RuntimeException nullMessageException = new RuntimeException((String) null);
    RuntimeException blankMessageException = new RuntimeException(" ");

    assertThat(command.formatExceptionMessage(new RuntimeException("visible-message")))
        .isEqualTo("visible-message");
    assertThat(command.formatExceptionMessage(nullMessageException))
        .isEqualTo(nullMessageException.toString());
    assertThat(command.formatExceptionMessage(blankMessageException))
        .isEqualTo(blankMessageException.toString());
  }

  @Test
  void getErrWriterUsesSpecWriterWhenAvailable() {
    BaseCliCommand command = new PicocliReadyCommand();
    CommandLine commandLine = new CommandLine(command);
    StringWriter errBuffer = new StringWriter();
    commandLine.setErr(new PrintWriter(errBuffer, true));
    command.spec = commandLine.getCommandSpec();

    PrintWriter writer = command.getErrWriter();
    writer.println("from-spec");
    writer.flush();

    assertThat(errBuffer.toString()).contains("from-spec");
  }

  @Test
  void getErrWriterFallsBackWhenSpecIsMissing() {
    assertThat(noOpCommand().getErrWriter()).isNotNull();
  }

  @Test
  void handleExceptionMasksSensitiveValuesAndPrintsStackTraceInVerboseMode() {
    StringWriter errBuffer = new StringWriter();
    BaseCliCommand command =
        new BaseCliCommand() {
          @Override
          protected Integer doCall(Config config, Path projectRoot) {
            return 0;
          }

          @Override
          protected boolean isVerboseEnabled() {
            return true;
          }

          @Override
          protected PrintWriter getErrWriter() {
            return new PrintWriter(errBuffer, true);
          }
        };

    int exitCode =
        command.handleException(
            new RuntimeException("token=super-secret-value"), new Config(), null);

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    assertThat(errBuffer.toString()).contains("ERROR: token=****");
    assertThat(errBuffer.toString()).contains("RuntimeException");
  }

  @Test
  void handleExceptionOmitsStackTraceWhenVerboseDisabled() {
    StringWriter errBuffer = new StringWriter();
    BaseCliCommand command =
        new BaseCliCommand() {
          @Override
          protected Integer doCall(Config config, Path projectRoot) {
            return 0;
          }

          @Override
          protected PrintWriter getErrWriter() {
            return new PrintWriter(errBuffer, true);
          }
        };

    int exitCode =
        command.handleException(
            new RuntimeException("token=super-secret-value"), new Config(), null);

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    assertThat(errBuffer.toString()).contains("ERROR: token=****");
    assertThat(errBuffer.toString()).doesNotContain("\tat ");
  }

  @Test
  void callUsesDefaultConfigWhenLoadConfigReturnsNullAndProjectRootResolutionIsDisabled() {
    AtomicReference<Config> capturedConfig = new AtomicReference<>();
    AtomicReference<Path> capturedProjectRoot = new AtomicReference<>();
    BaseCliCommand command =
        new BaseCliCommand() {
          @Override
          protected Integer doCall(Config config, Path projectRoot) {
            capturedConfig.set(config);
            capturedProjectRoot.set(projectRoot);
            return 17;
          }

          @Override
          protected Config loadConfig(Path projectRoot) {
            return null;
          }

          @Override
          protected boolean shouldResolveProjectRoot() {
            return false;
          }

          @Override
          protected boolean shouldDisplayStartupBanner(Config config, Path projectRoot) {
            return false;
          }
        };

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(17);
    assertThat(capturedProjectRoot.get()).isNull();
    assertThat(capturedConfig.get()).isNotNull();
    assertThat(capturedConfig.get().getExecution()).isNotNull();
    assertThat(capturedConfig.get().getExecution().getLogsRoot())
        .isEqualTo(expectedExecutionRunsRoot());
  }

  @Test
  void callRethrowsParameterException() {
    BaseCliCommand command =
        new BaseCliCommand() {
          @Override
          protected Integer doCall(Config config, Path projectRoot) {
            throw new CommandLine.ParameterException(
                new CommandLine(new PicocliReadyCommand()), "bad parameter");
          }

          @Override
          protected Config loadConfig(Path projectRoot) {
            return new Config();
          }

          @Override
          protected boolean shouldDisplayStartupBanner(Config config, Path projectRoot) {
            return false;
          }
        };

    assertThatThrownBy(command::call)
        .isInstanceOf(CommandLine.ParameterException.class)
        .hasMessageContaining("bad parameter");
  }

  @Test
  void callAppliesResolvedProjectRootToConfig(@TempDir Path tempDir) {
    AtomicReference<Config> capturedConfig = new AtomicReference<>();
    BaseCliCommand command =
        new BaseCliCommand() {
          @Override
          protected Integer doCall(Config config, Path projectRoot) {
            capturedConfig.set(config);
            return 0;
          }

          @Override
          protected Config loadConfig(Path projectRoot) {
            return new Config();
          }

          @Override
          protected Path resolveProjectRootFromCli() {
            return tempDir;
          }

          @Override
          protected boolean shouldDisplayStartupBanner(Config config, Path projectRoot) {
            return false;
          }
        };

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(0);
    assertThat(capturedConfig.get()).isNotNull();
    assertThat(capturedConfig.get().getProject()).isNotNull();
    assertThat(capturedConfig.get().getProject().getRoot()).isEqualTo(tempDir.toString());
  }

  @Test
  void startupBannerUsesResolvedConfigPathAndProjectRootFromConfigWhenCommandRootIsNull() {
    BaseCliCommand command = noOpCommand();
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    Path configuredRoot = Path.of("subdir");
    projectConfig.setRoot(configuredRoot.toString());
    config.setProject(projectConfig);
    command.rememberResolvedConfigPath(Path.of("cfg.json"));

    String output = captureStdout(() -> command.printStartupBanner(config, null));

    assertThat(output)
        .contains("config:    " + StartupBannerSupport.formatPath(command.getResolvedConfigPath()));
    assertThat(output)
        .contains("directory: " + StartupBannerSupport.formatDirectory(configuredRoot));
  }

  @Test
  void startupBannerFallsBackToCurrentDirectoryWhenConfiguredRootIsMalformed() {
    BaseCliCommand command = noOpCommand();
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setRoot("\0invalid-path");
    config.setProject(projectConfig);
    command.rememberResolvedConfigPath(null);

    String output = captureStdout(() -> command.printStartupBanner(config, null));

    assertThat(output).contains("directory: " + StartupBannerSupport.formatDirectory(Path.of(".")));
    assertThat(output)
        .contains("config:    " + StartupBannerSupport.formatPath(Path.of("config.json")));
  }

  @Test
  void startupBannerUsesExplicitProjectRootWhenProvided() {
    BaseCliCommand command = noOpCommand();
    Path explicitRoot = Path.of("x");

    String output = captureStdout(() -> command.printStartupBanner(new Config(), explicitRoot));

    assertThat(output).contains("directory: " + StartupBannerSupport.formatDirectory(explicitRoot));
  }

  @Test
  void startupBannerMetadataUsesConfigValuesAndFallbacks() {
    BaseCliCommand command = noOpCommand();
    Config config = new Config();
    config.setAppName("  custom-app  ");
    config.setVersion("  1.2.3  ");
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setModelName("  gpt-test  ");
    config.setLlm(llmConfig);

    assertThat(command.resolveStartupBannerApplicationName(config)).isEqualTo("custom-app");
    assertThat(command.resolveStartupBannerApplicationVersion(config)).isEqualTo("1.2.3");
    assertThat(command.resolveStartupBannerModelName(config)).isEqualTo("gpt-test");
    assertThat(command.resolveStartupBannerApplicationName(new Config()))
        .isEqualTo(StartupBannerSupport.resolveApplicationName());
    assertThat(command.resolveStartupBannerApplicationVersion(new Config()))
        .isEqualTo(StartupBannerSupport.resolveApplicationVersion());
    assertThat(command.resolveStartupBannerModelName(new Config())).isEqualTo("unknown");
    assertThat(command.resolveStartupBannerApplicationName(null))
        .isEqualTo(StartupBannerSupport.resolveApplicationName());
    assertThat(command.resolveStartupBannerApplicationVersion(null))
        .isEqualTo(StartupBannerSupport.resolveApplicationVersion());
    assertThat(command.resolveStartupBannerModelName(null)).isEqualTo("unknown");
  }

  @Test
  void validateProjectRootThrowsWithAndWithoutSpec(@TempDir Path tempDir) {
    Path missingDir = tempDir.resolve("missing-dir");

    BaseCliCommand noSpecCommand = noOpCommand();
    assertThatThrownBy(() -> noSpecCommand.validateProjectRoot(missingDir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Project root must be an existing directory");

    BaseCliCommand withSpecCommand = new PicocliReadyCommand();
    CommandLine commandLine = new CommandLine(withSpecCommand);
    withSpecCommand.spec = commandLine.getCommandSpec();
    assertThatThrownBy(() -> withSpecCommand.validateProjectRoot(missingDir))
        .isInstanceOf(CommandLine.ParameterException.class)
        .hasMessageContaining("Project root must be an existing directory");
  }

  @Test
  void resolveProjectConfigPathPrefersPrimaryThenFallback(@TempDir Path tempDir) throws Exception {
    BaseCliCommand command = noOpCommand();
    Path fallbackConfig = tempDir.resolve(".ful").resolve("config.json");
    Files.createDirectories(fallbackConfig.getParent());
    Files.writeString(fallbackConfig, "{}");

    assertThat(command.resolveProjectConfigPath(tempDir)).isEqualTo(fallbackConfig);

    Path primaryConfig = tempDir.resolve("config.json");
    Files.writeString(primaryConfig, "{}");
    assertThat(command.resolveProjectConfigPath(tempDir)).isEqualTo(primaryConfig);
  }

  private static BaseCliCommand noOpCommand() {
    return new BaseCliCommand() {
      @Override
      protected Integer doCall(Config config, Path projectRoot) {
        return 0;
      }
    };
  }

  @Command(name = "picocli-ready")
  private static final class PicocliReadyCommand extends BaseCliCommand {
    @Override
    protected Integer doCall(Config config, Path projectRoot) {
      return 0;
    }
  }

  private static String expectedExecutionRunsRoot() {
    return Path.of(".")
        .toAbsolutePath()
        .normalize()
        .resolve(".ful")
        .resolve("runs")
        .normalize()
        .toString();
  }

  private String captureStdout(Runnable action) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    UiLogger.setOutput(
        new PrintStream(stdout, true, StandardCharsets.UTF_8),
        new PrintStream(stderr, true, StandardCharsets.UTF_8));
    action.run();
    return stdout.toString(StandardCharsets.UTF_8);
  }

  private static final class RecordingConfigLoader extends ConfigLoaderImpl {
    private final Path resolvedPath;
    private final Config loadedConfig;

    private Path resolveConfigPathInput;
    private Path loadPathInput;
    private ConfigOverride[] loadOverrides = new ConfigOverride[0];

    RecordingConfigLoader(Path resolvedPath, Config loadedConfig) {
      this.resolvedPath = resolvedPath;
      this.loadedConfig = loadedConfig;
    }

    @Override
    public Path resolveConfigPath(Path explicitPath) {
      this.resolveConfigPathInput = explicitPath;
      return resolvedPath;
    }

    @Override
    public Config load(Path configFile, ConfigOverride... overrides) {
      this.loadPathInput = configFile;
      this.loadOverrides = overrides;
      return loadedConfig;
    }

    @Override
    public Config load(Path configFile) {
      return load(configFile, new ConfigOverride[0]);
    }

    @Override
    public void applyOverrides(Config config, ConfigOverride... overrides) {
      // no-op for test
    }

    Path getResolveConfigPathInput() {
      return resolveConfigPathInput;
    }

    Path getLoadPathInput() {
      return loadPathInput;
    }

    ConfigOverride[] getLoadOverrides() {
      return loadOverrides;
    }
  }
}
