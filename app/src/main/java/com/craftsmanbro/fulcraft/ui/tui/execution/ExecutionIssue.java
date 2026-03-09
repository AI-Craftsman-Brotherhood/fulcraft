package com.craftsmanbro.fulcraft.ui.tui.execution;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueCategory;
import java.util.Objects;

/**
 * Represents an execution issue that requires user decision.
 *
 * <p>When a failure occurs during test generation or execution, this record captures the relevant
 * information needed to present the issue to the user and allow them to decide how to proceed.
 *
 * <p>The issue includes:
 *
 * <ul>
 *   <li>{@code category} - The type of failure (exception, test failure, compile error)
 *   <li>{@code targetIdentifier} - The class/method/file that failed
 *   <li>{@code cause} - A brief (1-3 line) description of what went wrong
 *   <li>{@code stageName} - The pipeline stage where the failure occurred
 * </ul>
 *
 * @param category the category of the issue
 * @param targetIdentifier the identifier of the failed target (e.g., class name)
 * @param cause brief description of the cause (1-3 lines)
 * @param stageName the pipeline stage where the issue occurred
 */
public record ExecutionIssue(
    IssueCategory category, String targetIdentifier, String cause, String stageName) {

  /** Compact constructor with validation. */
  public ExecutionIssue {
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(targetIdentifier, "targetIdentifier must not be null");
    Objects.requireNonNull(cause, "cause must not be null");
    Objects.requireNonNull(stageName, "stageName must not be null");
    cause = normalizeLineSeparators(cause);
    // Ensure targetIdentifier is not blank
    if (targetIdentifier.isBlank()) {
      throw new IllegalArgumentException("targetIdentifier must not be blank");
    }
    // Ensure cause is not blank
    if (cause.isBlank()) {
      throw new IllegalArgumentException("cause must not be blank");
    }
  }

  /**
   * Creates an issue from an exception.
   *
   * @param targetIdentifier the target that failed
   * @param stageName the stage where it failed
   * @param exception the exception that occurred
   * @return a new ExecutionIssue
   */
  public static ExecutionIssue fromException(
      final String targetIdentifier, final String stageName, final Throwable exception) {
    Objects.requireNonNull(exception, "exception must not be null");
    String cause = exception.getMessage();
    if (cause == null || cause.isBlank()) {
      cause = exception.getClass().getSimpleName();
    }
    // Limit to 3 lines
    cause = truncateCause(cause, 3);
    return new ExecutionIssue(IssueCategory.EXCEPTION, targetIdentifier, cause, stageName);
  }

  /**
   * Creates an issue for a test failure.
   *
   * @param targetIdentifier the test target that failed
   * @param stageName the stage where it failed
   * @param failureMessage the test failure message
   * @return a new ExecutionIssue
   */
  public static ExecutionIssue testFailure(
      final String targetIdentifier, final String stageName, final String failureMessage) {
    String cause = failureMessage;
    if (cause == null || cause.isBlank()) {
      cause = MessageSource.getMessage("tui.issue.default_test_failure");
    }
    cause = truncateCause(cause, 3);
    return new ExecutionIssue(IssueCategory.TEST_FAILURE, targetIdentifier, cause, stageName);
  }

  /**
   * Creates an issue for a compile error.
   *
   * @param targetIdentifier the target that failed to compile
   * @param stageName the stage where it failed
   * @param compileError the compilation error message
   * @return a new ExecutionIssue
   */
  public static ExecutionIssue compileError(
      final String targetIdentifier, final String stageName, final String compileError) {
    String cause = compileError;
    if (cause == null || cause.isBlank()) {
      cause = MessageSource.getMessage("tui.issue.default_compile_error");
    }
    cause = truncateCause(cause, 3);
    return new ExecutionIssue(IssueCategory.COMPILE_ERROR, targetIdentifier, cause, stageName);
  }

  /** Truncates the cause message to a maximum number of lines. */
  private static String truncateCause(final String cause, final int maxLines) {
    if (cause == null) {
      return "";
    }
    final String normalized = normalizeLineSeparators(cause);
    final String[] lines = normalized.split("\n", maxLines + 1);
    if (lines.length <= maxLines) {
      return normalized;
    }
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < maxLines; i++) {
      if (i > 0) {
        sb.append("\n");
      }
      sb.append(lines[i]);
    }
    return sb.toString();
  }

  private static String normalizeLineSeparators(final String text) {
    return text.replace("\r\n", "\n").replace("\r", "\n");
  }

  /**
   * Returns a formatted summary suitable for display.
   *
   * @return formatted issue summary
   */
  public String toDisplaySummary() {
    return String.format(
        "[%s] %s at stage %s", category.getDisplayName(), targetIdentifier, stageName);
  }
}
