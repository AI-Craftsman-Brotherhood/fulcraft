package com.craftsmanbro.fulcraft.ui.cli.bootstrap;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.ui.cli.CliContext;
import com.craftsmanbro.fulcraft.ui.cli.spi.CliCommand;
import java.util.Iterator;
import java.util.ResourceBundle;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import picocli.CommandLine;

public final class CommandLineFactory {

  private static final String KEY_CLI_ERROR_UNKNOWN = "cli.error.unknown";
  private static final String KEY_CLI_WARN_COMMAND_LOAD_FAILED = "cli.warn.command_load_failed";

  public CommandLine create(final CliContext rootCommand, final String... args) {
    CliLocaleSupport.applyFromArgs(args);
    final CommandLine commandLine = new CommandLine(rootCommand);
    registerCliSubcommands(commandLine, ServiceLoader.load(CliCommand.class).iterator());
    applyResourceBundle(commandLine);
    installExecutionStrategy(commandLine, rootCommand);
    installParameterExceptionHandler(commandLine);
    return commandLine;
  }

  public static void registerCliSubcommands(
      final CommandLine commandLine, final Iterator<CliCommand> iterator) {
    while (true) {
      try {
        if (!iterator.hasNext()) {
          return;
        }
        commandLine.addSubcommand(iterator.next());
      } catch (ServiceConfigurationError error) {
        final String detail = error.getMessage();
        Logger.warn(
            MessageSource.getMessage(
                KEY_CLI_WARN_COMMAND_LOAD_FAILED,
                (detail == null || detail.isBlank())
                    ? MessageSource.getMessage(KEY_CLI_ERROR_UNKNOWN)
                    : detail));
      }
    }
  }

  public static String resolveParameterErrorMessage(
      final CommandLine.ParameterException exception) {
    if (exception == null) {
      return MessageSource.getMessage(KEY_CLI_ERROR_UNKNOWN);
    }
    final Throwable cause = exception.getCause();
    if (cause instanceof CommandLine.ParameterException nested) {
      final String nestedMessage = nested.getMessage();
      if (nestedMessage != null && !nestedMessage.isBlank()) {
        return nestedMessage;
      }
    }
    final String message = exception.getMessage();
    if (message != null && !message.isBlank()) {
      return message;
    }
    return MessageSource.getMessage(KEY_CLI_ERROR_UNKNOWN);
  }

  private void applyResourceBundle(final CommandLine commandLine) {
    final ResourceBundle bundle = CliLocaleSupport.getMessages();
    commandLine.setResourceBundle(bundle);
    for (final CommandLine subcommand : commandLine.getSubcommands().values()) {
      final ResourceBundle current = subcommand.getResourceBundle();
      if (current == null || CliLocaleSupport.MESSAGE_BUNDLE.equals(current.getBaseBundleName())) {
        subcommand.setResourceBundle(bundle);
      }
    }
  }

  private void installExecutionStrategy(
      final CommandLine commandLine, final CliContext rootCommand) {
    final CommandLine.IExecutionStrategy executionStrategy =
        commandLine.getExecutionStrategy() != null
            ? commandLine.getExecutionStrategy()
            : new CommandLine.RunLast();
    commandLine.setExecutionStrategy(
        parseResult -> {
          if (CliLocaleSupport.applyOption(rootCommand.getLanguageTag())) {
            applyResourceBundle(commandLine);
          }
          return executionStrategy.execute(parseResult);
        });
  }

  private void installParameterExceptionHandler(final CommandLine commandLine) {
    commandLine.setParameterExceptionHandler(
        (exception, args) -> {
          final CommandLine failedCommand = exception.getCommandLine();
          if (failedCommand == null) {
            return CommandLine.ExitCode.USAGE;
          }
          failedCommand.getErr().println(resolveParameterErrorMessage(exception));
          failedCommand.usage(failedCommand.getErr());
          return failedCommand.getCommandSpec().exitCodeOnInvalidInput();
        });
  }
}
