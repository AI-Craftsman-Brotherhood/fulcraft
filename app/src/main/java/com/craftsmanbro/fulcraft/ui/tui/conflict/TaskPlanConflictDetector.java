package com.craftsmanbro.fulcraft.ui.tui.conflict;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.contract.SourceFileManagerPort;
import com.craftsmanbro.fulcraft.infrastructure.fs.impl.SourceFileManager;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.io.impl.TasksFileFormatFactory;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.RunDirectories;
import com.craftsmanbro.fulcraft.plugins.reporting.io.TasksFileLoader;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.ui.tui.UiLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

final class TaskPlanConflictDetector implements ConflictDetector {

  private final Path projectRoot;

  private final TasksFileLoader tasksFileLoader;

  private final TasksFileFormatFactory tasksFileFormatFactory;

  private final SourceFileManagerPort sourceFileManager;

  private int totalFileCount;

  TaskPlanConflictDetector(final Path projectRoot) {
    this.projectRoot =
        Objects.requireNonNull(projectRoot, "projectRoot must not be null")
            .toAbsolutePath()
            .normalize();
    this.tasksFileLoader = new TasksFileLoader();
    this.tasksFileFormatFactory = new TasksFileFormatFactory(JsonMapperFactory.create());
    this.sourceFileManager = new SourceFileManager();
    this.totalFileCount = 0;
  }

  @Override
  public List<ConflictCandidate> detectConflicts() {
    final List<TaskRecord> tasks = loadTasks();
    final List<TaskRecord> selectedTasks =
        tasks.stream().filter(taskRecord -> Boolean.TRUE.equals(taskRecord.getSelected())).toList();
    if (selectedTasks.isEmpty()) {
      totalFileCount = 0;
      return List.of();
    }
    final List<ConflictCandidate> existingFileConflicts = new ArrayList<>();
    final Set<Path> uniqueTargetPaths = new HashSet<>();
    for (final TaskRecord selectedTask : selectedTasks) {
      final Path targetTestFile = resolveTargetPath(selectedTask);
      if (targetTestFile == null) {
        continue;
      }
      if (!uniqueTargetPaths.add(targetTestFile)) {
        continue;
      }
      if (!Files.isRegularFile(targetTestFile)) {
        continue;
      }
      existingFileConflicts.add(
          ConflictCandidate.of(targetTestFile, resolveExistingPath(targetTestFile)));
    }
    totalFileCount = uniqueTargetPaths.size();
    return List.copyOf(existingFileConflicts);
  }

  @Override
  public int getTotalFileCount() {
    return totalFileCount;
  }

  private List<TaskRecord> loadTasks() {
    final Path resolvedTasksFile = resolveTasksFile();
    if (resolvedTasksFile == null) {
      return List.of();
    }
    try {
      return tasksFileLoader.loadTasks(resolvedTasksFile);
    } catch (IOException e) {
      UiLogger.warn(
          MessageSource.getMessage("tui.conflict.detector.warn.load_tasks_failed", e.getMessage()));
      return List.of();
    }
  }

  private Path resolveTasksFile() {
    final Path projectTasksFile = tasksFileFormatFactory.resolveExistingTasksFile(projectRoot);
    if (projectTasksFile != null) {
      return projectTasksFile;
    }
    final Path runsRoot = RunDirectories.resolveRunsRoot(null, projectRoot);
    final Path latestRunDirectory = findLatestRunDir(runsRoot);
    if (latestRunDirectory == null) {
      return null;
    }
    final Path planDirectory = latestRunDirectory.resolve(RunPaths.PLAN_DIR);
    return tasksFileFormatFactory.resolveExistingTasksFile(planDirectory);
  }

  private Path findLatestRunDir(final Path runsRoot) {
    if (!Files.isDirectory(runsRoot)) {
      return null;
    }
    Path latestRunDirectory = null;
    FileTime latestModifiedTime = null;
    try (Stream<Path> stream = Files.list(runsRoot)) {
      for (final Path candidateRunDirectory : (Iterable<Path>) stream::iterator) {
        if (!Files.isDirectory(candidateRunDirectory)) {
          continue;
        }
        final FileTime candidateModifiedTime = resolveLastModifiedTime(candidateRunDirectory);
        if (candidateModifiedTime == null) {
          continue;
        }
        if (latestModifiedTime == null || candidateModifiedTime.compareTo(latestModifiedTime) > 0) {
          latestRunDirectory = candidateRunDirectory;
          latestModifiedTime = candidateModifiedTime;
        }
      }
    } catch (IOException e) {
      UiLogger.warn(
          MessageSource.getMessage("tui.conflict.detector.warn.scan_runs_failed", e.getMessage()));
      return null;
    }
    return latestRunDirectory;
  }

