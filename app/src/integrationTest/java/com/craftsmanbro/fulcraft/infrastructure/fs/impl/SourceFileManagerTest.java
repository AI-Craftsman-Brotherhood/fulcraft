package com.craftsmanbro.fulcraft.infrastructure.fs.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.fs.model.TestFilePlan;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFileManagerTest {

  @TempDir Path tempDir;

  @Test
  void resolvesSourceFileUnderStandardRoots() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path sourceFile = projectRoot.resolve("src/main/java/com/example/Foo.java");
    Files.createDirectories(sourceFile.getParent());
    Files.writeString(sourceFile, "class Foo {}");

    TaskRecord task = new TaskRecord();
    task.setTaskId("task-1");
    task.setFilePath("com/example/Foo.java");

    SourceFileManager manager = new SourceFileManager();
    Path resolved = manager.resolveSourcePathOrThrow(projectRoot, task);

    assertTrue(resolved.endsWith("com/example/Foo.java"));
  }

  @Test
  void resolvesSourceFileFromProjectRoot() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path sourceFile = projectRoot.resolve("RootLevel.java");
    Files.createDirectories(projectRoot);
    Files.writeString(sourceFile, "class RootLevel {}");

    SourceFileManager manager = new SourceFileManager();
    Path resolved = manager.resolveSourcePathOrThrow(projectRoot, "RootLevel.java", "task-root");

    assertEquals(sourceFile.toRealPath(), resolved);
  }

  @Test
  void rejectsAbsolutePathOutsideProject() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path outsideFile = tempDir.resolve("outside/Outside.java");
    Files.createDirectories(outsideFile.getParent());
    Files.writeString(outsideFile, "class Outside {}");

    TaskRecord task = new TaskRecord();
    task.setTaskId("task-2");
    task.setFilePath(outsideFile.toString());

    SourceFileManager manager = new SourceFileManager();
    assertThrows(IOException.class, () -> manager.resolveSourcePathOrThrow(projectRoot, task));
  }

  @Test
  void rejectsDirectoryCandidates() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path directoryCandidate = projectRoot.resolve("src/main/java/com/example/Foo");
    Files.createDirectories(directoryCandidate);

    TaskRecord task = new TaskRecord();
    task.setTaskId("task-3");
    task.setFilePath("com/example/Foo");

    SourceFileManager manager = new SourceFileManager();
    assertThrows(IOException.class, () -> manager.resolveSourcePathOrThrow(projectRoot, task));
  }

  @Test
  void throwsWhenSourceFileNotFound() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Files.createDirectories(projectRoot);

    SourceFileManager manager = new SourceFileManager();
    IOException ex =
        assertThrows(
            IOException.class,
            () -> manager.resolveSourcePathOrThrow(projectRoot, "missing/Foo.java", "task-404"));

    assertTrue(ex.getMessage().contains("task-404"));
    assertTrue(ex.getMessage().contains("missing/Foo.java"));
    assertTrue(ex.getMessage().contains("Tried paths"));
  }

  @Test
  void plansTestFilePathWithoutCreatingDirectories() {
    Path projectRoot = tempDir.resolve("project");
    SourceFileManager manager = new SourceFileManager();

    TestFilePlan plan = manager.planTestFile(projectRoot, "com.example.foo", "FooTest");

    Path expectedPath = projectRoot.resolve("src/test/java/com/example/foo/FooTest.java");
    assertEquals(expectedPath, plan.testFile());
    assertEquals("FooTest", plan.testClassName());
    assertFalse(Files.exists(expectedPath));
  }

  @Test
  void plansTestFileUnderAppTestRootWhenProjectUsesAppLayout() throws IOException {
    Path projectRoot = tempDir.resolve("android-project");
    Files.createDirectories(projectRoot.resolve("app/src/main/java/com/example"));
    SourceFileManager manager = new SourceFileManager();

    TestFilePlan plan = manager.planTestFile(projectRoot, "com.example.foo", "FooTest");

    Path expectedPath = projectRoot.resolve("app/src/test/java/com/example/foo/FooTest.java");
    assertEquals(expectedPath.toAbsolutePath().normalize(), plan.testFile());
    assertEquals("FooTest", plan.testClassName());
  }

  @Test
  void planTestFileRejectsPathSeparatorsInArguments() {
    SourceFileManager manager = new SourceFileManager();

    IllegalArgumentException packageException =
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.planTestFile(tempDir, "../escape", "FooTest"));
    assertTrue(packageException.getMessage().contains("packageName must not contain path separators"));

    IllegalArgumentException classException =
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.planTestFile(tempDir, "com.example", "../FooTest"));
    assertTrue(
        classException.getMessage().contains("baseTestClassName must not contain path separators"));
  }

  @Test
  void savesFailedTestUnderBuildDirectory() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Files.createDirectories(projectRoot);

    SourceFileManager manager = new SourceFileManager();
    manager.saveFailedTest(projectRoot, "com.example", "FooTest", "class FooTest {}");

    Path expected =
        projectRoot.resolve("build/failed_tests/com/example/FooTest.java").toAbsolutePath();
    assertTrue(Files.exists(expected));
    assertEquals("class FooTest {}", Files.readString(expected));
  }

  @Test
  void saveFailedTestRejectsPathSeparatorsInArguments() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Files.createDirectories(projectRoot);
    SourceFileManager manager = new SourceFileManager();

    IllegalArgumentException packageException =
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.saveFailedTest(projectRoot, "../escape", "FooTest", "class FooTest {}"));
    assertTrue(packageException.getMessage().contains("packageName must not contain path separators"));

    IllegalArgumentException classException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                manager.saveFailedTest(
                    projectRoot, "com.example", "../FooTest", "class FooTest {}"));
    assertTrue(classException.getMessage().contains("testClassName must not contain path separators"));
  }

  @Test
  void writeAndReadStringRoundTrip() throws IOException {
    SourceFileManager manager = new SourceFileManager();
    Path target = tempDir.resolve("nested/dir/file.txt");
    String content = "hello";

    manager.writeString(target, content);

    assertTrue(Files.exists(target));
    assertEquals(content, manager.readString(target));
  }

  @Test
  void readAndWriteValidateNullArguments() {
    SourceFileManager manager = new SourceFileManager();

    assertThrows(NullPointerException.class, () -> manager.readString(null));
    assertThrows(NullPointerException.class, () -> manager.writeString(null, "content"));
    assertThrows(NullPointerException.class, () -> manager.writeString(tempDir.resolve("x"), null));
  }

  @Test
  void resolveSourcePathValidatesNullArguments() {
    SourceFileManager manager = new SourceFileManager();

    assertThrows(
        NullPointerException.class, () -> manager.resolveSourcePathOrThrow(null, "x", "t"));
    assertThrows(
        NullPointerException.class, () -> manager.resolveSourcePathOrThrow(tempDir, null, "t"));
    assertThrows(
        NullPointerException.class, () -> manager.resolveSourcePathOrThrow(tempDir, "x", null));

    assertThrows(
        NullPointerException.class,
        () -> manager.resolveSourcePathOrThrow(tempDir, (TaskRecord) null));
  }
}
