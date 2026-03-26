package com.craftsmanbro.fulcraft.ui.cli.command.support;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import com.craftsmanbro.fulcraft.ui.tui.TuiApplication;
import java.io.IOException;
import java.util.function.Consumer;
import picocli.CommandLine;

/** Shared helper for launching TUI commands with consistent lifecycle handling. */
public final class TuiCommandSupport {

  private TuiCommandSupport() {}

  @FunctionalInterface
  public interface AppRunner {

    void run(TuiApplication app) throws IOException, InterruptedException;
  }

  public static int launch(
      final TuiApplication app,
      final Consumer<TuiApplication> appInitializer,
      final String exitedNormallyMessage,
      final String errorMessage) {
    return launch(
        app,
        appInitializer,
        exitedNormallyMessage,
        errorMessage,
        TuiCommandSupport::runApplication);
  }

  public static int launch(
      final TuiApplication app,
      final Consumer<TuiApplication> appInitializer,
      final String exitedNormallyMessage,
      final String errorMessage,
      final AppRunner appRunner) {
    try (app) {
      if (appInitializer != null) {
        appInitializer.accept(app);
      }
      final Thread.UncaughtExceptionHandler previousHandler = installGlobalExceptionHandler(app);
      final Thread shutdownHook = createShutdownHook(app);
      Runtime.getRuntime().addShutdownHook(shutdownHook);
      try {
        return executeWithExceptionHandling(app, exitedNormallyMessage, errorMessage, appRunner);
      } finally {
        restoreGlobalExceptionHandler(previousHandler);
        removeShutdownHook(shutdownHook);
      }
    } catch (RuntimeException e) {
      UiLogger.error(errorMessage, e);
      return CommandLine.ExitCode.SOFTWARE;
    }
  }

  private static int executeWithExceptionHandling(
      final TuiApplication app,
      final String exitedNormallyMessage,
      final String errorMessage,
      final AppRunner appRunner) {
    try {
      appRunner.run(app);
      UiLogger.info(exitedNormallyMessage);
      return CommandLine.ExitCode.OK;
    } catch (IOException | InterruptedException | RuntimeException e) {
      UiLogger.error(errorMessage, e);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return CommandLine.ExitCode.SOFTWARE;
    }
  }

  private static void runApplication(final TuiApplication app) throws IOException {
    try {
      app.init();
      app.run();
    } catch (Exception e) {
      app.handleFatalError(e);
      rethrowAsOriginalOrIo(e);
    }
  }

  private static void rethrowAsOriginalOrIo(final Exception exception) throws IOException {
    if (exception instanceof IOException ioException) {
      throw ioException;
    }
    if (exception instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    throw new IOException(MessageSource.getMessage("tui.error.unexpected"), exception);
  }

  private static Thread createShutdownHook(final TuiApplication app) {
    return new Thread(app::handleInterrupt, "tui-shutdown");
  }

  private static Thread.UncaughtExceptionHandler installGlobalExceptionHandler(
      final TuiApplication app) {
    final Thread.UncaughtExceptionHandler previousHandler =
        Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, error) -> {
          try {
            app.handleFatalError(error);
          } catch (Exception e) {
            UiLogger.error(MessageSource.getMessage("tui.error.handle_fatal_failed"), e);
          }
          if (previousHandler != null) {
            previousHandler.uncaughtException(thread, error);
          }
        });
    return previousHandler;
  }

  private static void restoreGlobalExceptionHandler(
      final Thread.UncaughtExceptionHandler previousHandler) {
    Thread.setDefaultUncaughtExceptionHandler(previousHandler);
  }

  private static void removeShutdownHook(final Thread shutdownHook) {
    try {
      Runtime.getRuntime().removeShutdownHook(shutdownHook);
    } catch (IllegalStateException e) {
      UiLogger.debug(MessageSource.getMessage("tui.debug.shutdown_hook_skip"));
    }
  }
}
