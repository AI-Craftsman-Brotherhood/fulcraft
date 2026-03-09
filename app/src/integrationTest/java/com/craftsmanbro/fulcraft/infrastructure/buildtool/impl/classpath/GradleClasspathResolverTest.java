package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link GradleClasspathResolver}.
 *
 * <p>Tests input validation and edge cases. Actual Gradle execution is not tested as it requires a
 * real Gradle project and wrapper.
 */
class GradleClasspathResolverTest {

  @TempDir Path tempDir;

  @Test
  void resolveCompileClasspathAttempt_withNullProjectRoot_returnsFalse() {
    ClasspathAttemptResult result = GradleClasspathResolver.resolveCompileClasspathAttempt(null);

    assertFalse(result.success());
    assertEquals("gradle", result.tool());
    assertNotNull(result.message());
    assertTrue(result.message().contains("Invalid project root"));
  }

  @Test
  void resolveCompileClasspathAttempt_withNonExistentDirectory_returnsFalse() {
    Path nonExistent = tempDir.resolve("does-not-exist");

    ClasspathAttemptResult result =
        GradleClasspathResolver.resolveCompileClasspathAttempt(nonExistent);

    assertFalse(result.success());
    assertEquals("gradle", result.tool());
    assertTrue(result.message().contains("Invalid project root"));
  }

  @Test
  void resolveCompileClasspathAttempt_withFileInsteadOfDirectory_returnsFalse() throws IOException {
    Path file = tempDir.resolve("not-a-dir.txt");
    Files.writeString(file, "content");

    ClasspathAttemptResult result = GradleClasspathResolver.resolveCompileClasspathAttempt(file);

    assertFalse(result.success());
    assertTrue(result.message().contains("Invalid project root"));
  }

  @Test
  void resolveCompileClasspathAttempt_withoutGradleWrapper_returnsFalse() {
    // tempDir is empty, no gradlew exists
    ClasspathAttemptResult result = GradleClasspathResolver.resolveCompileClasspathAttempt(tempDir);

    assertFalse(result.success());
    assertEquals("gradle", result.tool());
    assertTrue(result.message().contains("Gradle wrapper not found"));
  }

  @Test
  void resolveCompileClasspath_withNullProjectRoot_returnsEmptyList() {
    var entries = GradleClasspathResolver.resolveCompileClasspath(null);

    assertNotNull(entries);
    assertTrue(entries.isEmpty());
  }

  @Test
  void resolveCompileClasspath_withoutGradleWrapper_returnsEmptyList() {
    var entries = GradleClasspathResolver.resolveCompileClasspath(tempDir);

    assertNotNull(entries);
    assertTrue(entries.isEmpty());
  }
}
