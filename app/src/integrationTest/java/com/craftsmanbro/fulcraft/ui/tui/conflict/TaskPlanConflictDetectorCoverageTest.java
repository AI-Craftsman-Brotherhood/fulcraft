package com.craftsmanbro.fulcraft.ui.tui.conflict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class TaskPlanConflictDetectorCoverageTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("detectConflicts counts unique targets and ignores missing files")
  void detectConflicts_countsUniqueTargetsAndIgnoresMissingFiles() throws Exception {
    Path tasksFile = tempDir.resolve("tasks.jsonl");
    writeTasks(
        tasksFile,
        List.of(
            taskJson("com.example.FooService", "doWork", true, null, null),
            taskJson("com.example.MissingService", "notCreated", true, null, null),
            taskJson("com.example.BarService", "a", true, "SharedSpec", null),
            taskJson("com.example.BazService", "b", true, "SharedSpec", null)));

    Path fooTarget = expectedTestFile(tempDir, "com.example", "FooService_doWorkGeneratedTest");
    Path sharedTarget = expectedTestFile(tempDir, "com.example", "SharedSpec");
    createTestFile(fooTarget);
    createTestFile(sharedTarget);

    TaskPlanConflictDetector detector = new TaskPlanConflictDetector(tempDir);
    List<ConflictCandidate> conflicts = detector.detectConflicts();

    assertEquals(3, detector.getTotalFileCount());
    assertEquals(2, conflicts.size());

    Set<String> names =
        conflicts.stream().map(ConflictCandidate::fileName).collect(Collectors.toSet());
    assertEquals(Set.of("FooService_doWorkGeneratedTest.java", "SharedSpec.java"), names);
  }

  @Test
  @DisplayName("Private derivation helpers cover package and class-name branches")
  void privateHelpers_coverDerivationBranches() throws Exception {
    TaskPlanConflictDetector detector = new TaskPlanConflictDetector(tempDir);

    TaskRecord taskWithoutDot = task("OnlyClass", "run", true, null);
    taskWithoutDot.setFilePath("com/example/path/Foo.java");
    assertEquals(
        "com.example.path",
        invokeString(detector, "derivePackageName", TaskRecord.class, taskWithoutDot));

    TaskRecord taskWithUppercasePackage = task("Com.Example.Service", "run", true, null);
    assertEquals(
        "Com.Example",
        invokeString(detector, "derivePackageName", TaskRecord.class, taskWithUppercasePackage));

    TaskRecord taskWithNullClassFqn = task(null, "run", true, null);
    taskWithNullClassFqn.setFilePath("fallback/pkg/Foo.java");
    assertEquals(
        "fallback.pkg",
        invokeString(detector, "derivePackageName", TaskRecord.class, taskWithNullClassFqn));

    TaskRecord taskWithEmptyPackageSegment = task("com..Service", "run", true, null);
    assertEquals(
        "com.",
        invokeString(detector, "derivePackageName", TaskRecord.class, taskWithEmptyPackageSegment));

    assertEquals("", invokeString(detector, "derivePackageName", TaskRecord.class, (Object) null));

    assertEquals(
        "", invokeString(detector, "derivePackageNameFromPath", String.class, (Object) null));
    assertEquals(
        "com.example",
        invokeString(detector, "derivePackageNameFromPath", String.class, "com/example/Foo.java"));
    assertEquals(
        "com.example",
        invokeString(detector, "derivePackageNameFromPath", String.class, "com/example/Foo.kt"));
    assertEquals(
        "", invokeString(detector, "derivePackageNameFromPath", String.class, "NoSlashName"));

    TaskRecord noClassFqn = task(null, "run", true, null);
    assertEquals(
        "",
        invokeString(
            detector,
            "deriveClassNameBase",
            TaskRecord.class,
            String.class,
            noClassFqn,
            "com.example"));
    assertEquals(
        "",
        invokeString(
            detector, "deriveClassNameBase", TaskRecord.class, String.class, null, "com.example"));

    TaskRecord classFqnTask = task("com.example.Deep.Service", "run", true, null);
    assertEquals(
        "com.example.Deep.Service",
        invokeString(
            detector, "deriveClassNameBase", TaskRecord.class, String.class, classFqnTask, ""));
    assertEquals(
        "com.example.Deep.Service",
        invokeString(
            detector, "deriveClassNameBase", TaskRecord.class, String.class, classFqnTask, null));
    assertEquals(
        "Deep.Service",
        invokeString(
            detector,
            "deriveClassNameBase",
            TaskRecord.class,
            String.class,
            classFqnTask,
            "com.example"));
    assertEquals(
        "com.example.Deep.Service",
        invokeString(
            detector,
            "deriveClassNameBase",
            TaskRecord.class,
            String.class,
            classFqnTask,
            "com.other"));

    TaskRecord explicitNameTask = task("com.example.Service", "work", true, "CustomTestName");
    assertEquals(
        "CustomTestName",
        invokeString(
            detector,
            "determineBaseTestClassName",
            TaskRecord.class,
            String.class,
            explicitNameTask,
            "Service"));

    TaskRecord unknownMethodTask = task("com.example.Service", null, true, " ");
    assertEquals(
        "Service_UnknownMethodGeneratedTest",
        invokeString(
            detector,
            "determineBaseTestClassName",
            TaskRecord.class,
            String.class,
            unknownMethodTask,
            "Service"));

    TaskRecord sanitizeTask = task("com.example.Service", "do-work", true, "");
    assertEquals(
        "Outer_Inner_do_workGeneratedTest",
        invokeString(
            detector,
            "determineBaseTestClassName",
            TaskRecord.class,
            String.class,
            sanitizeTask,
            "Outer.Inner"));

    assertEquals(
        "",
        invokeString(
            detector,
            "determineBaseTestClassName",
            TaskRecord.class,
            String.class,
            null,
            "ClassName"));
    assertEquals(
        "",
        invokeString(
            detector,
            "determineBaseTestClassName",
            TaskRecord.class,
            String.class,
            sanitizeTask,
            null));
  }

  @Test
  @DisplayName("Private path helpers cover fallback and selection branches")
  void privatePathHelpers_coverFallbackBranches() throws Exception {
    TaskPlanConflictDetector detector = new TaskPlanConflictDetector(tempDir);

    assertNull(invokePath(detector, "resolveTargetPath", TaskRecord.class, (Object) null));

    Path missing = tempDir.resolve("missing/File.java");
    Path resolvedExisting = invokePath(detector, "resolveExistingPath", Path.class, missing);
    assertEquals(missing.toAbsolutePath().normalize(), resolvedExisting);

    assertNull(
        invokeObject(
            detector, "resolveLastModifiedTime", Path.class, tempDir.resolve("missing-dir")));

    Path runsRoot = tempDir.resolve(".ful/runs");
    Path firstRun = runsRoot.resolve("run-1");
    Path secondRun = runsRoot.resolve("run-2");
    Files.createDirectories(firstRun);
    Files.createDirectories(secondRun);
    Files.writeString(runsRoot.resolve("README.txt"), "not a directory");

    Files.setLastModifiedTime(firstRun, FileTime.fromMillis(1_000));
    Files.setLastModifiedTime(secondRun, FileTime.fromMillis(2_000));

    Path latestRun = invokePath(detector, "findLatestRunDir", Path.class, runsRoot);
    assertEquals(secondRun, latestRun);

    Path nonDirectory = tempDir.resolve("not-a-directory.txt");
    Files.writeString(nonDirectory, "x");
    assertNull(invokePath(detector, "findLatestRunDir", Path.class, nonDirectory));
  }

  private static Map<String, Object> taskJson(
      String classFqn, String methodName, boolean selected, String testClassName, String filePath) {
    Map<String, Object> task = new LinkedHashMap<>();
    task.put("class_fqn", classFqn);
    task.put("method_name", methodName);
    task.put("selected", selected);
    if (testClassName != null) {
      task.put("test_class_name", testClassName);
    }
    if (filePath != null) {
      task.put("file_path", filePath);
    }
    return task;
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

  private static void writeTasks(Path tasksFile, List<Map<String, Object>> tasks)
      throws IOException {
    Files.createDirectories(tasksFile.getParent());
    ObjectMapper mapper = JsonMapperFactory.create();
    StringBuilder content = new StringBuilder();
    for (Map<String, Object> task : tasks) {
      content.append(mapper.writeValueAsString(task)).append(System.lineSeparator());
    }
    Files.writeString(tasksFile, content.toString());
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

  private static Object invokeObject(Object target, String methodName, Class<?> type, Object arg)
      throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName, type);
    method.setAccessible(true);
    return method.invoke(target, arg);
  }

  private static String invokeString(Object target, String methodName, Class<?> type, Object arg)
      throws Exception {
    return (String) invokeObject(target, methodName, type, arg);
  }

  private static String invokeString(
      Object target, String methodName, Class<?> type1, Class<?> type2, Object arg1, Object arg2)
      throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName, type1, type2);
    method.setAccessible(true);
    return (String) method.invoke(target, arg1, arg2);
  }

  private static Path invokePath(Object target, String methodName, Class<?> type, Object arg)
      throws Exception {
    return (Path) invokeObject(target, methodName, type, arg);
  }
}
