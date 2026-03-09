package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.ClasspathResolutionResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ClasspathResolutionResult}.
 *
 * <p>Verifies result storage, immutability, and isEmpty behavior.
 */
class ClasspathResolutionResultTest {

  @Test
  void constructor_storesFieldsCorrectly() {
    List<Path> entries = List.of(Path.of("/lib/a.jar"), Path.of("/lib/b.jar"));
    List<ClasspathResolutionResult.Attempt> attempts =
        List.of(new ClasspathResolutionResult.Attempt("gradle", true, 0, null));

    ClasspathResolutionResult result = new ClasspathResolutionResult(entries, "gradle", attempts);

    assertEquals(2, result.getEntries().size());
    assertEquals("gradle", result.getSelectedTool());
    assertEquals(1, result.getAttempts().size());
  }

  @Test
  void constructor_withNullEntries_returnsEmptyList() {
    ClasspathResolutionResult result = new ClasspathResolutionResult(null, "maven", List.of());

    assertNotNull(result.getEntries());
    assertTrue(result.getEntries().isEmpty());
  }

  @Test
  void constructor_withNullAttempts_returnsEmptyList() {
    ClasspathResolutionResult result =
        new ClasspathResolutionResult(List.of(Path.of("/a.jar")), "gradle", null);

    assertNotNull(result.getAttempts());
    assertTrue(result.getAttempts().isEmpty());
  }

  @Test
  void constructor_withNullSelectedTool_preservesNull() {
    ClasspathResolutionResult result = new ClasspathResolutionResult(List.of(), null, List.of());

    assertNull(result.getSelectedTool());
  }

  @Test
  void constructor_createsDefensiveCopyOfEntries() {
    List<Path> mutableEntries = new ArrayList<>();
    mutableEntries.add(Path.of("/lib/a.jar"));

    ClasspathResolutionResult result =
        new ClasspathResolutionResult(mutableEntries, "gradle", List.of());

    mutableEntries.add(Path.of("/lib/b.jar"));
    assertEquals(1, result.getEntries().size(), "Should not reflect changes to original list");
  }

  @Test
  void constructor_createsDefensiveCopyOfAttempts() {
    List<ClasspathResolutionResult.Attempt> mutableAttempts = new ArrayList<>();
    mutableAttempts.add(new ClasspathResolutionResult.Attempt("gradle", true, 0, null));

    ClasspathResolutionResult result =
        new ClasspathResolutionResult(List.of(), "gradle", mutableAttempts);

    mutableAttempts.add(new ClasspathResolutionResult.Attempt("maven", false, 1, "error"));
    assertEquals(1, result.getAttempts().size(), "Should not reflect changes to original list");
  }

  @Test
  void getEntries_returnsUnmodifiableList() {
    ClasspathResolutionResult result =
        new ClasspathResolutionResult(List.of(Path.of("/a.jar")), "gradle", List.of());

    assertThrows(
        UnsupportedOperationException.class, () -> result.getEntries().add(Path.of("/x.jar")));
  }

  @Test
  void getAttempts_returnsUnmodifiableList() {
    ClasspathResolutionResult result =
        new ClasspathResolutionResult(
            List.of(),
            "gradle",
            List.of(new ClasspathResolutionResult.Attempt("gradle", true, 0, null)));

    assertThrows(
        UnsupportedOperationException.class,
        () ->
            result
                .getAttempts()
                .add(new ClasspathResolutionResult.Attempt("maven", false, 1, "err")));
  }

  @Test
  void isEmpty_returnsTrueWhenNoEntries() {
    ClasspathResolutionResult result =
        new ClasspathResolutionResult(List.of(), "unknown", List.of());

    assertTrue(result.isEmpty());
  }

  @Test
  void isEmpty_returnsFalseWhenHasEntries() {
    ClasspathResolutionResult result =
        new ClasspathResolutionResult(List.of(Path.of("/lib/dep.jar")), "gradle", List.of());

    assertFalse(result.isEmpty());
  }

  @Test
  void attempt_record_storesFieldsCorrectly() {
    ClasspathResolutionResult.Attempt attempt =
        new ClasspathResolutionResult.Attempt("maven", false, 127, "Command failed");

    assertEquals("maven", attempt.tool());
    assertFalse(attempt.success());
    assertEquals(127, attempt.exitCode());
    assertEquals("Command failed", attempt.message());
  }

  @Test
  void attempt_record_allowsNullExitCodeAndMessage() {
    ClasspathResolutionResult.Attempt attempt =
        new ClasspathResolutionResult.Attempt("gradle", true, null, null);

    assertTrue(attempt.success());
    assertNull(attempt.exitCode());
    assertNull(attempt.message());
  }
}
