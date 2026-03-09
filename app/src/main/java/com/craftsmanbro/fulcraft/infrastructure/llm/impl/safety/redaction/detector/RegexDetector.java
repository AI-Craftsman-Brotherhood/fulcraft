package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based sensitive data detector.
 *
 * <p>Detects sensitive data patterns using regular expressions. This is a refactored implementation
 * of the original SensitiveDataRedactor patterns, now conforming to the SensitiveDetector
 * interface.
 *
 * <p>Detected patterns include:
 *
 * <ul>
 *   <li>Email addresses
 *   <li>Credit card numbers (Luhn-validated)
 *   <li>PEM private keys
 *   <li>Authorization tokens (Bearer, API keys)
 *   <li>JWT tokens
 * </ul>
 */
public final class RegexDetector implements SensitiveDetector {

  public static final String NAME = "regex";

  // Detection types
  public static final String TYPE_EMAIL = "EMAIL";

  public static final String TYPE_CREDIT_CARD = "CREDIT_CARD";

  public static final String TYPE_PEM_KEY = "PEM_KEY";

  public static final String TYPE_AUTH_TOKEN = "AUTH_TOKEN";

  public static final String TYPE_JWT = "JWT";

  // Rule IDs
  private static final String RULE_EMAIL = "regex:email";

  private static final String RULE_CREDIT_CARD = "regex:credit_card";

  private static final String RULE_PEM_KEY = "regex:pem_key";

  private static final String RULE_AUTH_BEARER = "regex:auth_bearer";

  private static final String RULE_KEY_VALUE = "regex:key_value";

  private static final String RULE_JWT = "regex:jwt";

  // Confidence scores
  private static final double CONFIDENCE_PEM_KEY = 1.0;

  private static final double CONFIDENCE_AUTH_BEARER = 0.95;

  private static final double CONFIDENCE_JWT = 0.95;

  private static final double CONFIDENCE_EMAIL = 0.90;

  private static final double CONFIDENCE_KEY_VALUE = 0.85;

  private static final double CONFIDENCE_CREDIT_CARD = 0.98;

  private static final int AUTH_BEARER_SECRET_GROUP = 2;

  // Patterns (from original SensitiveDataRedactor)
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", Pattern.CASE_INSENSITIVE);

  private static final Pattern PEM_KEY_PATTERN =
      Pattern.compile(
          "-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----[\\s\\S]*?-----END (?:[A-Z ]+ )?PRIVATE KEY-----",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern AUTH_BEARER_PATTERN =
      Pattern.compile("(?i)(\\bAuthorization\\s*:\\s*Bearer\\s+)([A-Z0-9._+/=-]+)");

  private static final Pattern KEY_VALUE_PREFIX_PATTERN =
      Pattern.compile(
          "(?i)\\b[A-Z0-9_-]*(?:api[_-]?key|token|access[_-]?token|secret|password)[A-Z0-9_-]*\\b\\s*[:=]\\s*");

  private static final Pattern JWT_PATTERN =
      Pattern.compile(
          "(?<![A-Za-z0-9_\\-])([A-Za-z0-9_\\-]{10,})\\.([A-Za-z0-9_\\-]{10,})\\.([A-Za-z0-9_\\-]{8,})(?![A-Za-z0-9_\\-])");

  private static final Pattern CREDIT_CARD_CANDIDATE_PATTERN =
      Pattern.compile("(?<!\\d)(?:\\d[ -]*){13,19}(?!\\d)");

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isEnabled(final DetectionContext ctx) {
    return ctx.isDetectorEnabled(NAME);
  }

  @Override
  public DetectionResult detect(final String text, final DetectionContext ctx) {
    if (text == null || text.isEmpty()) {
      return DetectionResult.EMPTY;
    }
    final List<Finding> findings = new ArrayList<>();
    // PEM key detection (high confidence - very specific pattern)
    detectPattern(text, PEM_KEY_PATTERN, TYPE_PEM_KEY, RULE_PEM_KEY, CONFIDENCE_PEM_KEY, findings);
    // Bearer token detection
    detectAuthBearer(text, findings);
    // Key-value secret patterns
    detectKeyValue(text, findings);
    // JWT detection
    detectPattern(text, JWT_PATTERN, TYPE_JWT, RULE_JWT, CONFIDENCE_JWT, findings);
    // Email detection
    detectPattern(text, EMAIL_PATTERN, TYPE_EMAIL, RULE_EMAIL, CONFIDENCE_EMAIL, findings);
    // Credit card detection (with Luhn validation)
    detectCreditCards(text, findings);
    // Filter out allowlisted terms
    return DetectionResult.of(filterAllowlisted(findings, ctx));
  }

  private void detectPattern(
      final String text,
      final Pattern pattern,
      final String type,
      final String ruleId,
      final double confidence,
      final List<Finding> findings) {
    final Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      findings.add(
          new Finding(type, matcher.start(), matcher.end(), confidence, matcher.group(), ruleId));
    }
  }

