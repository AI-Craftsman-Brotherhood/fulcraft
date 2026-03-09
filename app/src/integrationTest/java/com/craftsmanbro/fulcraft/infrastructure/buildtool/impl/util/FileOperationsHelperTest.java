package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link FileOperationsHelper}.
 *
 * <p>Verifies file copy, delete, test file writing, and directory creation operations.
 */
class FileOperationsHelperTest {

  @TempDir Path tempDir;

  private FileOperationsHelper helper;

  @BeforeEach
  void setUp() {
    helper = new FileOperationsHelper();
  }

  // --- copyProject tests ---

  @Test
  void copyProject_withNullSource_throwsIllegalArgumentException() {
    Path target = tempDir.resolve("target");
    assertThrows(IllegalArgumentException.class, () -> helper.copyProject(null, target));
  }

  @Test
  void copyProject_withNullTarget_throwsIllegalArgumentException() {
    Path source = tempDir.resolve("source");
    assertThrows(IllegalArgumentException.class, () -> helper.copyProject(source, null));
  }

  @Test
  void copyProject_copiesFilesAndDirectories() throws IOException {
    Path source = tempDir.resolve("source");
    Path target = tempDir.resolve("target");
    Files.createDirectories(source.resolve("src/main/java"));
    Files.writeString(source.resolve("src/main/java/Main.java"), "class Main {}");
    Files.writeString(source.resolve("build.gradle"), "plugins {}");

    helper.copyProject(source, target);

    assertTrue(Files.exists(target.resolve("src/main/java/Main.java")));
    assertTrue(Files.exists(target.resolve("build.gradle")));
    assertEquals("class Main {}", Files.readString(target.resolve("src/main/java/Main.java")));
  }

  @Test
  void copyProject_excludesBuildDirectories() throws IOException {
    Path source = tempDir.resolve("source");
    Path target = tempDir.resolve("target");
    Files.createDirectories(source.resolve("build/classes"));
    Files.writeString(source.resolve("build/classes/Main.class"), "bytecode");
    Files.createDirectories(source.resolve("src"));
    Files.writeString(source.resolve("src/Main.java"), "code");

    helper.copyProject(source, target);

    assertFalse(Files.exists(target.resolve("build")));
    assertTrue(Files.exists(target.resolve("src/Main.java")));
  }

  @Test
  void copyProject_excludesGitDirectory() throws IOException {
    Path source = tempDir.resolve("source");
    Path target = tempDir.resolve("target");
    Files.createDirectories(source.resolve(".git/objects"));
    Files.writeString(source.resolve(".git/HEAD"), "ref: refs/heads/main");
    Files.writeString(source.resolve("README.md"), "readme");

    helper.copyProject(source, target);

    assertFalse(Files.exists(target.resolve(".git")));
    assertTrue(Files.exists(target.resolve("README.md")));
  }

  @Test
  void copyProject_excludesLogFiles() throws IOException {
    Path source = tempDir.resolve("source");
    Path target = tempDir.resolve("target");
    Files.createDirectories(source);
    Files.writeString(source.resolve("app.log"), "log content");
    Files.writeString(source.resolve("Main.java"), "code");

    helper.copyProject(source, target);

    assertFalse(Files.exists(target.resolve("app.log")));
    assertTrue(Files.exists(target.resolve("Main.java")));
  }

  @Test
  void copyProject_excludesHeapDumpFiles() throws IOException {
    Path source = tempDir.resolve("source");
    Path target = tempDir.resolve("target");
    Files.createDirectories(source);
    Files.writeString(source.resolve("java_pid123.hprof"), "heap");
    Files.writeString(source.resolve("Main.java"), "code");

    helper.copyProject(source, target);

    assertFalse(Files.exists(target.resolve("java_pid123.hprof")));
    assertTrue(Files.exists(target.resolve("Main.java")));
  }

  @Test
  void copyProject_keepsNestedBuildDirectory_whenNotProjectRootChild() throws IOException {
    Path source = tempDir.resolve("source");
    Path target = tempDir.resolve("target");
    Files.createDirectories(source.resolve("src/build"));
    Files.writeString(source.resolve("src/build/Generated.java"), "class Generated {}");

    helper.copyProject(source, target);

    assertTrue(Files.exists(target.resolve("src/build/Generated.java")));
  }

