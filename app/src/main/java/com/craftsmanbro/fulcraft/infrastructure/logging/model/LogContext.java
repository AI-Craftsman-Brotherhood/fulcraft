package com.craftsmanbro.fulcraft.infrastructure.logging.model;

/** Immutable mapped diagnostic context (MDC) values used by infrastructure logging. */
public record LogContext(
    String runId,
    String traceId,
    String subsystem,
    String stage,
    String targetClass,
    String taskId) {

  public LogContext {
    runId = normalizeContextValue(runId);
    traceId = normalizeContextValue(traceId);
    subsystem = normalizeContextValue(subsystem);
    stage = normalizeContextValue(stage);
    targetClass = normalizeContextValue(targetClass);
    taskId = normalizeContextValue(taskId);
  }

  /** Returns whether any mapped diagnostic context value is present. */
  public boolean hasAny() {
    return runId != null
        || traceId != null
        || subsystem != null
        || stage != null
        || targetClass != null
        || taskId != null;
  }

  private static String normalizeContextValue(final String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }
}
