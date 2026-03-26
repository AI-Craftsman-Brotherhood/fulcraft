package com.craftsmanbro.fulcraft.kernel.workflow.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.Locale;

/** Failure policy for a workflow node. */
public enum WorkflowFailurePolicy {
  STOP,
  CONTINUE,
  SKIP_DOWNSTREAM;

  /** Parses policy text. Falls back to STOP for null/blank values. */
  public static WorkflowFailurePolicy fromString(final String value) {
    if (value == null || value.isBlank()) {
      return STOP;
    }
    final String normalized = value.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "STOP" -> STOP;
      case "CONTINUE" -> CONTINUE;
      case "SKIP_DOWNSTREAM" -> SKIP_DOWNSTREAM;
      default ->
          throw new IllegalArgumentException(
              MessageSource.getMessage("kernel.workflow.failure_policy.error.unsupported", value));
    };
  }
}
