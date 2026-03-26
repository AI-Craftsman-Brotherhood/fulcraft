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
 * Tests for {@link ClasspathAttemptResult} record.
 *
 * <p>Verifies field access, defensive copying, and conversion methods.
 */
class ClasspathAttemptResultTest {

  @Test
  void constructor_storesFieldsCorrectly() {
    List<Path> entries = List.of(Path.of("/lib/a.jar"), Path.of("/lib/b.jar"));
    ClasspathAttemptResult result = new ClasspathAttemptResult("gradle", entries, true, 0, null);

    assertEquals("gradle", result.tool());
    assertEquals(2, result.entries().size());
    assertTrue(result.success());
    assertEquals(0, result.exitCode());
    assertNull(result.message());
  }

  @Test
  void constructor_withNullEntries_returnsEmptyList() {
    ClasspathAttemptResult result = new ClasspathAttemptResult("maven", null, false, 1, "error");

    assertNotNull(result.entries());
    assertTrue(result.entries().isEmpty());
  }

  @Test
  void constructor_createsDefensiveCopyOfEntries() {
    List<Path> mutableList = new ArrayList<>();
    mutableList.add(Path.of("/lib/a.jar"));

    ClasspathAttemptResult result =
        new ClasspathAttemptResult("gradle", mutableList, true, 0, null);

    mutableList.add(Path.of("/lib/b.jar"));
    assertEquals(1, result.entries().size(), "Should not reflect changes to original list");
  }

  @Test
  void entries_returnsImmutableList() {
    List<Path> entries = List.of(Path.of("/lib/a.jar"));
    ClasspathAttemptResult result = new ClasspathAttemptResult("gradle", entries, true, 0, null);

    assertThrows(
        UnsupportedOperationException.class, () -> result.entries().add(Path.of("/x.jar")));
  }

  @Test
  void safeEntries_returnsEntriesWhenNotNull() {
    List<Path> entries = List.of(Path.of("/lib/a.jar"));
    ClasspathAttemptResult result = new ClasspathAttemptResult("gradle", entries, true, 0, null);

    List<Path> safe = result.safeEntries();
    assertNotNull(safe);
    assertEquals(1, safe.size());
  }

  @Test
  void safeEntries_returnsEmptyListWhenEntriesNull() {
    // Note: After compact constructor, entries will be List.of() not null
    ClasspathAttemptResult result =
        new ClasspathAttemptResult("gradle", null, false, null, "error");

    List<Path> safe = result.safeEntries();
    assertNotNull(safe);
    assertTrue(safe.isEmpty());
  }

  @Test
  void toAttempt_createsAttemptWithCorrectFields() {
    ClasspathAttemptResult result =
        new ClasspathAttemptResult("maven", List.of(Path.of("/a.jar")), true, 0, "success message");

    ClasspathResolutionResult.Attempt attempt = result.toAttempt();

    assertNotNull(attempt);
    assertEquals("maven", attempt.tool());
    assertTrue(attempt.success());
    assertEquals(0, attempt.exitCode());
    assertEquals("success message", attempt.message());
  }

  @Test
  void toAttempt_preservesNullExitCodeAndMessage() {
    ClasspathAttemptResult result =
        new ClasspathAttemptResult("gradle", List.of(), false, null, null);

    ClasspathResolutionResult.Attempt attempt = result.toAttempt();

    assertNull(attempt.exitCode());
    assertNull(attempt.message());
  }

  @Test
  void constructor_withFailureState_storesCorrectly() {
    ClasspathAttemptResult result =
        new ClasspathAttemptResult("maven", List.of(), false, 127, "Command not found");

    assertFalse(result.success());
    assertEquals(127, result.exitCode());
    assertEquals("Command not found", result.message());
    assertTrue(result.entries().isEmpty());
  }
}