  private void detectAuthBearer(final String text, final List<Finding> findings) {
    final Matcher matcher = AUTH_BEARER_PATTERN.matcher(text);
    while (matcher.find()) {
      // The secret is in AUTH_BEARER_SECRET_GROUP.
      final int secretStart = matcher.start(AUTH_BEARER_SECRET_GROUP);
      final int secretEnd = matcher.end(AUTH_BEARER_SECRET_GROUP);
      final String secret = matcher.group(AUTH_BEARER_SECRET_GROUP);
      findings.add(
          new Finding(
              TYPE_AUTH_TOKEN,
              secretStart,
              secretEnd,
              CONFIDENCE_AUTH_BEARER,
              secret,
              RULE_AUTH_BEARER));
    }
  }

  private void detectKeyValue(final String text, final List<Finding> findings) {
    final Matcher matcher = KEY_VALUE_PREFIX_PATTERN.matcher(text);
    int searchIndex = 0;
    while (searchIndex < text.length() && matcher.find(searchIndex)) {
      final int valueStart = matcher.end();
      int nextSearchIndex = valueStart;
      int secretStart = -1;
      int secretEnd = -1;
      boolean shouldAdd = false;
      if (valueStart < text.length()) {
        final char firstChar = text.charAt(valueStart);
        if (firstChar == '"' || firstChar == '\'') {
          secretStart = valueStart + 1;
          final int closing = text.indexOf(firstChar, secretStart);
          if (closing == -1) {
            nextSearchIndex = Math.min(valueStart + 1, text.length());
          } else {
            secretEnd = closing;
            shouldAdd = true;
            nextSearchIndex = Math.max(secretEnd, matcher.end());
          }
        } else {
          secretStart = valueStart;
          secretEnd = findBareValueEnd(text, valueStart);
          if (secretEnd > secretStart) {
            shouldAdd = true;
            nextSearchIndex = Math.max(secretEnd, matcher.end());
          } else {
            nextSearchIndex = Math.min(matcher.end() + 1, text.length());
          }
        }
      }
      if (shouldAdd) {
        final String secret = text.substring(secretStart, secretEnd);
        findings.add(
            new Finding(
                TYPE_AUTH_TOKEN,
                secretStart,
                secretEnd,
                CONFIDENCE_KEY_VALUE,
                secret,
                RULE_KEY_VALUE));
      }
      searchIndex = nextSearchIndex;
    }
  }

  private void detectCreditCards(final String text, final List<Finding> findings) {
    final Matcher matcher = CREDIT_CARD_CANDIDATE_PATTERN.matcher(text);
    while (matcher.find()) {
      final String candidate = matcher.group();
      final String digits = candidate.replaceAll("\\D", "");
      if (digits.length() >= 13 && digits.length() <= 19 && passesLuhn(digits)) {
        findings.add(
            new Finding(
                TYPE_CREDIT_CARD,
                matcher.start(),
                matcher.end(),
                CONFIDENCE_CREDIT_CARD,
                candidate,
                RULE_CREDIT_CARD));
      }
    }
  }

  private int findBareValueEnd(final String text, final int start) {
    int index = start;
    while (index < text.length()) {
      final char current = text.charAt(index);
      if (Character.isWhitespace(current) || current == '"' || current == '\'' || current == ';') {
        break;
      }
      index++;
    }
    return index;
  }

  private boolean passesLuhn(final String digits) {
    int sum = 0;
    boolean doubleDigit = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int digit = digits.charAt(i) - '0';
      if (doubleDigit) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      doubleDigit = !doubleDigit;
    }
    return sum % 10 == 0;
  }

  private List<Finding> filterAllowlisted(
      final List<Finding> findings, final DetectionContext ctx) {
    if (findings.isEmpty() || ctx.getAllowlistTerms().isEmpty()) {
      return findings;
    }
    return findings.stream().filter(f -> !ctx.isAllowlisted(f.snippet())).toList();
  }
}
