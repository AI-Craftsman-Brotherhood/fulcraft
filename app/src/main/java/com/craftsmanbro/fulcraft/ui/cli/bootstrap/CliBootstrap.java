package com.craftsmanbro.fulcraft.ui.cli.bootstrap;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.EnvironmentLogger;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.KernelLoggerFactoryAdapter;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.logging.LoggerPortProvider;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/** Shared bootstrap helpers used by the Main entrypoint. */
public final class CliBootstrap {

  private static final String KEY_CLI_ERROR_UNKNOWN = "cli.error.unknown";
  private static final String KEY_CLI_ERROR_EXECUTION_FAILED = "cli.error.execution_failed";

  private CliBootstrap() {}

  public static void initializeLogging() {
    LoggerPortProvider.setFactory(new KernelLoggerFactoryAdapter());
  }

  public static void logStartupEnvironment() {
    EnvironmentLogger.logStartupEnvironment();
  }

  public static String logExecutionFailure(final Exception exception) {
    final ResourceBundle bundle = CliLocaleSupport.getMessages();
    final String rawMessage = exception == null ? null : exception.getMessage();
    final String errorDetail =
        rawMessage != null && !rawMessage.isBlank()
            ? rawMessage
            : bundle.getString(KEY_CLI_ERROR_UNKNOWN);
    Logger.error(
        MessageFormat.format(bundle.getString(KEY_CLI_ERROR_EXECUTION_FAILED), errorDetail));
    return errorDetail;
  }
}
