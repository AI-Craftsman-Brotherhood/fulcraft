package com.craftsmanbro.fulcraft.infrastructure.buildtool.model;

/**
 * Outcome of an optional isolated validation check.
 *
 * @param success whether the validation check passed
 * @param exitCode exit code produced by the underlying build tool process
 * @param output captured process output (or a portion of it)
 * @param errorMessage summarized failure reason, if available
 * @param assertionDetails formatted assertion detail string (may include expected/actual)
 * @param stackTrace stack trace snippet or full trace
 * @param reportContent XML snippet of failing testcase/testsuite
 */
public record IsolationCheckResult(
    boolean success,
    Integer exitCode,
    String output,
    String errorMessage,
    String assertionDetails,
    String stackTrace,
    String reportContent) {

  public static IsolationCheckResult passed() {
    return new IsolationCheckResult(true, 0, null, null, null, null, null);
  }

  public static IsolationCheckResult failed(
      final int exitCode, final String output, final String errorMessage) {
    return new IsolationCheckResult(false, exitCode, output, errorMessage, null, null, null);
  }

  public static IsolationCheckResult failedWithDetails(
      final int exitCode,
      final String output,
      final String errorMessage,
      final String assertionDetails,
      final String stackTrace,
      final String reportContent) {
    return new IsolationCheckResult(
        false, exitCode, output, errorMessage, assertionDetails, stackTrace, reportContent);
  }
}
