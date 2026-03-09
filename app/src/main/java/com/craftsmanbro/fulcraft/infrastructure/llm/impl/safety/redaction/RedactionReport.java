package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Report summarizing redaction findings.
 *
 * <p>Contains counts by type for backward compatibility, plus methods for generating detailed
 * reports for logging and auditing.
 *
 * @param emailCount Number of email addresses detected
 * @param creditCardCount Number of credit card numbers detected
 * @param pemKeyCount Number of PEM private keys detected
 * @param authTokenCount Number of auth tokens (Bearer, API keys) detected
 * @param jwtCount Number of JWT tokens detected
 * @param dictionaryCount Number of dictionary terms detected
 * @param mlEntityCount Number of ML NER entities detected
 */
public record RedactionReport(
    int emailCount,
    int creditCardCount,
    int pemKeyCount,
    int authTokenCount,
    int jwtCount,
    int dictionaryCount,
    int mlEntityCount) {

  /** Empty report with no findings. */
  public static final RedactionReport EMPTY = new RedactionReport(0, 0, 0, 0, 0, 0, 0);

  private static final long ZERO_COUNT = 0L;
  private static final String TYPE_EMAIL = "EMAIL";
  private static final String TYPE_CREDIT_CARD = "CREDIT_CARD";
  private static final String TYPE_PEM_KEY = "PEM_KEY";
  private static final String TYPE_AUTH_TOKEN = "AUTH_TOKEN";
  private static final String TYPE_JWT = "JWT";
  private static final String TYPE_DICTIONARY = "DICTIONARY";
  private static final Set<String> ML_ENTITY_TYPES =
      Set.of(
          "PERSON_NAME",
          "ORGANIZATION",
          "LOCATION",
          "ML_ENTITY",
          "DATE",
          "SSN",
          "PHONE",
          "MEDICAL");

  /**
   * Creates a legacy-compatible report (backward compatible constructor).
   *
   * @param emailCount Email count
   * @param creditCardCount Credit card count
   * @param pemKeyCount PEM key count
   * @param authTokenCount Auth token count
   * @param jwtCount JWT count
   */
  public RedactionReport(
      final int emailCount,
      final int creditCardCount,
      final int pemKeyCount,
      final int authTokenCount,
      final int jwtCount) {
    this(emailCount, creditCardCount, pemKeyCount, authTokenCount, jwtCount, 0, 0);
  }

  /**
   * Creates a report from a list of findings.
   *
   * @param findings List of findings to summarize
   * @return Report with counts by type
   */
  public static RedactionReport fromFindings(final List<Finding> findings) {
    if (findings == null || findings.isEmpty()) {
      return EMPTY;
    }
    final Map<String, Long> counts =
        findings.stream().collect(Collectors.groupingBy(Finding::type, Collectors.counting()));
    final int mlEntityCount =
        ML_ENTITY_TYPES.stream().mapToInt(type -> countByType(counts, type)).sum();
    return new RedactionReport(
        countByType(counts, TYPE_EMAIL),
        countByType(counts, TYPE_CREDIT_CARD),
        countByType(counts, TYPE_PEM_KEY),
        countByType(counts, TYPE_AUTH_TOKEN),
        countByType(counts, TYPE_JWT),
        countByType(counts, TYPE_DICTIONARY),
        mlEntityCount);
  }

  /**
   * Returns the total count of all detected items.
   *
   * @return Sum of all counts
   */
  public int totalCount() {
    return emailCount
        + creditCardCount
        + pemKeyCount
        + authTokenCount
        + jwtCount
        + dictionaryCount
        + mlEntityCount;
  }

  /**
   * Returns true if any items were detected.
   *
   * @return true if total count > 0
   */
  public boolean hasDetections() {
    return totalCount() > 0;
  }

  /**
   * Merges this report with another.
   *
   * @param other Report to merge
   * @return Combined report
   */
  public RedactionReport merge(final RedactionReport other) {
    if (other == null) {
      return this;
    }
    return new RedactionReport(
        emailCount + other.emailCount(),
        creditCardCount + other.creditCardCount(),
        pemKeyCount + other.pemKeyCount(),
        authTokenCount + other.authTokenCount(),
        jwtCount + other.jwtCount(),
        dictionaryCount + other.dictionaryCount(),
        mlEntityCount + other.mlEntityCount());
  }

  private static int countByType(final Map<String, Long> counts, final String findingType) {
    return Math.toIntExact(counts.getOrDefault(findingType, ZERO_COUNT));
  }

  @Override
  public String toString() {
    if (!hasDetections()) {
      return "RedactionReport{clean}";
    }
    final StringBuilder sb = new StringBuilder("RedactionReport{");
    appendIfPositive(sb, "emails", emailCount);
    appendIfPositive(sb, "creditCards", creditCardCount);
    appendIfPositive(sb, "pemKeys", pemKeyCount);
    appendIfPositive(sb, "authTokens", authTokenCount);
    appendIfPositive(sb, "jwt", jwtCount);
    appendIfPositive(sb, "dictionary", dictionaryCount);
    appendIfPositive(sb, "mlEntities", mlEntityCount);
    sb.append("}");
    return sb.toString();
  }

  private void appendIfPositive(final StringBuilder sb, final String name, final int count) {
    if (count > 0) {
      if (sb.charAt(sb.length() - 1) != '{') {
        sb.append(", ");
      }
      sb.append(name).append("=").append(count);
    }
  }
}
