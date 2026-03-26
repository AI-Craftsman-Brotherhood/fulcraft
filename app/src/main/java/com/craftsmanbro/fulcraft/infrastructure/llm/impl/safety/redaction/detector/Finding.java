package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector;

import java.util.Objects;

/**
 * Represents a single sensitive data finding.
 *
 * <p>A finding captures the location, type, and confidence of detected sensitive information.
 *
 * @param type Detection type (e.g., EMAIL, API_KEY, SECRET, PERSON_NAME, CREDIT_CARD)
 * @param start Start position in the text (0-indexed, inclusive)
 * @param end End position in the text (0-indexed, exclusive)
 * @param confidence Confidence score (0.0-1.0) where 1.0 is highest certainty
 * @param snippet The matched text (may be truncated for long matches)
 * @param ruleId Identifier for the rule/pattern that triggered this finding (e.g., "regex:EMAIL",
 *     "dictionary:denylist.txt", "ml:spacy-ner")
 */
public record Finding(
    String type, int start, int end, double confidence, String snippet, String ruleId) {

  private static final int SNIPPET_FULL_REDACTION_LENGTH = 6;
  private static final int SNIPPET_VISIBLE_EDGE_LENGTH = 2;
  private static final String SNIPPET_PARTIAL_MASK = "***";
  private static final String SNIPPET_FULL_MASK = "[REDACTED]";

  public Finding {
    Objects.requireNonNull(
        type,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "type must not be null"));
    Objects.requireNonNull(
        ruleId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "ruleId must not be null"));
    if (start < 0) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "start must be >= 0"));
    }
    if (end < start) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "end must be >= start"));
    }
    if (confidence < 0.0 || confidence > 1.0) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "confidence must be between 0.0 and 1.0"));
    }
  }

  /**
   * Creates a finding with full confidence (1.0).
   *
   * @param type Detection type
   * @param start Start position
   * @param end End position
   * @param snippet Matched text
   * @param ruleId Rule identifier
   * @return Finding with confidence 1.0
   */
  public static Finding certain(
      final String type,
      final int start,
      final int end,
      final String snippet,
      final String ruleId) {
    return new Finding(type, start, end, 1.0, snippet, ruleId);
  }

  /**
   * Returns true if this finding overlaps with another.
   *
   * @param other The other finding to check
   * @return true if ranges overlap
   */
  public boolean overlaps(final Finding other) {
    return start < other.end && other.start < end;
  }

  /**
   * Returns the length of the matched region.
   *
   * @return Length in characters
   */
  public int length() {
    return end - start;
  }

  /**
   * Returns a masked version of the snippet suitable for logging.
   *
   * @return Masked snippet showing only first and last few characters
   */
  public String maskedSnippet() {
    if (snippet == null || snippet.length() <= SNIPPET_FULL_REDACTION_LENGTH) {
      return SNIPPET_FULL_MASK;
    }
    final int snippetLength = snippet.length();
    return snippet.substring(0, SNIPPET_VISIBLE_EDGE_LENGTH)
        + SNIPPET_PARTIAL_MASK
        + snippet.substring(snippetLength - SNIPPET_VISIBLE_EDGE_LENGTH);
  }

  @Override
  public String toString() {
    return String.format(
        "Finding{type=%s, pos=[%d,%d), confidence=%.2f, rule=%s}",
        type, start, end, confidence, ruleId);
  }
}
