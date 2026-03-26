package com.craftsmanbro.fulcraft.infrastructure.fs.impl;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.contract.SourceFileManagerPort;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.TestFilePlan;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages file system operations for source and test files.
 *
 * <p>Handles resolution of source paths, planning for test file creation, and writing content to
 * files with appropriate directory handling.
 */
public class SourceFileManager implements SourceFileManagerPort {

  private static final String JAVA_EXTENSION = ".java";
  private static final String PROJECT_ROOT_MUST_NOT_BE_NULL = "projectRoot must not be null";
  private static final String PACKAGE_NAME = "packageName";
  private static final String BASE_TEST_CLASS_NAME = "baseTestClassName";
  private static final String TEST_CLASS_NAME = "testClassName";
  private static final String STANDARD_SOURCE_ROOT = "src/main/java";
  private static final String APP_SOURCE_ROOT = "app/src/main/java";
  private static final String STANDARD_TEST_ROOT = "src/test/java";
  private static final String APP_TEST_ROOT = "app/src/test/java";
  private static final List<String> SOURCE_PATH_CANDIDATES =
      List.of("", STANDARD_SOURCE_ROOT, "src", APP_SOURCE_ROOT);

  /**
   * Resolves the absolute path to the source file defined in the task.
   *
   * @param projectRoot The root directory of the project.
   * @param task The task record containing the file path.
   * @return The absolute path to the existing source file.
   * @throws IOException If the source file cannot be found in any standard location.
   */
  public Path resolveSourcePathOrThrow(final Path projectRoot, final TaskRecord task)
      throws IOException {
    Objects.requireNonNull(projectRoot, PROJECT_ROOT_MUST_NOT_BE_NULL);
    Objects.requireNonNull(
        task,
        MessageSource.getMessage("infra.common.error.argument_null", "task must not be null"));
    Objects.requireNonNull(
        task.getFilePath(),
        MessageSource.getMessage(
            "infra.common.error.argument_null", "task.filePath must not be null"));
    Objects.requireNonNull(
        task.getTaskId(),
        MessageSource.getMessage(
            "infra.common.error.argument_null", "task.taskId must not be null"));
    return resolveSourcePathOrThrowInternal(projectRoot, task.getFilePath(), task.getTaskId());
  }

  /**
   * Resolves the absolute path to the source file defined by filePath.
   *
   * @param projectRoot The root directory of the project.
   * @param filePath The file path relative to the project root or standard source roots.
   * @param taskId The task identifier used in error reporting.
   * @return The absolute path to the existing source file.
   * @throws IOException If the source file cannot be found in any standard location.
   */
  @Override
  public Path resolveSourcePathOrThrow(
      final Path projectRoot, final String filePath, final String taskId) throws IOException {
    Objects.requireNonNull(projectRoot, PROJECT_ROOT_MUST_NOT_BE_NULL);
    Objects.requireNonNull(
        filePath,
        MessageSource.getMessage("infra.common.error.argument_null", "filePath must not be null"));
    Objects.requireNonNull(
        taskId,
        MessageSource.getMessage("infra.common.error.argument_null", "taskId must not be null"));
    return resolveSourcePathOrThrowInternal(projectRoot, filePath, taskId);
  }

  private Path resolveSourcePathOrThrowInternal(
      final Path projectRoot, final String filePath, final String taskId) throws IOException {
    final var normalizedProjectRoot = normalizeProjectRoot(projectRoot);
    final var srcPath = resolveSourcePath(normalizedProjectRoot, filePath);
    if (srcPath.isPresent()) {
      return srcPath.get();
    }
    final var triedPaths =
        SOURCE_PATH_CANDIDATES.stream()
            .map(base -> resolveCandidatePath(normalizedProjectRoot, base, filePath))
            .map(Path::toAbsolutePath)
            .map(Path::toString)
            .collect(Collectors.joining("\n                - "));
    throw new IOException(
        """
            Source file not found for task %s
              file_path: %s
              Tried paths:
                - %s
              Please verify the file exists and file_path in analysis.json is correct."""
            .formatted(taskId, filePath, triedPaths));
  }

