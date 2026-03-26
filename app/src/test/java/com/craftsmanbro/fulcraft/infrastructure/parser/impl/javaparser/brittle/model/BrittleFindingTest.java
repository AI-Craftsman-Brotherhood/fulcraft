package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.RuleId;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.Severity;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BrittleFinding} record.
 *
 * <p>Verifies record construction, validation, enums, and toString.
 */
class BrittleFindingTest {

  // --- Constructor validation tests ---

  @Test
  void constructor_withNullRuleId_throwsNullPointerException() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> new BrittleFinding(null, Severity.ERROR, "file.java", 1, "message", "evidence"));

    assertTrue(exception.getMessage().endsWith("ruleId"), exception.getMessage());
  }

  @Test
  void constructor_withNullSeverity_throwsNullPointerException() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                new BrittleFinding(RuleId.REFLECTION, null, "file.java", 1, "message", "evidence"));

    assertTrue(exception.getMessage().endsWith("severity"), exception.getMessage());
  }

  @Test
  void constructor_withNullFilePath_throwsNullPointerException() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                new BrittleFinding(
                    RuleId.REFLECTION, Severity.ERROR, null, 1, "message", "evidence"));

    assertTrue(exception.getMessage().endsWith("filePath"), exception.getMessage());
  }

  @Test
  void constructor_withNullMessage_throwsNullPointerException() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                new BrittleFinding(
                    RuleId.REFLECTION, Severity.ERROR, "file.java", 1, null, "evidence"));

    assertTrue(exception.getMessage().endsWith("message"), exception.getMessage());
  }

  @Test
  void constructor_withZeroLineNumber_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BrittleFinding(
                    RuleId.OVER_MOCK,
                    Severity.WARNING,
                    "MockTest.java",
                    0,
                    "Too many mocks",
                    null));

    assertTrue(exception.getMessage().contains("lineNumber"), exception.getMessage());
  }

  @Test
  void constructor_withLineNumberLessThanNegativeOne_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BrittleFinding(
                    RuleId.TIME, Severity.WARNING, "TimeTest.java", -2, "Invalid line", null));

    assertTrue(exception.getMessage().contains("lineNumber"), exception.getMessage());
  }

  @Test
  void constructor_withNullEvidence_isAllowed() {
    BrittleFinding finding =
        new BrittleFinding(RuleId.REFLECTION, Severity.WARNING, "file.java", 1, "message", null);

    assertNotNull(finding);
    assertNull(finding.evidence());
  }

  @Test
  void constructor_withValidArgs_createsRecord() {
    BrittleFinding finding =
        new BrittleFinding(
            RuleId.SLEEP,
            Severity.WARNING,
            "TestFile.java",
            42,
            "Avoid sleep",
            "Thread.sleep(1000)");

    assertEquals(RuleId.SLEEP, finding.ruleId());
    assertEquals(Severity.WARNING, finding.severity());
    assertEquals("TestFile.java", finding.filePath());
    assertEquals(42, finding.lineNumber());
    assertEquals("Avoid sleep", finding.message());
    assertEquals("Thread.sleep(1000)", finding.evidence());
  }

  // --- RuleId enum tests ---

  @Test
  void ruleId_hasExpectedValues() {
    RuleId[] values = RuleId.values();

    assertEquals(5, values.length);
    assertEquals(RuleId.REFLECTION, RuleId.valueOf("REFLECTION"));
    assertEquals(RuleId.SLEEP, RuleId.valueOf("SLEEP"));
    assertEquals(RuleId.TIME, RuleId.valueOf("TIME"));
    assertEquals(RuleId.RANDOM, RuleId.valueOf("RANDOM"));
    assertEquals(RuleId.OVER_MOCK, RuleId.valueOf("OVER_MOCK"));
  }

  // --- Severity enum tests ---

  @Test
  void severity_hasExpectedValues() {
    Severity[] values = Severity.values();

    assertEquals(2, values.length);
    assertEquals(Severity.ERROR, Severity.valueOf("ERROR"));
    assertEquals(Severity.WARNING, Severity.valueOf("WARNING"));
  }

  // --- toString tests ---

  @Test
  void toString_withPositiveLineNumber_includesLineNumber() {
    BrittleFinding finding =
        new BrittleFinding(
            RuleId.TIME, Severity.ERROR, "MyTest.java", 100, "Time-dependent code", null);

    assertEquals("[ERROR] TIME at MyTest.java:100 - Time-dependent code", finding.toString());
  }

  @Test
  void toString_withNegativeLineNumber_omitsLineNumber() {
    BrittleFinding finding =
        new BrittleFinding(
            RuleId.RANDOM, Severity.WARNING, "RandomTest.java", -1, "Random usage", null);

    String result = finding.toString();

    assertEquals("[WARNING] RANDOM at RandomTest.java - Random usage", result);
    assertFalse(result.contains(":-1"));
  }

  @Test
  void toString_doesNotExposeEvidence() {
    BrittleFinding finding =
        new BrittleFinding(
            RuleId.REFLECTION,
            Severity.ERROR,
            "ReflectionTest.java",
            12,
            "Reflection usage",
            "field.setAccessible(true)");

    String result = finding.toString();

    assertTrue(result.contains("Reflection usage"));
    assertFalse(result.contains("setAccessible"));
  }

  @Test
  void equalsAndHashCode_withSameValues_areEqual() {
    BrittleFinding first =
        new BrittleFinding(
            RuleId.SLEEP, Severity.WARNING, "TestFile.java", 42, "Avoid sleep", "Thread.sleep(1)");
    BrittleFinding second =
        new BrittleFinding(
            RuleId.SLEEP, Severity.WARNING, "TestFile.java", 42, "Avoid sleep", "Thread.sleep(1)");

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }
}
