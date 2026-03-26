package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding;
import java.util.List;
import java.util.Objects;

/**
 * Result of a prompt redaction operation.
 *
 * <p>Contains the redacted text, legacy report (for compatibility), and extended findings with
 * confidence scores for transparency and auditing.
 *
 * @param redactedText The text after applying redaction (masked or original)
 * @param report Legacy report containing counts by type (for backward compatibility)
 * @param findings Detailed list of individual findings with positions and confidence
 * @param maxConfidence Maximum confidence score among all findings
 */
public record RedactionResult(
    String redactedText, RedactionReport report, List<Finding> findings, double maxConfidence) {

  public RedactionResult {
    Objects.requireNonNull(
        redactedText,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "redactedText must not be null"));
    Objects.requireNonNull(
        report,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "report must not be null"));
    Objects.requireNonNull(
        findings,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "findings must not be null"));
    findings = List.copyOf(findings);
    if (maxConfidence < 0.0 || maxConfidence > 1.0) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "maxConfidence must be between 0.0 and 1.0"));
    }
  }

  /** Empty result with no findings. */
  public static final RedactionResult EMPTY =
      new RedactionResult("", RedactionReport.EMPTY, List.of(), 0.0);

  /**
   * Creates a result with legacy report only (backward compatible).
   *
   * @param redactedText The redacted text
   * @param report Legacy report
   */
  public RedactionResult(final String redactedText, final RedactionReport report) {
    this(
        redactedText,
        safeReport(report),
        List.of(),
        report != null && report.totalCount() > 0 ? 1.0 : 0.0);
  }

  private static RedactionReport safeReport(final RedactionReport report) {
    return report != null ? report : RedactionReport.EMPTY;
  }

  /**
   * Creates a result for text that had no findings.
   *
   * @param originalText The original (unchanged) text
   * @return Result with original text and empty report
   */
  public static RedactionResult unchanged(final String originalText) {
    return new RedactionResult(originalText, RedactionReport.EMPTY, List.of(), 0.0);
  }

  /**
   * Returns true if any sensitive data was detected.
   *
   * @return true if findings exist
   */
  public boolean hasFindings() {
    return !findings.isEmpty() || report.hasDetections();
  }

  /**
   * Returns true if the result should trigger blocking based on threshold.
   *
   * @param blockThreshold Threshold for blocking
   * @return true if max confidence exceeds block threshold
   */
  public boolean exceedsBlockThreshold(final double blockThreshold) {
    return maxConfidence >= blockThreshold;
  }

  /**
   * Returns a summary string for logging.
   *
   * @return Human-readable summary
   */
  public String summary() {
    if (!hasFindings()) {
      return "No sensitive data detected";
    }
    return String.format(
        "Detected %d findings (max confidence: %.2f), %s",
        findings.isEmpty() ? report.totalCount() : findings.size(), maxConfidence, report);
  }
}