  private FileTime resolveLastModifiedTime(final Path candidatePath) {
    try {
      return Files.getLastModifiedTime(candidatePath);
    } catch (IOException e) {
      return null;
    }
  }

  private Path resolveTargetPath(final TaskRecord task) {
    if (task == null) {
      return null;
    }
    final String packageName = derivePackageName(task);
    final String classNameBase = deriveClassNameBase(task, packageName);
    final String testClassName = determineBaseTestClassName(task, classNameBase);
    if (testClassName.isBlank()) {
      return null;
    }
    final Path plannedTestFile =
        sourceFileManager.planTestFile(projectRoot, packageName, testClassName).testFile();
    return plannedTestFile.toAbsolutePath().normalize();
  }

  private Path resolveExistingPath(final Path targetPath) {
    try {
      return targetPath.toRealPath();
    } catch (IOException e) {
      return targetPath.toAbsolutePath().normalize();
    }
  }

  private static final String JAVA_FILE_EXTENSION = ".java";

  private static final String GENERATED_TEST_SUFFIX = "GeneratedTest";

  private static final String NON_ALPHANUMERIC_METHOD_NAME_REGEX = "[^A-Za-z0-9]";

  private String derivePackageName(final TaskRecord task) {
    if (task == null) {
      return "";
    }
    final String classFqn = task.getClassFqn();
    if (classFqn == null || !classFqn.contains(".")) {
      return derivePackageNameFromPath(task.getFilePath());
    }
    return derivePackageNameFromClassFqn(classFqn);
  }

  private String derivePackageNameFromClassFqn(final String classFqn) {
    final int lastDotIndex = classFqn.lastIndexOf('.');
    final String[] segments = classFqn.split("\\.");
    final StringBuilder packageNameBuilder = new StringBuilder();
    for (int i = 0; i < segments.length - 1; i++) {
      final String segment = segments[i];
      // Stop once segments look like type names instead of package names.
      if (!segment.isEmpty() && Character.isUpperCase(segment.charAt(0))) {
        break;
      }
      if (!packageNameBuilder.isEmpty()) {
        packageNameBuilder.append(".");
      }
      packageNameBuilder.append(segment);
    }
    if (!packageNameBuilder.isEmpty()) {
      return packageNameBuilder.toString();
    }
    return classFqn.substring(0, lastDotIndex);
  }

  private String derivePackageNameFromPath(final String relativePath) {
    if (relativePath == null) {
      return "";
    }
    String relativePathWithoutExtension = relativePath;
    if (relativePathWithoutExtension.endsWith(JAVA_FILE_EXTENSION)) {
      relativePathWithoutExtension =
          relativePathWithoutExtension.substring(
              0, relativePathWithoutExtension.length() - JAVA_FILE_EXTENSION.length());
    }
    final int lastSlashIndex = relativePathWithoutExtension.lastIndexOf('/');
    if (lastSlashIndex >= 0) {
      return relativePathWithoutExtension.substring(0, lastSlashIndex).replace('/', '.');
    }
    return "";
  }

  private String deriveClassNameBase(final TaskRecord task, final String packageName) {
    if (task == null) {
      return "";
    }
    final String classFqn = task.getClassFqn();
    if (classFqn == null) {
      return "";
    }
    if (packageName == null || packageName.isEmpty()) {
      return classFqn;
    }
    if (classFqn.startsWith(packageName + ".")) {
      return classFqn.substring(packageName.length() + 1);
    }
    return classFqn;
  }

  private String determineBaseTestClassName(final TaskRecord task, final String classNameBase) {
    if (task == null || classNameBase == null) {
      return "";
    }
    final String explicitTestClassName = task.getTestClassName();
    if (explicitTestClassName != null && !explicitTestClassName.isBlank()) {
      return explicitTestClassName;
    }
    // Keep synthesized names stable so repeat scans resolve the same target file.
    final String sanitizedMethodName =
        task.getMethodName() != null
            ? task.getMethodName().replaceAll(NON_ALPHANUMERIC_METHOD_NAME_REGEX, "_")
            : "UnknownMethod";
    final String sanitizedClassName = classNameBase.replace('.', '_');
    return sanitizedClassName + "_" + sanitizedMethodName + GENERATED_TEST_SUFFIX;
  }
}
