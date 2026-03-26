package com.craftsmanbro.fulcraft;

import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.config.ConfigPathResolver;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.telemetry.contract.TelemetryPort;
import com.craftsmanbro.fulcraft.infrastructure.telemetry.impl.Telemetry;
import com.craftsmanbro.fulcraft.ui.cli.CliContext;
import com.craftsmanbro.fulcraft.ui.cli.bootstrap.CliBootstrap;
import com.craftsmanbro.fulcraft.ui.cli.bootstrap.CliVersionProvider;
import com.craftsmanbro.fulcraft.ui.cli.bootstrap.CommandLineFactory;
import com.craftsmanbro.fulcraft.ui.cli.wiring.DefaultServiceFactory;
import com.craftsmanbro.fulcraft.ui.cli.wiring.ServiceFactory;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.nio.file.Path;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Entry point for the Fulcraft CLI.
 *
 * <p>Bootstraps logging, telemetry, localization, and command wiring before delegating execution to
 * Picocli.
 */
@Command(
    name = "ful",
    mixinStandardHelpOptions = true,
    versionProvider = CliVersionProvider.class,
    resourceBundle = "messages",
    description = "${cli.description}",
    footer = "${cli.footer}")
public class Main implements Callable<Integer>, CliContext {

  private static final String SPAN_NAME = "fulcraft";
  private static final String ATTR_EXIT_CODE = "exit.code";

  @Spec private CommandSpec spec;

  @Option(
      names = {"-c", "--config"},
      description = "${option.config.description}",
      defaultValue = ConfigPathResolver.DEFAULT_CONFIG_FILE_NAME)
  private Path configFile;

  @Option(
      names = {"-l", "--lang"},
      description = "${option.lang.description}",
      paramLabel = "<lang>")
  private String languageTag;

  private final ConfigLoaderPort configLoader;
  private final ServiceFactory services;
  private final TelemetryPort telemetry;

  public Main() {
    this(Telemetry.getInstance());
  }

  private Main(final TelemetryPort telemetry) {
    this(new DefaultServiceFactory(telemetry.getTracer()), new ConfigLoaderImpl(), telemetry);
  }

  Main(
      final ServiceFactory services,
      final ConfigLoaderPort configLoader,
      final TelemetryPort telemetry) {
    this.services = services;
    this.configLoader = configLoader;
    this.telemetry = telemetry;
  }

  public Tracer getTracer() {
    return telemetry.getTracer();
  }

  /**
   * Launches the CLI process.
   *
   * @param args raw command-line arguments
   */
  public static void main(final String[] args) {
    System.exit(run(args));
  }

  /**
   * Internal execution entry point.
   *
   * <p>Sets up the execution environment, including logging and telemetry, before parsing and
   * executing the given command-line arguments. Returns an appropriate exit code.
   *
   * @param args the command-line arguments
   * @return the exit code of the CLI execution
   */
  static int run(final String[] args) {
    // Initialize logging early to ensure startup and error logs are captured
    CliBootstrap.initializeLogging();
    try (TelemetryPort telemetry = Telemetry.getInstance()) {
      final var tracer = telemetry.getTracer();

      // Start a global span for the entire CLI execution
      final var span = tracer.spanBuilder(SPAN_NAME).startSpan();
      try (var scope = span.makeCurrent()) {
        Objects.requireNonNull(scope, "telemetry scope");

        // Log environment details for debugging purposes
        CliBootstrap.logStartupEnvironment();

        // Construct and execute the Picocli command
        final CommandLine commandLine = createCommandLine(args);
        final int exitCode = commandLine.execute(args);

        span.setAttribute(ATTR_EXIT_CODE, exitCode);
        return exitCode;
      } catch (Exception exception) {
        // Record any unhandled exceptions that propagated to the top level
        span.recordException(exception);
        final String errorDetail = CliBootstrap.logExecutionFailure(exception);
        span.setStatus(StatusCode.ERROR, Objects.requireNonNull(errorDetail));
        return 1;
      } finally {
        span.end();
      }
    }
  }

  /**
   * Creates and configures the Picocli entry point.
   *
   * <p>Applies locale pre-processing, registers subcommands through {@link ServiceLoader}, and
   * installs shared execution and error-handling hooks.
   *
   * @return a fully configured command line
   */
  public static CommandLine createCommandLine(final String... args) {
    return new CommandLineFactory().create(new Main(), args);
  }

  @Override
  public Integer call() {
    Logger.stdout(spec.commandLine().getUsageMessage());
    return CommandLine.ExitCode.USAGE;
  }

  @Override
  public ServiceFactory getServices() {
    return services;
  }

  @Override
  public ConfigLoaderPort getConfigLoader() {
    return configLoader;
  }

  @Override
  public Path getConfigFile() {
    return configFile;
  }

  @Override
  public String getLanguageTag() {
    return languageTag;
  }
}
