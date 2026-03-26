package com.craftsmanbro.fulcraft.plugins.reporting.core.util;

import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.util.Locale;

/** Utility for building stable task/result keys. */
public final class TaskKeyUtil {

  private static final String UNKNOWN = "unknown";

  private TaskKeyUtil() {
    // Utility class
  }

  public static String buildKey(final TaskRecord task) {
    if (task == null) {
      return UNKNOWN;
    }
    if (task.getTaskId() != null && !task.getTaskId().isBlank()) {
      return task.getTaskId();
    }
    final String classPart = task.getClassFqn() != null ? task.getClassFqn() : UNKNOWN;
    final String methodPart = task.getMethodName() != null ? task.getMethodName() : UNKNOWN;
    return (classPart + "#" + methodPart).toLowerCase(Locale.ROOT);
  }

  public static String buildKey(final GenerationTaskResult result) {
    if (result == null) {
      return UNKNOWN;
    }
    if (result.getTaskId() != null && !result.getTaskId().isBlank()) {
      return result.getTaskId();
    }
    final String classPart = result.getClassFqn() != null ? result.getClassFqn() : UNKNOWN;
    final String methodPart = result.getMethodName() != null ? result.getMethodName() : UNKNOWN;
    return (classPart + "#" + methodPart).toLowerCase(Locale.ROOT);
  }
}