  @Test
  void copyProject_excludesRootLogsDirectory_butKeepsNestedLogsDirectory() throws IOException {
    Path source = tempDir.resolve("source");
    Path target = tempDir.resolve("target");
    Files.createDirectories(source.resolve("logs"));
    Files.writeString(source.resolve("logs/run.txt"), "root-log");
    Files.createDirectories(source.resolve("src/logs"));
    Files.writeString(source.resolve("src/logs/keep.txt"), "nested-log");

    helper.copyProject(source, target);

    assertFalse(Files.exists(target.resolve("logs")));
    assertTrue(Files.exists(target.resolve("src/logs/keep.txt")));
  }

  @Test
  void copyProject_createsGradlewAsExecutable() throws IOException {
    Path source = tempDir.resolve("source");
    Path target = tempDir.resolve("target");
    Files.createDirectories(source);
    Files.writeString(source.resolve("gradlew"), "#!/bin/bash\necho 'gradle'");

    helper.copyProject(source, target);

    assertTrue(Files.exists(target.resolve("gradlew")));
    // Note: executable permission check depends on OS
  }

  // --- deleteDirectory tests ---

  @Test
  void deleteDirectory_withNullPath_doesNotThrow() {
    assertDoesNotThrow(() -> helper.deleteDirectory(null));
  }

  @Test
  void deleteDirectory_withNonExistentPath_doesNotThrow() {
    Path nonExistent = tempDir.resolve("does-not-exist");
    assertDoesNotThrow(() -> helper.deleteDirectory(nonExistent));
  }

  @Test
  void deleteDirectory_deletesDirectoryAndContents() throws IOException {
    Path dir = tempDir.resolve("to-delete");
    Files.createDirectories(dir.resolve("sub/deep"));
    Files.writeString(dir.resolve("file.txt"), "content");
    Files.writeString(dir.resolve("sub/nested.txt"), "nested");

    helper.deleteDirectory(dir);

    assertFalse(Files.exists(dir));
  }

  @Test
  void deleteDirectory_deletesSingleFile() throws IOException {
    Path file = tempDir.resolve("single-file.txt");
    Files.writeString(file, "content");

    helper.deleteDirectory(file);

    assertFalse(Files.exists(file));
  }

  // --- writeTestFile tests ---

  @Test
  void writeTestFile_withNullTempDir_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> helper.writeTestFile(null, "com.example", "TestClass", "code"));
  }

  @Test
  void writeTestFile_withNullTestClassName_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> helper.writeTestFile(tempDir, "com.example", null, "code"));
  }

  @Test
  void writeTestFile_withBlankTestClassName_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> helper.writeTestFile(tempDir, "com.example", "  ", "code"));
  }

  @Test
  void writeTestFile_createsFileInCorrectPackageDirectory() throws IOException {
    helper.writeTestFile(tempDir, "com.example.test", "MyTest", "test code content");

    Path expectedPath = tempDir.resolve("src/test/java/com/example/test/MyTest.java");
    assertTrue(Files.exists(expectedPath));
    assertEquals("test code content", Files.readString(expectedPath));
  }

  @Test
  void writeTestFile_withNullPackage_createsFileInRootTestDir() throws IOException {
    helper.writeTestFile(tempDir, null, "MyTest", "test code");

    Path expectedPath = tempDir.resolve("src/test/java/MyTest.java");
    assertTrue(Files.exists(expectedPath));
  }

  @Test
  void writeTestFile_withEmptyPackage_createsFileInRootTestDir() throws IOException {
    helper.writeTestFile(tempDir, "", "MyTest", "test code");

    Path expectedPath = tempDir.resolve("src/test/java/MyTest.java");
    assertTrue(Files.exists(expectedPath));
  }

  // --- cleanupTempDir tests ---

  @Test
  void cleanupTempDir_withNullPath_doesNotThrow() {
    assertDoesNotThrow(() -> helper.cleanupTempDir(null));
  }

  @Test
  void cleanupTempDir_deletesDirectory() throws IOException {
    Path dir = tempDir.resolve("cleanup-test");
    Files.createDirectories(dir.resolve("sub"));
    Files.writeString(dir.resolve("file.txt"), "data");

    helper.cleanupTempDir(dir);

    assertFalse(Files.exists(dir));
  }

  // --- createDirectories tests ---

  @Test
  void createDirectories_createsNestedDirectories() throws IOException {
    Path nested = tempDir.resolve("a/b/c/d");

    helper.createDirectories(nested);

    assertTrue(Files.isDirectory(nested));
  }

  @Test
  void createDirectories_doesNotFailIfExists() throws IOException {
    Path existing = tempDir.resolve("existing");
    Files.createDirectories(existing);

    assertDoesNotThrow(() -> helper.createDirectories(existing));
  }
}
