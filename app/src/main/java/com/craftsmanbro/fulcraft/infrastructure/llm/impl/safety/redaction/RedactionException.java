package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Signals a failure or block while attempting to redact sensitive data from prompts.
 *
 * <p>This exception is thrown when:
 *
 * <ul>
 *   <li>A detection exceeds the block threshold (high-confidence sensitive data)
 *   <li>An error occurs during the redaction process
 * </ul>
 *
 * <p>The exception includes detailed information about which detector triggered the block, the
 * detected content, positions, and confidence scores for auditing and debugging.
 */
public class RedactionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private transient List<Finding> blockingFindings;

  private final String detectorName;

  private final double maxConfidence;

  /**
   * Creates an exception with basic message and cause.
   *
   * @param message Error message
   * @param cause Underlying cause
   */
  public RedactionException(final String message, final Throwable cause) {
    super(message, cause);
    this.blockingFindings = List.of();
    this.detectorName = null;
    this.maxConfidence = 0.0;
  }

  /**
   * Creates an exception with basic message.
   *
   * @param message Error message
   */
  public RedactionException(final String message) {
    super(message);
    this.blockingFindings = List.of();
    this.detectorName = null;
    this.maxConfidence = 0.0;
  }

  /**
   * Creates an exception for a blocking condition with full details.
   *
   * @param message Error message
   * @param blockingFindings Findings that caused the block
   * @param detectorName Name of the detector that triggered the block
   * @param maxConfidence Maximum confidence score
   */
  public RedactionException(
      final String message,
      final List<Finding> blockingFindings,
      final String detectorName,
      final double maxConfidence) {
    super(message);
    this.blockingFindings = blockingFindings != null ? List.copyOf(blockingFindings) : List.of();
    this.detectorName = detectorName;
    this.maxConfidence = maxConfidence;
  }

  /**
   * Creates a blocking exception from findings that exceed the threshold.
   *
   * @param findings List of findings
   * @param blockThreshold The threshold that was exceeded
   * @return RedactionException with details
   */
  public static RedactionException blocked(
      final List<Finding> findings, final double blockThreshold) {
    if (findings == null || findings.isEmpty()) {
      return new RedactionException("Blocked due to policy but no findings recorded");
    }
    final Finding primary =
        findings.stream()
            .max((a, b) -> Double.compare(a.confidence(), b.confidence()))
            .orElse(findings.get(0));
    final double maxConfidence = findings.stream().mapToDouble(Finding::confidence).max().orElse(0.0);
    final String detectorName = extractDetector(primary.ruleId());
    final String message =
        String.format(
            "Sensitive data blocked by redaction policy. "
                + "Detector: %s, Type: %s, Confidence: %.2f (threshold: %.2f). "
                + "Found %d high-confidence items.",
            detectorName,
            primary.type(),
            primary.confidence(),
            blockThreshold,
            findings.size());
    return new RedactionException(message, findings, detectorName, maxConfidence);
  }

  private static String extractDetector(final String ruleId) {
    if (ruleId == null) {
      return "unknown";
    }
    final int colonIndex = ruleId.indexOf(':');
    return colonIndex > 0 ? ruleId.substring(0, colonIndex) : ruleId;
  }

  /**
   * Returns the findings that caused this exception.
   *
   * @return List of blocking findings (may be empty, never null)
   */
  public List<Finding> getBlockingFindings() {
    if (blockingFindings == null) {
      return List.of();
    }
    return List.copyOf(blockingFindings);
  }

  private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    // Findings are intentionally excluded from serialized form.
    this.blockingFindings = List.of();
  }

  /**
   * Returns the name of the detector that triggered the block.
   *
   * @return Detector name (may be null)
   */
  public String getDetectorName() {
    return detectorName;
  }

  /**
   * Returns the maximum confidence score among blocking findings.
   *
   * @return Max confidence (0.0 if not applicable)
   */
  public double getMaxConfidence() {
    return maxConfidence;
  }

  /**
   * Checks if a throwable chain contains a RedactionException.
   *
   * @param throwable The throwable to check
   * @return true if a RedactionException is in the chain
   */
  public static boolean isRedactionFailure(final Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof RedactionException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  /**
   * Returns a detailed message for logging/auditing.
   *
   * @return Detailed message including findings information
   */
  public String getDetailedMessage() {
    final List<Finding> findings = blockingFindings == null ? List.of() : blockingFindings;
    if (findings.isEmpty()) {
      return getMessage();
    }
    final StringBuilder sb = new StringBuilder(getMessage());
    sb.append("\n--- Blocking Findings ---");
    for (final Finding f : findings) {
      sb.append("\n  - ")
          .append(f.type())
          .append(" at [")
          .append(f.start())
          .append(",")
          .append(f.end())
          .append("): confidence=")
          .append(String.format("%.2f", f.confidence()))
          .append(", rule=")
          .append(f.ruleId())
          .append(", snippet=")
          .append(f.maskedSnippet());
    }
    return sb.toString();
  }
}
