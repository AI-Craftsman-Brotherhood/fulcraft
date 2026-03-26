package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath;

import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.ClasspathResolutionResult;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.util.List;
import java.util.stream.Collectors;

public final class ClasspathResolutionLogger {

  private static final String KEY_NO_ATTEMPTS = "classpath:resolution:none";

  private static final String KEY_FAILED = "classpath:resolution:failed";

  private static final String UNKNOWN_TOOL = "unknown";

  private ClasspathResolutionLogger() {}

  public static void logAttempts(final ClasspathResolutionResult result) {
    if (result == null) {
      return;
    }
    final List<ClasspathResolutionResult.Attempt> resolutionAttempts = result.getAttempts();
    if (!result.isEmpty()) {
      logSuccessfulResolution(result);
    }
    if (resolutionAttempts.isEmpty()) {
      if (result.isEmpty()) {
        Logger.warnOnce(
            KEY_NO_ATTEMPTS,
            "Classpath resolution skipped (no build tool attempts). External dependencies will not be resolved.");
      }
      return;
    }
    final List<ClasspathResolutionResult.Attempt> failedAttempts =
        resolutionAttempts.stream().filter(attempt -> !attempt.success()).toList();
    if (result.isEmpty()) {
      logFailedResolution(failedAttempts);
    }
    for (final ClasspathResolutionResult.Attempt attempt : failedAttempts) {
      final String exitCode = attempt.exitCode() != null ? attempt.exitCode().toString() : "n/a";
      Logger.debug(
          "Classpath resolution via "
              + attempt.tool()
              + " failed (exitCode="
              + exitCode
              + "): "
              + attempt.message());
    }
  }

  private static void logSuccessfulResolution(final ClasspathResolutionResult result) {
    final int resolvedEntryCount = result.getEntries().size();
    final String selectedTool =
        result.getSelectedTool() != null ? result.getSelectedTool() : UNKNOWN_TOOL;
    Logger.infoOnce(
        "classpath:resolution:success:" + selectedTool + ":" + resolvedEntryCount,
        "Classpath resolved via " + selectedTool + ": " + resolvedEntryCount + " entries");
  }

  private static void logFailedResolution(
      final List<ClasspathResolutionResult.Attempt> failedAttempts) {
    final String summary =
        failedAttempts.isEmpty() ? "no build tool succeeded" : formatSummary(failedAttempts);
    Logger.warnOnce(
        KEY_FAILED + ":" + summary,
        "Classpath resolution failed ("
            + summary
            + "). External dependencies will not be resolved.");
  }

  private static String formatSummary(
      final List<ClasspathResolutionResult.Attempt> failedAttempts) {
    return failedAttempts.stream()
        .map(ClasspathResolutionLogger::formatAttempt)
        .collect(Collectors.joining("; "));
  }

  private static String formatAttempt(final ClasspathResolutionResult.Attempt attempt) {
    final String message = attempt.message();
    if (message == null || message.isBlank()) {
      final String exitCode = attempt.exitCode() != null ? attempt.exitCode().toString() : "n/a";
      return attempt.tool() + " exitCode=" + exitCode;
    }
    return attempt.tool() + ": " + message;
  }
}
