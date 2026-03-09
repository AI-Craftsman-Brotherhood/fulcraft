package com.craftsmanbro.fulcraft.ui.tui.conflict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.io.impl.JsonlTasksFileFormat;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link TaskPlanConflictDetector}. */
class TaskPlanConflictDetectorTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("Returns empty when no tasks file is present")
  void detectConflicts_noTasksFile_returnsEmpty() {
    TaskPlanConflictDetector detector = new TaskPlanConflictDetector(tempDir);

    List<ConflictCandidate> conflicts = detector.detectConflicts();

    assertTrue(conflicts.isEmpty());
    assertEquals(0, detector.getTotalFileCount());
  }

  @Test
  @DisplayName("Uses project root tasks file and counts unique targets")
  void detectConflicts_usesProjectTasksFileAndCountsUniqueTargets() throws Exception {
    Path tasksFile = tempDir.resolve("tasks.jsonl");

    TaskRecord task1 = task("com.example.FooService", "do-work", true, null);
    TaskRecord task2 = task("com.example.BarService", "run", true, "SharedTest");
    TaskRecord task3 = task("com.example.BazService", "other", true, "SharedTest");
    TaskRecord task4 = task("com.example.SkipService", "skip", false, null);

    writeTasks(tasksFile, List.of(task1, task2, task3, task4));

    Path fooTarget = expectedTestFile(tempDir, "com.example", "FooService_do_workGeneratedTest");
    Path sharedTarget = expectedTestFile(tempDir, "com.example", "SharedTest");
    Path skippedTarget = expectedTestFile(tempDir, "com.example", "SkipService_skipGeneratedTest");

    createTestFile(fooTarget);
    createTestFile(sharedTarget);
    createTestFile(skippedTarget);

    TaskPlanConflictDetector detector = new TaskPlanConflictDetector(tempDir);
    List<ConflictCandidate> conflicts = detector.detectConflicts();

    assertEquals(2, detector.getTotalFileCount());
    assertEquals(2, conflicts.size());

    Set<String> fileNames =
        conflicts.stream().map(ConflictCandidate::fileName).collect(Collectors.toSet());
    assertEquals(Set.of("FooService_do_workGeneratedTest.java", "SharedTest.java"), fileNames);
  }

  @Test
  @DisplayName("Falls back to latest run plan when project tasks file is missing")
  void detectConflicts_fallsBackToLatestRunPlan() throws Exception {
    Path run1Plan = tempDir.resolve(".ful/runs/run-1/plan");
    Path run2Plan = tempDir.resolve(".ful/runs/run-2/plan");
    Files.createDirectories(run1Plan);
    Files.createDirectories(run2Plan);

    writeTasks(
        run1Plan.resolve("tasks.jsonl"),
        List.of(task("com.example.OldService", "oldMethod", true, null)));
    writeTasks(
        run2Plan.resolve("tasks.jsonl"),
        List.of(task("com.example.NewService", "newMethod", true, null)));

    Files.setLastModifiedTime(run1Plan.getParent(), FileTime.fromMillis(1_000));
    Files.setLastModifiedTime(run2Plan.getParent(), FileTime.fromMillis(2_000));

    Path newTarget = expectedTestFile(tempDir, "com.example", "NewService_newMethodGeneratedTest");
    createTestFile(newTarget);

    TaskPlanConflictDetector detector = new TaskPlanConflictDetector(tempDir);
    List<ConflictCandidate> conflicts = detector.detectConflicts();

    assertEquals(1, detector.getTotalFileCount());
    assertEquals(1, conflicts.size());
    assertEquals(newTarget.toAbsolutePath().normalize(), conflicts.get(0).targetPath());
  }

  private static TaskRecord task(
      String classFqn, String methodName, boolean selected, String testClassName) {
    TaskRecord task = new TaskRecord();
    task.setClassFqn(classFqn);
    task.setMethodName(methodName);
    task.setSelected(selected);
    task.setTestClassName(testClassName);
    return task;
  }

  private static void writeTasks(Path tasksFile, List<TaskRecord> tasks) throws IOException {
    Files.createDirectories(tasksFile.getParent());
    JsonlTasksFileFormat format = new JsonlTasksFileFormat(JsonMapperFactory.create());
    format.write(tasks, List.of(), tasksFile);
  }

  private static Path expectedTestFile(Path projectRoot, String packageName, String testClassName) {
    Path packagePath = projectRoot.resolve("src/test/java");
    if (packageName != null && !packageName.isBlank()) {
      packagePath = packagePath.resolve(packageName.replace('.', '/'));
    }
    return packagePath.resolve(testClassName + ".java").toAbsolutePath().normalize();
  }

  private static void createTestFile(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, "class Dummy {}");
  }
}
