package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector;

/**
 * Interface for sensitive data detectors in the redaction chain.
 *
 * <p>Each detector implements a specific detection strategy (regex, dictionary, ML, etc.) and
 * returns findings with confidence scores. Detectors can be chained together to provide
 * comprehensive sensitive data detection.
 *
 * <p>Usage in the detector chain (order matters for precedence):
 *
 * <pre>{@code
 * List<SensitiveDetector> detectors = List.of(
 *         new RegexDetector(),
 *         DictionaryDetector.fromFiles(denylistPath, allowlistPath, false),
 *         new MlNerDetector(endpointUrl));
 * }</pre>
 */
public interface SensitiveDetector {

  /**
   * Detects sensitive data in the given text.
   *
   * @param text The text to analyze for sensitive data
   * @param ctx Context information for detection (may contain configuration, state, etc.)
   * @return Detection result containing findings and max confidence score
   */
  DetectionResult detect(String text, DetectionContext ctx);

  /**
   * Returns the unique name of this detector.
   *
   * <p>Used for logging, configuration (detector lists), and identifying which detector triggered a
   * finding.
   *
   * @return The detector name (e.g., "regex", "dictionary", "ml")
   */
  String getName();

  /**
   * Returns whether this detector is enabled based on the current configuration.
   *
   * <p>Default implementation returns true. Detectors may override this to read from context
   * configuration.
   *
   * @param ctx Detection context containing configuration
   * @return true if this detector should be used
   */
  default boolean isEnabled(final DetectionContext ctx) {
    return true;
  }
}
