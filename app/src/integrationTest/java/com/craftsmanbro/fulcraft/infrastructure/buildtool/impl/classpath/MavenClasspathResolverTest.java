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
 * Tests for {@link MavenClasspathResolver}.
 *
 * <p>Tests input validation and edge cases. Actual Maven execution is not tested as it requires
 * Maven to be installed or a project with wrapper.
 */
class MavenClasspathResolverTest {

  @TempDir Path tempDir;

  @Test
  void resolveCompileClasspathAttempt_withNullProjectRoot_returnsFalse() {
    ClasspathAttemptResult result = MavenClasspathResolver.resolveCompileClasspathAttempt(null);

    assertFalse(result.success());
    assertEquals("maven", result.tool());
    assertNotNull(result.message());
    assertTrue(result.message().contains("Invalid project root"));
  }

  @Test
  void resolveCompileClasspathAttempt_withNonExistentDirectory_returnsFalse() {
    Path nonExistent = tempDir.resolve("does-not-exist");

    ClasspathAttemptResult result =
        MavenClasspathResolver.resolveCompileClasspathAttempt(nonExistent);

    assertFalse(result.success());
    assertEquals("maven", result.tool());
    assertTrue(result.message().contains("Invalid project root"));
  }

  @Test
  void resolveCompileClasspathAttempt_withFileInsteadOfDirectory_returnsFalse() throws IOException {
    Path file = tempDir.resolve("not-a-dir.txt");
    Files.writeString(file, "content");

    ClasspathAttemptResult result = MavenClasspathResolver.resolveCompileClasspathAttempt(file);

    assertFalse(result.success());
    assertTrue(result.message().contains("Invalid project root"));
  }

  @Test
  void resolveCompileClasspath_withNullProjectRoot_returnsEmptyList() {
    var entries = MavenClasspathResolver.resolveCompileClasspath(null);

    assertNotNull(entries);
    assertTrue(entries.isEmpty());
  }

  @Test
  void resolveCompileClasspath_withNonExistentDirectory_returnsEmptyList() {
    Path nonExistent = tempDir.resolve("non-existent-dir");

    var entries = MavenClasspathResolver.resolveCompileClasspath(nonExistent);

    assertNotNull(entries);
    assertTrue(entries.isEmpty());
  }
}
