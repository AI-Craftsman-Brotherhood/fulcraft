package com.craftsmanbro.fulcraft.plugins.reporting.core.util;

import com.craftsmanbro.fulcraft.infrastructure.fs.impl.PathUtils;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import org.apache.commons.lang3.math.NumberUtils;

/** Utility class for common aggregator operations. */
public final class AggregatorUtils {

  private AggregatorUtils() {
    // Utility class
  }

  /** Safely parses an integer, returning 0 on failure. */
  public static int parseIntSafe(final String value) {
    return NumberUtils.toInt(value, 0);
  }

  /** Safely retrieves the class FQN from a task, with fallbacks. */
  public static String safeClassFqn(final TaskRecord task) {
    java.util.Objects.requireNonNull(
        task,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "task must not be null"));
    if (task.getClassFqn() != null && !task.getClassFqn().isBlank()) {
      return task.getClassFqn();
    }
    if (task.getFilePath() != null && !task.getFilePath().isBlank()) {
      var normalized = task.getFilePath().replace('/', '.').replace('\\', '.');
      if (normalized.endsWith(PathUtils.JAVA_EXTENSION)) {
        normalized =
            normalized.substring(0, normalized.length() - PathUtils.JAVA_EXTENSION.length());
      }
      return normalized.isEmpty() ? ClassInfo.UNKNOWN_CLASS : normalized;
    }
    return ClassInfo.UNKNOWN_CLASS;
  }
  // Task loading moved to reporting.io.TasksFileLoader to keep core utilities I/O-free.
}
