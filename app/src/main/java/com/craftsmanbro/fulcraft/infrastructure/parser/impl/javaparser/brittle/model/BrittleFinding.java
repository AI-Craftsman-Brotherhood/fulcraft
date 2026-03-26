package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model;

import java.util.Objects;

/**
 * Represents a finding from the InspectionPortAdapter.
 *
 * <p>A finding indicates a problematic pattern detected in generated test code that may lead to
 * brittle, flaky, or hard-to-maintain tests.
 *
 * @param ruleId The rule that was violated
 * @param severity The severity level
 * @param filePath The path to the file containing the violation
 * @param lineNumber The line number (1-indexed, or -1 if unknown)
 * @param message A short description of the issue
 * @param evidence The relevant code snippet or detected pattern
 */
public record BrittleFinding(
    RuleId ruleId,
    Severity severity,
    String filePath,
    int lineNumber,
    String message,
    String evidence) {

  private static final String COMMON_ERROR_MESSAGE_KEY = "infra.common.error.message";

  private static final String LINE_NUMBER_MESSAGE = "lineNumber must be -1 or a positive integer";

  /** Rule identifiers for brittle test detection. */
  public enum RuleId {
    /** Reflection usage (setAccessible, getDeclaredField, etc.). */
    REFLECTION,
    /** Thread.sleep or TimeUnit.sleep usage. */
    SLEEP,
    /** Time-dependent code (Instant.now, LocalDateTime.now, etc.). */
    TIME,
    /** Random number generation (Random, UUID.randomUUID, etc.). */
    RANDOM,
    /** Excessive mock usage (too many Mockito.mock calls). */
    OVER_MOCK
  }

  /** Severity level of the finding. */
  public enum Severity {
    /** Error - causes pipeline failure. */
    ERROR,
    /** Warning - reported but does not fail pipeline. */
    WARNING
  }

  /** Compact constructor for validation. */
  public BrittleFinding {
    Objects.requireNonNull(
        ruleId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "ruleId"));
    Objects.requireNonNull(
        severity,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "severity"));
    Objects.requireNonNull(
        filePath,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "filePath"));
    Objects.requireNonNull(
        message,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "message"));
    validateLineNumber(lineNumber);
    // evidence may be null
  }

  private static void validateLineNumber(final int lineNumber) {
    if (lineNumber == 0 || lineNumber < -1) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              COMMON_ERROR_MESSAGE_KEY, LINE_NUMBER_MESSAGE));
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("[").append(severity).append("] ").append(ruleId);
    sb.append(" at ").append(filePath);
    if (lineNumber > 0) {
      sb.append(":").append(lineNumber);
    }
    sb.append(" - ").append(message);
    return sb.toString();
  }
}
