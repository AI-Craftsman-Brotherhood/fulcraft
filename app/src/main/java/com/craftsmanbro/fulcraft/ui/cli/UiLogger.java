package com.craftsmanbro.fulcraft.ui.cli;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.PrintStream;
import java.nio.file.Path;

/** UI-facing logging facade to avoid direct infrastructure logging dependencies. */
public final class UiLogger {

  private UiLogger() {}

  public static void initialize(final Config config) {
    Logger.initialize(config);
  }

  public static void configureRunLogging(
      final Config config, final Path runDirectory, final String runId) {
    Logger.configureRunLogging(config, runDirectory, runId);
  }

  public static void setOutput(final PrintStream stdout, final PrintStream stderr) {
    Logger.setOutput(stdout, stderr);
  }

  public static void setJsonMode(final boolean enabled) {
    Logger.setJsonMode(enabled);
  }

  public static void setColorEnabled(final boolean enabled) {
    Logger.setColorEnabled(enabled);
  }

  public static boolean isColorEnabled() {
    return Logger.isColorEnabled();
  }

  public static boolean isJsonMode() {
    return Logger.isJsonMode();
  }

  public static void stdout(final String message) {
    Logger.stdout(message);
  }

  public static void stdoutInline(final String message) {
    Logger.stdoutInline(message);
  }

  public static void stdoutWarn(final String message) {
    Logger.stdoutWarn(message);
  }

  public static void stderr(final String message) {
    Logger.stderr(message);
  }

  public static void debug(final String message) {
    Logger.debug(message);
  }

  public static void info(final String message) {
    Logger.info(message);
  }

  public static void warn(final String message) {
    Logger.warn(message);
  }

  public static void warn(final String message, final Throwable t) {
    Logger.warn(message, t);
  }

  public static void error(final String message) {
    Logger.error(message);
  }

  public static void error(final String message, final Throwable t) {
    Logger.error(message, t);
  }
}
