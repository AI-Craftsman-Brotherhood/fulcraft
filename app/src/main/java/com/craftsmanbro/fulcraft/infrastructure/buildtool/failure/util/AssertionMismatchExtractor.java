package com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.util;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured mismatch information from assertion failure messages.
 *
 * <p>This class analyzes assertion failure messages and stack traces to extract detailed
 * information about what went wrong, including expected/actual values and the type of mismatch.
 *
 * <h2>Supported Assertion Libraries</h2>
 *
 * <ul>
 *   <li>JUnit 5 - {@code expected: <X> but was: <Y>}
 *   <li>AssertJ - {@code expected: X but was: Y} or {@code Expecting: X to be equal to: Y}
 *   <li>Hamcrest - {@code Expected: X but: was Y}
 * </ul>
 *
 * @see MismatchType
 * @see com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml.JUnitXmlReportParser
 */
public class AssertionMismatchExtractor {

  /**
   * Extracted mismatch details from a test failure.
   *
   * @param mismatchType the categorized type of mismatch
   * @param expected the expected value (if extractable)
   * @param actual the actual value (if extractable)
   * @param delta the numeric difference for numeric mismatches
   * @param assertionLocation the location in test code where assertion failed
   * @param lineNumber the line number in test code
   * @param rawMessage the original failure message
   */
  public record MismatchDetails(
      @JsonProperty("mismatch_type") MismatchType mismatchType,
      @JsonProperty("expected") String expected,
      @JsonProperty("actual") String actual,
      @JsonProperty("delta") String delta,
      @JsonProperty("assertion_location") String assertionLocation,
      @JsonProperty("line_number") Integer lineNumber,
      @JsonProperty("raw_message") String rawMessage) {

    /**
     * Returns whether both expected and actual values were extracted.
     *
     * @return true if both values are available
     */
    public boolean hasExpectedActual() {
      return expected != null && actual != null;
    }
  }

  // Patterns for extracting expected/actual values
  // JUnit 5: "expected: <X> but was: <Y>"
  private static final Pattern JUNIT_PATTERN =
      Pattern.compile("expected:\\s*<([^>]*)>\\s*but was:\\s*<([^>]*)>");

  // AssertJ: "expected: X but was: Y" or "Expecting: X to be equal to: Y"
  private static final Pattern ASSERTJ_EXPECTED_PATTERN =
      Pattern.compile("expected:\\s*([^\\n]+?)\\s+but was:\\s*([^\\n]+)");

  private static final Pattern ASSERTJ_EXPECTING_PATTERN =
      Pattern.compile(
          "[Ee]xpecting[:\\s]+([^\\n]+?)\\s+(?:to be equal to|but was)[:\\s]+([^\\n]+)");

  // Hamcrest: "Expected: X\n but: was Y"
  private static final Pattern HAMCREST_PATTERN =
      Pattern.compile("Expected:\\s*([^\\n]+)\\s+but:\\s*(?:was\\s+)?([^\\n]+)");

  // Numeric patterns
  private static final Pattern INTEGER_PATTERN = Pattern.compile("^[+-]?\\d+$");

  private static final Pattern NUMERIC_PATTERN =
      Pattern.compile("^[+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$");

  private static final Pattern FLOAT_PATTERN =
      Pattern.compile("^[+-]?(?:\\d+\\.\\d*|\\d*\\.\\d+)(?:[eE][+-]?\\d+)?$");

  private static final Pattern NULL_WORD_PATTERN =
      Pattern.compile("\\bnull\\b", Pattern.CASE_INSENSITIVE);

  // Collection patterns
  private static final Pattern COLLECTION_PATTERN = Pattern.compile("\\[.*\\]");

  private static final Pattern MAP_PATTERN = Pattern.compile("\\{.*\\}");

