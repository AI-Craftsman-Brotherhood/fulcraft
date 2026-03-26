package com.craftsmanbro.fulcraft.infrastructure.buildtool.failure;

import com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.util.AssertionMismatchExtractor;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.util.FixSuggestionTemplates;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.util.MismatchType;

/**
 * Analyzes test failures and provides structured information for repair.
 *
 * <p>This class consolidates the analysis logic that was previously scattered in the IO layer. It
 * uses {@link AssertionMismatchExtractor} to extract details and {@link FixSuggestionTemplates} to
 * provide fix guidance.
 *
 * <h2>Architectural Role</h2>
 *
 * <pre>
 *   IO Layer (JUnitXmlReportParser)
 *       ↓ raw TestFailure (message, stack trace)
 *   Core Layer (FailureAnalysisService)   ← this class
 *       ↓ AnalyzedFailure (with mismatch details, fix suggestions)
 *   Flow/Adapter
 * </pre>
 *
 * @see AssertionMismatchExtractor
 * @see FixSuggestionTemplates
 */
public class FailureAnalysisService {

  private final AssertionMismatchExtractor mismatchExtractor;

  /** Create a new FailureAnalysisService with default extractor. */
  public FailureAnalysisService() {
    this.mismatchExtractor = new AssertionMismatchExtractor();
  }

  /**
   * Create a new FailureAnalysisService with a custom extractor.
   *
   * @param mismatchExtractor the extractor to use
   */
  public FailureAnalysisService(final AssertionMismatchExtractor mismatchExtractor) {
    this.mismatchExtractor = mismatchExtractor;
  }

  /**
   * Analyze a test failure and extract mismatch details.
   *
   * @param failureMessage the failure message
   * @param stackTrace the stack trace
   * @return extracted mismatch details
   */
  public AssertionMismatchExtractor.MismatchDetails analyzeMismatch(
      final String failureMessage, final String stackTrace) {
    return mismatchExtractor.extract(failureMessage, stackTrace);
  }

  /**
   * Get fix suggestion for a mismatch type.
   *
   * @param mismatchType the type of mismatch
   * @return the fix suggestion template
   */
  public String getFixSuggestion(final MismatchType mismatchType) {
    return FixSuggestionTemplates.getTemplate(mismatchType);
  }

  /**
   * Get fix suggestion template ID for logging/tracking.
   *
   * @param mismatchType the type of mismatch
   * @return the template ID
   */
  public String getFixTemplateId(final MismatchType mismatchType) {
    return FixSuggestionTemplates.getTemplateId(mismatchType);
  }

  /**
   * Format failure details for exchange (e.g., LLM input).
   *
   * @param testClass the test class name
   * @param testMethod the test method name
   * @param failureType the failure type (e.g., AssertionError)
   * @param failureMessage the failure message
   * @param stackTrace the stack trace
   * @return formatted failure details
   */
  public String formatForExchange(
      final String testClass,
      final String testMethod,
      final String failureType,
      final String failureMessage,
      final String stackTrace) {
    final AssertionMismatchExtractor.MismatchDetails details =
        mismatchExtractor.extract(failureMessage, stackTrace);
    final StringBuilder sb = new StringBuilder();
    sb.append("=== ASSERTION FAILURE DETAILS ===\n");
    sb.append("Test: ").append(testClass).append("#").append(testMethod).append("\n");
    sb.append("Type: ").append(failureType).append("\n");
    appendMismatchDetails(sb, details);
    appendFailureMessage(sb, failureMessage);
    appendStackTrace(sb, stackTrace);
    appendFixSuggestion(sb, details);
    return sb.toString();
  }

  private void appendMismatchDetails(
      final StringBuilder sb, final AssertionMismatchExtractor.MismatchDetails details) {
    if (details == null) {
      return;
    }
    sb.append("Mismatch Type: ").append(details.mismatchType()).append("\n");
    if (details.expected() != null) {
      sb.append("Expected: ").append(truncate(details.expected(), 100)).append("\n");
    }
    if (details.actual() != null) {
      sb.append("Actual: ").append(truncate(details.actual(), 100)).append("\n");
    }
    if (details.delta() != null) {
      sb.append("Delta: ").append(details.delta()).append("\n");
    }
    if (details.assertionLocation() != null) {
      sb.append("Location: ").append(details.assertionLocation()).append("\n");
    }
  }

  private void appendFailureMessage(final StringBuilder sb, final String failureMessage) {
    if (failureMessage != null) {
      sb.append("Message: ").append(truncate(failureMessage, 200)).append("\n");
    }
  }

  private void appendStackTrace(final StringBuilder sb, final String stackTrace) {
    if (stackTrace == null) {
      return;
    }
    sb.append("Stack Trace (first 5 lines):\n");
    final String[] lines = stackTrace.split("\n");
    final int limit = Math.min(5, lines.length);
    for (int i = 0; i < limit; i++) {
      sb.append("  ").append(lines[i]).append("\n");
    }
  }

  private void appendFixSuggestion(
      final StringBuilder sb, final AssertionMismatchExtractor.MismatchDetails details) {
    final MismatchType mismatchType =
        details != null ? details.mismatchType() : MismatchType.UNKNOWN;
    final String fixSuggestion = getFixSuggestion(mismatchType);
    if (fixSuggestion != null && !fixSuggestion.isBlank()) {
      sb.append("=== FIX SUGGESTION ===\n");
      sb.append("Template: ").append(getFixTemplateId(mismatchType)).append("\n");
      sb.append(fixSuggestion.trim()).append("\n");
    }
  }

  private String truncate(final String s, final int maxLen) {
    if (s == null) {
      return null;
    }
    if (s.length() <= maxLen) {
      return s;
    }
    return s.substring(0, maxLen) + "...";
  }
}
