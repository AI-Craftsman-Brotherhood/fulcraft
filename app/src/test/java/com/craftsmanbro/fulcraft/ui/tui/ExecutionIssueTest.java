package com.craftsmanbro.fulcraft.ui.tui;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueCategory;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionIssue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link ExecutionIssue}. */
class ExecutionIssueTest {

  @Nested
  @DisplayName("constructor validation")
  class ConstructorValidationTests {

    @Test
    @DisplayName("rejects null category")
    void rejectsNullCategory() {
      assertThrows(
          NullPointerException.class, () -> new ExecutionIssue(null, "target", "cause", "stage"));
    }

    @Test
    @DisplayName("rejects null targetIdentifier")
    void rejectsNullTarget() {
      assertThrows(
          NullPointerException.class,
          () -> new ExecutionIssue(IssueCategory.EXCEPTION, null, "cause", "stage"));
    }

    @Test
    @DisplayName("rejects null cause")
    void rejectsNullCause() {
      assertThrows(
          NullPointerException.class,
          () -> new ExecutionIssue(IssueCategory.EXCEPTION, "target", null, "stage"));
    }

    @Test
    @DisplayName("rejects null stageName")
    void rejectsNullStageName() {
      assertThrows(
          NullPointerException.class,
          () -> new ExecutionIssue(IssueCategory.EXCEPTION, "target", "cause", null));
    }

    @Test
    @DisplayName("rejects blank targetIdentifier")
    void rejectsBlankTarget() {
      assertThrows(
          IllegalArgumentException.class,
          () -> new ExecutionIssue(IssueCategory.EXCEPTION, "  ", "cause", "stage"));
    }

    @Test
    @DisplayName("rejects blank cause")
    void rejectsBlankCause() {
      assertThrows(
          IllegalArgumentException.class,
          () -> new ExecutionIssue(IssueCategory.EXCEPTION, "target", "  ", "stage"));
    }

    @Test
    @DisplayName("accepts valid parameters")
    void acceptsValidParams() {
      ExecutionIssue issue =
          new ExecutionIssue(
              IssueCategory.TEST_FAILURE, "com.example.MyClass", "Assertion failed", "RUN");

      assertEquals(IssueCategory.TEST_FAILURE, issue.category());
      assertEquals("com.example.MyClass", issue.targetIdentifier());
      assertEquals("Assertion failed", issue.cause());
      assertEquals("RUN", issue.stageName());
    }
  }

  @Nested
  @DisplayName("fromException factory")
  class FromExceptionTests {

    @Test
    @DisplayName("creates issue from exception with message")
    void createsFromExceptionWithMessage() {
      Exception ex = new RuntimeException("Something went wrong");
      ExecutionIssue issue = ExecutionIssue.fromException("MyClass", "GENERATE", ex);

      assertEquals(IssueCategory.EXCEPTION, issue.category());
      assertEquals("MyClass", issue.targetIdentifier());
      assertEquals("Something went wrong", issue.cause());
      assertEquals("GENERATE", issue.stageName());
    }

    @Test
    @DisplayName("uses exception class name when message is null")
    void usesClassNameWhenMessageNull() {
      Exception ex = new NullPointerException();
      ExecutionIssue issue = ExecutionIssue.fromException("MyClass", "RUN", ex);

      assertEquals("NullPointerException", issue.cause());
    }

    @Test
    @DisplayName("truncates cause to 3 lines")
    void truncatesCauseTo3Lines() {
      Exception ex = new RuntimeException("Line1\nLine2\nLine3\nLine4\nLine5");
      ExecutionIssue issue = ExecutionIssue.fromException("MyClass", "GENERATE", ex);

      String cause = issue.cause();
      long lineCount = cause.chars().filter(c -> c == '\n').count() + 1;
      assertTrue(lineCount <= 3, "Cause should have at most 3 lines");
    }
  }

  @Nested
  @DisplayName("testFailure factory")
  class TestFailureTests {

    @Test
    @DisplayName("creates test failure issue")
    void createsTestFailureIssue() {
      ExecutionIssue issue =
          ExecutionIssue.testFailure("MyClassTest", "RUN", "Expected 5 but was 3");

      assertEquals(IssueCategory.TEST_FAILURE, issue.category());
      assertEquals("MyClassTest", issue.targetIdentifier());
      assertEquals("Expected 5 but was 3", issue.cause());
      assertEquals("RUN", issue.stageName());
    }

    @Test
    @DisplayName("uses default message when null")
    void usesDefaultWhenNull() {
      ExecutionIssue issue = ExecutionIssue.testFailure("MyClass", "RUN", null);
      assertEquals("Test assertion failed", issue.cause());
    }

    @Test
    @DisplayName("uses default message when blank")
    void usesDefaultWhenBlank() {
      ExecutionIssue issue = ExecutionIssue.testFailure("MyClass", "RUN", "  ");
      assertEquals("Test assertion failed", issue.cause());
    }
  }

  @Nested
  @DisplayName("compileError factory")
  class CompileErrorTests {

    @Test
    @DisplayName("creates compile error issue")
    void createsCompileErrorIssue() {
      ExecutionIssue issue =
          ExecutionIssue.compileError("MyClass", "GENERATE", "Cannot resolve symbol 'foo'");

      assertEquals(IssueCategory.COMPILE_ERROR, issue.category());
      assertEquals("MyClass", issue.targetIdentifier());
      assertEquals("Cannot resolve symbol 'foo'", issue.cause());
      assertEquals("GENERATE", issue.stageName());
    }

    @Test
    @DisplayName("uses default message when null")
    void usesDefaultWhenNull() {
      ExecutionIssue issue = ExecutionIssue.compileError("MyClass", "GENERATE", null);
      assertEquals("Compilation failed", issue.cause());
    }
  }

  @Nested
  @DisplayName("toDisplaySummary")
  class ToDisplaySummaryTests {

    @Test
    @DisplayName("returns formatted summary")
    void returnsFormattedSummary() {
      ExecutionIssue issue =
          new ExecutionIssue(
              IssueCategory.EXCEPTION, "com.example.MyClass", "Error message", "GENERATE");

      String summary = issue.toDisplaySummary();
      assertTrue(summary.contains("Exception"));
      assertTrue(summary.contains("com.example.MyClass"));
      assertTrue(summary.contains("GENERATE"));
    }
  }
}