  /**
   * Extract mismatch details from a failure message and stack trace.
   *
   * @param failureMessage the assertion failure message
   * @param stackTrace the stack trace from the failure
   * @return extracted mismatch details
   */
  public MismatchDetails extract(final String failureMessage, final String stackTrace) {
    if (failureMessage == null || failureMessage.isEmpty()) {
      return new MismatchDetails(
          MismatchType.UNKNOWN, null, null, null, null, null, failureMessage);
    }
    // Extract expected/actual using pattern matching
    final String[] expectedActual = extractExpectedActual(failureMessage);
    final String expected = expectedActual[0];
    final String actual = expectedActual[1];
    // Extract line number from stack trace
    Integer lineNumber = null;
    String assertionLocation = null;
    if (stackTrace != null) {
      lineNumber = extractLineNumber(stackTrace);
      assertionLocation = extractAssertionLocation(stackTrace);
    }
    // Classify mismatch type
    final MismatchType type = classifyMismatchType(expected, actual, failureMessage);
    // Calculate delta for numeric types
    final String delta = calculateDeltaIfNumeric(type, expected, actual);
    Logger.debug(
        "[MismatchExtractor] Extracted: type="
            + type
            + ", expected="
            + expected
            + ", actual="
            + actual);
    return new MismatchDetails(
        type, expected, actual, delta, assertionLocation, lineNumber, failureMessage);
  }

  private String[] extractExpectedActual(final String failureMessage) {
    // Try different patterns in order of specificity
    final Pattern[] patterns = {
      JUNIT_PATTERN, ASSERTJ_EXPECTED_PATTERN, ASSERTJ_EXPECTING_PATTERN, HAMCREST_PATTERN
    };
    for (final Pattern pattern : patterns) {
      final Matcher m = pattern.matcher(failureMessage);
      if (m.find()) {
        return new String[] {m.group(1).trim(), m.group(2).trim()};
      }
    }
    return new String[] {null, null};
  }

  private String calculateDeltaIfNumeric(
      final MismatchType type, final String expected, final String actual) {
    if ((type == MismatchType.NUMERIC || type == MismatchType.FLOAT_TOLERANCE)
        && expected != null
        && actual != null) {
      return calculateDelta(expected, actual);
    }
    return null;
  }

  private MismatchType classifyMismatchType(
      final String expected, final String actual, final String message) {
    // Check for null or exception related mismatches first
    final MismatchType nullOrException = classifyByNullOrException(expected, actual, message);
    if (nullOrException != null) {
      return nullOrException;
    }
    // Check expected/actual patterns
    if (expected != null && actual != null) {
      final MismatchType byValues = classifyByExpectedActual(expected, actual, message);
      if (byValues != null) {
        return byValues;
      }
    }
    // Check message for hints
    final MismatchType byMessage = classifyByMessageContent(message);
    if (byMessage != null) {
      return byMessage;
    }
    // Default to object equals
    if (expected != null && actual != null) {
      return MismatchType.OBJECT_EQUALS;
    }
    return MismatchType.UNKNOWN;
  }

  private MismatchType classifyByNullOrException(
      final String expected, final String actual, final String message) {
    // Check for null-related
    if ("null".equalsIgnoreCase(expected) || "null".equalsIgnoreCase(actual)) {
      return MismatchType.NULL_MISMATCH;
    }
    if (message != null && NULL_WORD_PATTERN.matcher(message).find()) {
      return MismatchType.NULL_MISMATCH;
    }
    // Check for exception message
    if (message != null && (message.contains("exception") || message.contains("Exception"))) {
      return MismatchType.EXCEPTION_MESSAGE;
    }
    return null;
  }

  private MismatchType classifyByExpectedActual(
      final String expected, final String actual, final String message) {
    // Numeric check
    if (isNumericMismatch(expected, actual)) {
      return classifyNumericType(expected, actual);
    }
    // Collection check
    if (COLLECTION_PATTERN.matcher(expected).find() || COLLECTION_PATTERN.matcher(actual).find()) {
      if (message != null && (message.contains("order") || message.contains("sorted"))) {
        return MismatchType.ORDERING;
      }
      return MismatchType.COLLECTION;
    }
    // Map check
    if (MAP_PATTERN.matcher(expected).find() || MAP_PATTERN.matcher(actual).find()) {
      return MismatchType.MAP;
    }
    // String check (quoted strings)
    if (isQuotedString(expected) || isQuotedString(actual)) {
      return MismatchType.STRING;
    }
    return null;
  }

