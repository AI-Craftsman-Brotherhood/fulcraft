package com.craftsmanbro.fulcraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.config.ConfigPathResolver;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.telemetry.contract.TelemetryPort;
import com.craftsmanbro.fulcraft.infrastructure.telemetry.impl.Telemetry;
import com.craftsmanbro.fulcraft.ui.cli.bootstrap.CommandLineFactory;
import com.craftsmanbro.fulcraft.ui.cli.spi.CliCommand;
import com.craftsmanbro.fulcraft.ui.cli.wiring.ServiceFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.ServiceConfigurationError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Command;

class MainCoverageTest {

  private Locale originalLocale;

  @BeforeEach
  void setUp() {
    originalLocale = Locale.getDefault();
    MessageSource.setLocale(originalLocale);
  }

  @AfterEach
  void tearDown() {
    Locale.setDefault(originalLocale);
    MessageSource.setLocale(originalLocale);
  }

  @Test
  void getTracer_returnsTracerFromTelemetry() {
    TelemetryPort telemetry = Telemetry.getInstance();
    Main main = new Main(mock(ServiceFactory.class), mock(ConfigLoaderPort.class), telemetry);

    assertThat(main.getTracer()).isSameAs(telemetry.getTracer());
  }

  @Test
  void call_returnsUsageExitCodeWhenNoSubcommandIsSpecified() {
    Main main =
        new Main(mock(ServiceFactory.class), mock(ConfigLoaderPort.class), Telemetry.getInstance());
    CommandLine cmd = new CommandLine(main);

    int exitCode = cmd.execute();

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
  }

  @Test
  void getServices_returnsInjectedServiceFactory() {
    ServiceFactory services = mock(ServiceFactory.class);
    Main main = new Main(services, mock(ConfigLoaderPort.class), Telemetry.getInstance());

    assertThat(main.getServices()).isSameAs(services);
  }

  @Test
  void getConfigFile_returnsParsedConfigPath() {
    Main main =
        new Main(mock(ServiceFactory.class), mock(ConfigLoaderPort.class), Telemetry.getInstance());
    Path configPath = Path.of("custom-config.json");

    new CommandLine(main).parseArgs("--config", configPath.toString());

    assertThat(main.getConfigFile()).isEqualTo(configPath);
  }

  @Test
  void configPathResolver_usesExplicitConfigPathWhenFileExists(@TempDir Path tempDir)
      throws IOException {
    ConfigLoaderPort configLoader = mock(ConfigLoaderPort.class);
    Main main = new Main(mock(ServiceFactory.class), configLoader, Telemetry.getInstance());
    Path configPath = tempDir.resolve("config.json");
    Files.writeString(configPath, "{}");
    Config expected = new Config();
    when(configLoader.load(configPath)).thenReturn(expected);
    new CommandLine(main).parseArgs("--config", configPath.toString());

    Path resolved = ConfigPathResolver.resolve(main.getConfigFile());
    Config actual = configLoader.load(resolved);

    assertThat(actual).isSameAs(expected);
    verify(configLoader).load(configPath);
  }

  @Test
  void configPathResolver_usesDotFulFallbackWhenExplicitConfigIsMissing(@TempDir Path tempDir)
      throws IOException {
    ConfigLoaderPort configLoader = mock(ConfigLoaderPort.class);
    Main main = new Main(mock(ServiceFactory.class), configLoader, Telemetry.getInstance());
    Path missingConfig = tempDir.resolve("missing-config.json");
    new CommandLine(main).parseArgs("--config", missingConfig.toString());

    Path fallbackDir = Path.of(".ful");
    Path fallbackConfig = fallbackDir.resolve("config.json");
    boolean fallbackDirExisted = Files.exists(fallbackDir);
    boolean fallbackConfigExisted = Files.exists(fallbackConfig);
    byte[] original = fallbackConfigExisted ? Files.readAllBytes(fallbackConfig) : null;

    Files.createDirectories(fallbackDir);
    Files.writeString(fallbackConfig, "{}");

    Config expected = new Config();
    when(configLoader.load(Path.of(".ful", "config.json"))).thenReturn(expected);

    try {
      Path resolved = ConfigPathResolver.resolve(main.getConfigFile());
      Config actual = configLoader.load(resolved);

      assertThat(actual).isSameAs(expected);
      verify(configLoader).load(Path.of(".ful", "config.json"));
    } finally {
      if (fallbackConfigExisted) {
        Files.write(fallbackConfig, original);
      } else {
        Files.deleteIfExists(fallbackConfig);
      }
      if (!fallbackDirExisted) {
        Files.deleteIfExists(fallbackDir);
      }
    }
  }

