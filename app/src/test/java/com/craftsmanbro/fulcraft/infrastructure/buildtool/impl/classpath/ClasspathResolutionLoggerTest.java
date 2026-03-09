package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.ClasspathResolutionResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ClasspathResolutionLogger}.
 *
 * <p>Verifies that logging methods handle various result states without throwing exceptions. Since
 * the Logger class writes to console/file, we mainly verify that the methods execute safely for all
 * edge cases.
 */
class ClasspathResolutionLoggerTest {

  @Test
  void logAttempts_withNullResult_doesNotThrow() {
    assertDoesNotThrow(() -> ClasspathResolutionLogger.logAttempts(null));
  }

  @Test
  void logAttempts_withEmptyResultAndNoAttempts_doesNotThrow() {
    ClasspathResolutionResult result = new ClasspathResolutionResult(List.of(), null, List.of());

    assertDoesNotThrow(() -> ClasspathResolutionLogger.logAttempts(result));
  }

  @Test
  void logAttempts_withSuccessfulResult_doesNotThrow() {
    List<ClasspathResolutionResult.Attempt> attempts =
        List.of(new ClasspathResolutionResult.Attempt("gradle", true, 0, null));
    ClasspathResolutionResult result =
        new ClasspathResolutionResult(
            List.of(Path.of("/lib/a.jar"), Path.of("/lib/b.jar")), "gradle", attempts);

    assertDoesNotThrow(() -> ClasspathResolutionLogger.logAttempts(result));
  }

  @Test
  void logAttempts_withSuccessfulResultAndNullTool_doesNotThrow() {
    ClasspathResolutionResult result =
        new ClasspathResolutionResult(List.of(Path.of("/lib/a.jar")), null, List.of());

    assertDoesNotThrow(() -> ClasspathResolutionLogger.logAttempts(result));
  }

  @Test
  void logAttempts_withFailedAttempts_doesNotThrow() {
    List<ClasspathResolutionResult.Attempt> attempts =
        List.of(
            new ClasspathResolutionResult.Attempt("gradle", false, 1, "Build failed"),
            new ClasspathResolutionResult.Attempt("maven", false, 127, "Command not found"));
    ClasspathResolutionResult result =
        new ClasspathResolutionResult(List.of(), "unknown", attempts);

    assertDoesNotThrow(() -> ClasspathResolutionLogger.logAttempts(result));
  }

  @Test
  void logAttempts_withMixedAttempts_doesNotThrow() {
    List<ClasspathResolutionResult.Attempt> attempts =
        List.of(
            new ClasspathResolutionResult.Attempt("gradle", false, 1, "Build failed"),
            new ClasspathResolutionResult.Attempt("maven", true, 0, null));
    ClasspathResolutionResult result =
        new ClasspathResolutionResult(List.of(Path.of("/lib/dep.jar")), "maven", attempts);

    assertDoesNotThrow(() -> ClasspathResolutionLogger.logAttempts(result));
  }

  @Test
  void logAttempts_withNullExitCode_doesNotThrow() {
    List<ClasspathResolutionResult.Attempt> attempts =
        List.of(new ClasspathResolutionResult.Attempt("gradle", false, null, "Interrupted"));
    ClasspathResolutionResult result = new ClasspathResolutionResult(List.of(), "gradle", attempts);

    assertDoesNotThrow(() -> ClasspathResolutionLogger.logAttempts(result));
  }

  @Test
  void logAttempts_withNullMessage_doesNotThrow() {
    List<ClasspathResolutionResult.Attempt> attempts =
        List.of(new ClasspathResolutionResult.Attempt("maven", false, 1, null));
    ClasspathResolutionResult result = new ClasspathResolutionResult(List.of(), "maven", attempts);

    assertDoesNotThrow(() -> ClasspathResolutionLogger.logAttempts(result));
  }

  @Test
  void logAttempts_withBlankMessage_doesNotThrow() {
    List<ClasspathResolutionResult.Attempt> attempts =
        List.of(new ClasspathResolutionResult.Attempt("gradle", false, 1, "   "));
    ClasspathResolutionResult result = new ClasspathResolutionResult(List.of(), "gradle", attempts);

    assertDoesNotThrow(() -> ClasspathResolutionLogger.logAttempts(result));
  }

  @Test
  void logAttempts_withEmptyAttemptsList_andNonEmptyResult_doesNotThrow() {
    ClasspathResolutionResult result =
        new ClasspathResolutionResult(List.of(Path.of("/lib/a.jar")), "gradle", List.of());

    assertDoesNotThrow(() -> ClasspathResolutionLogger.logAttempts(result));
  }
}