  private boolean isNumericMismatch(final String expected, final String actual) {
    return NUMERIC_PATTERN.matcher(expected).matches() && NUMERIC_PATTERN.matcher(actual).matches();
  }

  private MismatchType classifyNumericType(final String expected, final String actual) {
    final boolean isExpectedInteger = INTEGER_PATTERN.matcher(expected).matches();
    final boolean isActualInteger = INTEGER_PATTERN.matcher(actual).matches();
    final boolean hasFloatValue =
        FLOAT_PATTERN.matcher(expected).matches() || FLOAT_PATTERN.matcher(actual).matches();
    if (!isExpectedInteger || !isActualInteger || hasFloatValue) {
      return MismatchType.FLOAT_TOLERANCE;
    }
    return MismatchType.NUMERIC;
  }

  private boolean isQuotedString(final String value) {
    return value.startsWith("\"") || value.startsWith("'");
  }

  private MismatchType classifyByMessageContent(final String message) {
    if (message == null) {
      return null;
    }
    if (message.contains("collection") || message.contains("list") || message.contains("size")) {
      return MismatchType.COLLECTION;
    }
    if (message.contains("order") || message.contains("sorted")) {
      return MismatchType.ORDERING;
    }
    if (message.contains("string") || message.contains("String")) {
      return MismatchType.STRING;
    }
    return null;
  }

  private Integer extractLineNumber(final String stackTrace) {
    // Look for test file line numbers (e.g., "at
    // com.example.TestClass.testMethod(TestClass.java:42)")
    final Pattern linePattern = Pattern.compile("at\\s+[\\w.$]+\\([\\w$]+\\.java:(\\d+)\\)");
    final Matcher m = linePattern.matcher(stackTrace);
    if (m.find()) {
      try {
        return Integer.parseInt(m.group(1));
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private String extractAssertionLocation(final String stackTrace) {
    // Prefer test-like frames; fall back to the first non-framework frame.
    final Pattern locPattern = Pattern.compile("at\\s+([\\w.$]+)\\(([\\w$]+\\.java:\\d+)\\)");
    final Matcher m = locPattern.matcher(stackTrace);
    String fallback = null;
    while (m.find()) {
      final String classMethod = m.group(1);
      if (isFrameworkClass(classMethod)) {
        continue;
      }
      final String location = classMethod + "(" + m.group(2) + ")";
      if (isTestLikeClass(classMethod)) {
        return location;
      }
      if (fallback == null) {
        fallback = location;
      }
    }
    return fallback;
  }

  private String calculateDelta(final String expected, final String actual) {
    try {
      if (INTEGER_PATTERN.matcher(expected).matches()
          && INTEGER_PATTERN.matcher(actual).matches()) {
        final long e = Long.parseLong(expected);
        final long a = Long.parseLong(actual);
        return String.valueOf(Math.abs(e - a));
      }
      final double e = Double.parseDouble(expected);
      final double a = Double.parseDouble(actual);
      return String.format("%.6f", Math.abs(e - a));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private boolean isFrameworkClass(final String classMethod) {
    return classMethod.startsWith("org.junit.")
        || classMethod.startsWith("org.assertj.")
        || classMethod.startsWith("org.hamcrest.")
        || classMethod.startsWith("java.")
        || classMethod.startsWith("jdk.")
        || classMethod.startsWith("sun.");
  }

  private boolean isTestLikeClass(final String classMethod) {
    return classMethod.contains("Test")
        || classMethod.contains("Spec")
        || classMethod.contains("IT")
        || classMethod.contains("Case");
  }
}