  @Test
  void createCommandLine_appliesLocaleFromLongEqualsOption() {
    Locale.setDefault(Locale.ENGLISH);
    MessageSource.setLocale(Locale.ENGLISH);

    Main.createCommandLine("--lang=ja");

    assertThat(Locale.getDefault().getLanguage()).isEqualTo("ja");
  }

  @Test
  void createCommandLine_appliesLocaleFromCompactShortOption() {
    Locale.setDefault(Locale.ENGLISH);
    MessageSource.setLocale(Locale.ENGLISH);

    Main.createCommandLine("-lja");

    assertThat(Locale.getDefault().getLanguage()).isEqualTo("ja");
  }

  @Test
  void createCommandLine_keepsLocaleWhenLanguageTagIsUnd() {
    Locale.setDefault(Locale.ENGLISH);
    MessageSource.setLocale(Locale.ENGLISH);

    Main.createCommandLine("--lang=und");

    assertThat(Locale.getDefault()).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void createCommandLine_keepsLocaleWhenLanguageValueIsMissing() {
    Locale.setDefault(Locale.ENGLISH);
    MessageSource.setLocale(Locale.ENGLISH);

    Main.createCommandLine("--lang");

    assertThat(Locale.getDefault()).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void createCommandLine_handlesNullArgumentToken() {
    Locale.setDefault(Locale.ENGLISH);
    MessageSource.setLocale(Locale.ENGLISH);

    Main.createCommandLine((String) null);

    assertThat(Locale.getDefault()).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void createCommandLine_handlesNullArgsArray() {
    Locale.setDefault(Locale.ENGLISH);
    MessageSource.setLocale(Locale.ENGLISH);

    Main.createCommandLine((String[]) null);

    assertThat(Locale.getDefault()).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void registerCliSubcommands_skipsServiceConfigurationErrors() {
    CommandLine cmd =
        new CommandLine(
            new Main(
                mock(ServiceFactory.class), mock(ConfigLoaderPort.class), Telemetry.getInstance()));
    Iterator<CliCommand> iterator =
        new Iterator<>() {
          private int index;

          @Override
          public boolean hasNext() {
            return index < 2;
          }

          @Override
          public CliCommand next() {
            if (index++ == 0) {
              throw new ServiceConfigurationError("broken provider");
            }
            return new SampleCliCommand();
          }
        };

    CommandLineFactory.registerCliSubcommands(cmd, iterator);

    assertThat(cmd.getSubcommands()).containsKey("sample-cli");
  }

  @Test
  void parameterExceptionHandler_returnsUsageWhenCommandLineIsNull() throws Exception {
    CommandLine cmd = Main.createCommandLine();
    CommandLine.IParameterExceptionHandler handler = cmd.getParameterExceptionHandler();
    CommandLine.ParameterException exception = mock(CommandLine.ParameterException.class);
    when(exception.getCommandLine()).thenReturn(null);

    int exitCode = handler.handleParseException(exception, new String[0]);

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
  }

  @Test
  void resolveParameterErrorMessage_returnsUnknownWhenExceptionIsNull() {
    String actual = CommandLineFactory.resolveParameterErrorMessage(null);

    assertThat(actual).isEqualTo(MessageSource.getMessage("cli.error.unknown"));
  }

  @Test
  void resolveParameterErrorMessage_returnsUnknownWhenMessagesAreBlank() {
    CommandLine.ParameterException nested = mock(CommandLine.ParameterException.class);
    when(nested.getMessage()).thenReturn(" ");
    CommandLine.ParameterException exception = mock(CommandLine.ParameterException.class);
    when(exception.getCause()).thenReturn(nested);
    when(exception.getMessage()).thenReturn(" ");

    String actual = CommandLineFactory.resolveParameterErrorMessage(exception);

    assertThat(actual).isEqualTo(MessageSource.getMessage("cli.error.unknown"));
  }

  @Command(name = "sample-cli")
  private static final class SampleCliCommand implements CliCommand {

    @Override
    public Integer call() {
      return CommandLine.ExitCode.OK;
    }
  }
}
