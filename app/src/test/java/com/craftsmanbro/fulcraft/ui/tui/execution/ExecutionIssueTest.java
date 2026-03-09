package com.craftsmanbro.fulcraft.ui.tui.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionIssueTest {

  @Test
  @DisplayName("constructor normalizes line separators in cause")
  void constructorNormalizesLineSeparatorsInCause() {
    ExecutionIssue issue =
        new ExecutionIssue(
            IssueCategory.EXCEPTION, "com.example.Target", "line1\r\nline2\rline3", "RUN");

    assertEquals("line1\nline2\nline3", issue.cause());
  }

  @Test
  @DisplayName("fromException rejects null exception")
  void fromExceptionRejectsNullException() {
    assertThrows(
        NullPointerException.class,
        () -> ExecutionIssue.fromException("com.example.Target", "RUN", null));
  }

  @Test
  @DisplayName("fromException falls back to exception class name when message is blank")
  void fromExceptionFallsBackToClassNameWhenMessageBlank() {
    ExecutionIssue issue =
        ExecutionIssue.fromException("com.example.Target", "RUN", new IllegalStateException("   "));

    assertEquals("IllegalStateException", issue.cause());
  }

  @Test
  @DisplayName("fromException falls back to exception class name when message is null")
  void fromExceptionFallsBackToClassNameWhenMessageNull() {
    ExecutionIssue issue =
        ExecutionIssue.fromException("com.example.Target", "RUN", new RuntimeException());

    assertEquals("RuntimeException", issue.cause());
  }

  @Test
  @DisplayName("testFailure truncates and normalizes multiline message")
  void testFailureTruncatesAndNormalizesMultilineMessage() {
    ExecutionIssue issue =
        ExecutionIssue.testFailure("MyTest", "RUN", "line1\r\nline2\rline3\nline4");

    assertEquals("line1\nline2\nline3", issue.cause());
  }

  @Test
  @DisplayName("compileError truncates and normalizes multiline message")
  void compileErrorTruncatesAndNormalizesMultilineMessage() {
    ExecutionIssue issue =
        ExecutionIssue.compileError("MyTest", "GENERATE", "line1\r\nline2\nline3\nline4");

    assertEquals("line1\nline2\nline3", issue.cause());
  }

  @Test
  @DisplayName("testFailure uses default message when input is null")
  void testFailureUsesDefaultMessageWhenInputNull() {
    ExecutionIssue issue = ExecutionIssue.testFailure("MyTest", "RUN", null);

    assertEquals("Test assertion failed", issue.cause());
  }

  @Test
  @DisplayName("compileError uses default message when input is blank")
  void compileErrorUsesDefaultMessageWhenInputBlank() {
    ExecutionIssue issue = ExecutionIssue.compileError("MyTest", "GENERATE", " ");

    assertEquals("Compilation failed", issue.cause());
  }

  @Test
  @DisplayName("constructor rejects blank target identifier and blank cause")
  void constructorRejectsBlankValues() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExecutionIssue(IssueCategory.EXCEPTION, " ", "cause", "RUN"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExecutionIssue(IssueCategory.EXCEPTION, "Target", " ", "RUN"));
  }

  @Test
  @DisplayName("toDisplaySummary uses category display name")
  void toDisplaySummaryUsesCategoryDisplayName() {
    ExecutionIssue issue =
        new ExecutionIssue(
            IssueCategory.COMPILE_ERROR, "com.example.MyTest", "compile failed", "GENERATE");

    assertEquals("[Compile Error] com.example.MyTest at stage GENERATE", issue.toDisplaySummary());
  }
}