  private Optional<Path> resolveSourcePath(final Path projectRoot, final String filePath)
      throws IOException {
    for (final var base : SOURCE_PATH_CANDIDATES) {
      final var candidate = resolveCandidatePath(projectRoot, base, filePath);
      if (!candidate.startsWith(projectRoot)) {
        continue;
      }
      if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
        final var realCandidate = candidate.toRealPath();
        if (realCandidate.startsWith(projectRoot)) {
          return Optional.of(realCandidate);
        }
      }
    }
    return Optional.empty();
  }

  private Path resolveCandidatePath(
      final Path projectRoot, final String base, final String filePath) {
    if (base.isEmpty()) {
      return projectRoot.resolve(filePath).normalize();
    }
    return projectRoot.resolve(base).resolve(filePath).normalize();
  }

  private Path normalizeProjectRoot(final Path projectRoot) throws IOException {
    final var normalized = projectRoot.toAbsolutePath().normalize();
    if (Files.exists(normalized)) {
      return normalized.toRealPath();
    }
    return normalized;
  }

  /**
   * Plans the path and class name for the test file to be generated.
   *
   * <p>Does NOT create any directories or files.
   *
   * @param projectRoot The root directory of the project.
   * @param packageName The package name for the test.
   * @param baseTestClassName The preferred base name for the test class.
   * @return A {@link TestFilePlan} containing the resolved path and class name.
   */
  @Override
  public TestFilePlan planTestFile(
      final Path projectRoot, final String packageName, final String baseTestClassName) {
    Objects.requireNonNull(projectRoot, PROJECT_ROOT_MUST_NOT_BE_NULL);
    Objects.requireNonNull(
        packageName,
        MessageSource.getMessage(
            "infra.common.error.argument_null", "packageName must not be null"));
    Objects.requireNonNull(
        baseTestClassName,
        MessageSource.getMessage(
            "infra.common.error.argument_null", "baseTestClassName must not be null"));
    final var testSrcRoot = resolvePreferredTestSourceRoot(projectRoot);
    final var packagePath = resolvePackagePath(testSrcRoot, packageName);
    final var testClassName = baseTestClassName;
    final var testFile =
        resolveFilePath(packagePath, testClassName + JAVA_EXTENSION, BASE_TEST_CLASS_NAME);
    return new TestFilePlan(testFile, testClassName);
  }

  /**
   * Saves logic for a failed test generation for inspection.
   *
   * @param projectRoot The root directory of the project.
   * @param packageName The package name.
   * @param testClassName The test class name.
   * @param code The code content.
   * @throws IOException If writing fails.
   */
  @Override
  public void saveFailedTest(
      final Path projectRoot,
      final String packageName,
      final String testClassName,
      final String code)
      throws IOException {
    Objects.requireNonNull(projectRoot, PROJECT_ROOT_MUST_NOT_BE_NULL);
    Objects.requireNonNull(
        packageName,
        MessageSource.getMessage(
            "infra.common.error.argument_null", "packageName must not be null"));
    Objects.requireNonNull(
        testClassName,
        MessageSource.getMessage(
            "infra.common.error.argument_null", "testClassName must not be null"));
    Objects.requireNonNull(
        code,
        MessageSource.getMessage("infra.common.error.argument_null", "code must not be null"));
    final var failedRoot = projectRoot.resolve("build").resolve("failed_tests");
    final var failedPath = resolvePackagePath(failedRoot, packageName);
    final var targetFile =
        resolveFilePath(failedPath, testClassName + JAVA_EXTENSION, TEST_CLASS_NAME);
    // writeString handles directory creation
    writeString(targetFile, code);
    Logger.stdout("📁 Saved failed test for inspection: " + targetFile);
  }

  /**
   * Reads content from the specified path.
   *
   * @param path The path to read from.
   * @return The string content of the file.
   * @throws IOException If an I/O error occurs.
   */
  @Override
  public String readString(final Path path) throws IOException {
    Objects.requireNonNull(
        path,
        MessageSource.getMessage("infra.common.error.argument_null", "path must not be null"));
    return Files.readString(path);
  }

  /**
   * Writes content to the specified path, creating parent directories if necessary.
   *
   * @param path The destination path.
   * @param content The string content to write.
   * @throws IOException If an I/O error occurs.
   */
  @Override
  public void writeString(final Path path, final String content) throws IOException {
    Objects.requireNonNull(
        path,
        MessageSource.getMessage("infra.common.error.argument_null", "path must not be null"));
    Objects.requireNonNull(
        content,
        MessageSource.getMessage("infra.common.error.argument_null", "content must not be null"));
    final var parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(path, content);
  }

  private Path resolvePreferredTestSourceRoot(final Path projectRoot) {
    final var standardTestRoot = projectRoot.resolve(STANDARD_TEST_ROOT);
    if (Files.isDirectory(standardTestRoot)) {
      return standardTestRoot;
    }

    final var appTestRoot = projectRoot.resolve(APP_TEST_ROOT);
    if (Files.isDirectory(appTestRoot)) {
      return appTestRoot;
    }

    final var standardSourceRoot = projectRoot.resolve(STANDARD_SOURCE_ROOT);
    final var appSourceRoot = projectRoot.resolve(APP_SOURCE_ROOT);
    if (!Files.isDirectory(standardSourceRoot) && Files.isDirectory(appSourceRoot)) {
      return appTestRoot;
    }
    return standardTestRoot;
  }

  private Path resolvePackagePath(final Path rootDirectory, final String packageName) {
    validateRelativeArgument(packageName, PACKAGE_NAME);
    return resolvePath(
        rootDirectory, packageName.isEmpty() ? "" : packageName.replace('.', '/'), PACKAGE_NAME);
  }

  private Path resolveFilePath(
      final Path parentDirectory, final String fileName, final String argumentName) {
    validateRelativeArgument(fileName, argumentName);
    return resolvePath(parentDirectory, fileName, argumentName);
  }

  private Path resolvePath(
      final Path parentDirectory, final String relativePath, final String argumentName) {
    final var candidate =
        relativePath.isEmpty()
            ? parentDirectory.normalize()
            : parentDirectory.resolve(relativePath).normalize();
    final var normalizedParent = parentDirectory.toAbsolutePath().normalize();
    final var normalizedCandidate = candidate.toAbsolutePath().normalize();
    if (!normalizedCandidate.startsWith(normalizedParent)) {
      throw invalidPathArgument(argumentName, "must resolve inside " + normalizedParent);
    }
    return candidate;
  }

  private void validateRelativeArgument(final String value, final String argumentName) {
    if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
      throw invalidPathArgument(argumentName, "must not contain path separators");
    }
  }

  private IllegalArgumentException invalidPathArgument(
      final String argumentName, final String details) {
    return new IllegalArgumentException(
        MessageSource.getMessage("infra.common.error.message", argumentName + " " + details));
  }
}
