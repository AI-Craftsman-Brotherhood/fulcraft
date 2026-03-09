package com.craftsmanbro.fulcraft.infrastructure.metrics.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;

/** Immutable pair of cyclomatic complexity and maximum nesting depth. */
public record CodeMetrics(int cyclomaticComplexity, int maxNestingDepth) {
  private static final String COMMON_ERROR_MESSAGE_KEY = "infra.common.error.message";
  private static final String NON_NEGATIVE_SUFFIX = " must be non-negative";

  public CodeMetrics {
    validateNonNegative(cyclomaticComplexity, "cyclomaticComplexity");
    validateNonNegative(maxNestingDepth, "maxNestingDepth");
  }

  private static void validateNonNegative(int metricValue, String metricName) {
    if (metricValue < 0) {
      throw new IllegalArgumentException(
          MessageSource.getMessage(COMMON_ERROR_MESSAGE_KEY, metricName + NON_NEGATIVE_SUFFIX));
    }
  }
}
