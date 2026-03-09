package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Result of a sensitive data detection operation.
 *
 * <p>Contains a list of findings and the maximum confidence score among them. This record is
 * immutable and thread-safe.
 *
 * @param findings List of detected sensitive data findings
 * @param maxConfidence Maximum confidence score among all findings (0.0-1.0)
 */
public record DetectionResult(List<Finding> findings, double maxConfidence) {

  /** Empty result with no findings. */
  public static final DetectionResult EMPTY = new DetectionResult(List.of(), 0.0);

  public DetectionResult {
    Objects.requireNonNull(
        findings,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "findings must not be null"));
    // Defensive copy for immutability
    findings = List.copyOf(findings);
    if (maxConfidence < 0.0 || maxConfidence > 1.0) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "maxConfidence must be between 0.0 and 1.0"));
    }
    final double computedMaxConfidence = calculateMaxConfidence(findings);
    if (!approximatelyEqual(maxConfidence, computedMaxConfidence)) {
      throw new IllegalArgumentException(
          "maxConfidence must match the maximum confidence of findings");
    }
  }

  /**
   * Creates a result from findings, automatically calculating max confidence.
   *
   * @param findings List of findings
   * @return Detection result with calculated max confidence
   */
  public static DetectionResult of(final List<Finding> findings) {
    if (findings == null || findings.isEmpty()) {
      return EMPTY;
    }
    final double calculatedMaxConfidence = calculateMaxConfidence(findings);
    return new DetectionResult(findings, calculatedMaxConfidence);
  }

  /**
   * Merges this result with another, combining findings and updating max confidence.
   *
   * @param other The other result to merge
   * @return Merged detection result
   */
  public DetectionResult merge(final DetectionResult other) {
    if (other == null || other.findings().isEmpty()) {
      return this;
    }
    if (findings.isEmpty()) {
      return other;
    }
    final List<Finding> mergedFindings = new ArrayList<>(findings);
    mergedFindings.addAll(other.findings());
    return DetectionResult.of(mergedFindings);
  }

  /**
   * Returns true if any findings were detected.
   *
   * @return true if findings list is not empty
   */
  public boolean hasFindings() {
    return !findings.isEmpty();
  }

  /**
   * Returns the count of findings.
   *
   * @return Number of findings
   */
  public int findingsCount() {
    return findings.size();
  }

  private static double calculateMaxConfidence(final List<Finding> findings) {
    return findings.stream().mapToDouble(Finding::confidence).max().orElse(0.0);
  }

  private static boolean approximatelyEqual(final double left, final double right) {
    return Math.abs(left - right) < 1.0e-9;
  }
}
