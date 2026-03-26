package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/**
 * Helper class for file operations during test execution. Handles copying, deleting, and writing
 * files.
 */
public class FileOperationsHelper {

  private static final Set<String> EXCLUDE_DIRS =
      Set.of(
          ".git",
          ".gradle",
          ".idea",
          ".vscode",
          "build",
          "target",
          "node_modules",
          "logs",
          "out",
          ".DS_Store");

  /** Copies a project directory to a target directory, excluding common build artifacts. */
  public void copyProject(final Path sourceDir, final Path targetDir) throws IOException {
    if (sourceDir == null) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "sourceDir must not be null"));
    }
    if (targetDir == null) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "targetDir must not be null"));
    }
    Files.createDirectories(targetDir);
    Files.walkFileTree(
        sourceDir,
        new SimpleFileVisitor<>() {

          @Override
          public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
              throws IOException {
            final Path directoryName = dir.getFileName();
            // Only skip generated/cache directories at the project root.
            final boolean isExcludedTopLevelDirectory =
                directoryName != null
                    && EXCLUDE_DIRS.contains(directoryName.toString())
                    && isImmediateChildOfProjectRoot(sourceDir, dir);
            if (isExcludedTopLevelDirectory) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            final Path targetDirectory = targetDir.resolve(sourceDir.relativize(dir));
            Files.createDirectories(targetDirectory);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
              throws IOException {
            final Path targetFile = targetDir.resolve(sourceDir.relativize(file));
            // Exclude files that might be huge or irrelevant
            final boolean isSkippedArtifact =
                file.toString().endsWith(".log") || file.toString().endsWith(".hprof");
            if (isSkippedArtifact) {
              return FileVisitResult.CONTINUE;
            }
            Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
            // Restore executable permission for gradlew/mvnw
            restoreExecutablePermission(file, targetFile);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private void restoreExecutablePermission(final Path source, final Path target) {
    final Path sourceFileName = source.getFileName();
    if (sourceFileName == null) {
      return;
    }
    final String sourceFileNameText = sourceFileName.toString();
    final boolean isWrapperScript =
        "gradlew".equals(sourceFileNameText) || "mvnw".equals(sourceFileNameText);
    if (!isWrapperScript) {
      return;
    }
    if (!target.toFile().setExecutable(true)) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Failed to set executable: " + target));
    }
  }

  /** Deletes a directory and all its contents recursively. */
  public void deleteDirectory(final Path path) throws IOException {
    if (path == null || !Files.exists(path)) {
      return;
    }
    Files.walkFileTree(
        path,
        new SimpleFileVisitor<>() {

          @Override
          public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
              throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  /** Writes a test file to the appropriate location in a temporary directory. */
  public void writeTestFile(
      final Path tempDir,
      final String packageName,
      final String testClassName,
      final String testCode)
      throws IOException {
    if (tempDir == null) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "tempDir must not be null"));
    }
    if (testClassName == null || testClassName.isBlank()) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "testClassName must not be blank"));
    }
    Path testSourceDirectory = tempDir.resolve("src/test/java");
    if (packageName != null && !packageName.isEmpty()) {
      testSourceDirectory = testSourceDirectory.resolve(packageName.replace('.', '/'));
    }
    Files.createDirectories(testSourceDirectory);
    Files.writeString(testSourceDirectory.resolve(testClassName + ".java"), testCode);
  }

  /** Cleans up a temporary directory, logging any errors. */
  public void cleanupTempDir(final Path tempDir) {
    if (tempDir == null) {
      return;
    }
    try {
      deleteDirectory(tempDir);
    } catch (IOException exception) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Failed to cleanup temp dir: " + exception.getMessage()));
    }
  }

  /** Creates directories (wrapper for Files.createDirectories). */
  public void createDirectories(final Path dir) throws IOException {
    Files.createDirectories(dir);
  }

  private boolean isImmediateChildOfProjectRoot(final Path sourceDir, final Path directory) {
    final Path parentDirectory = directory.getParent();
    return parentDirectory != null && parentDirectory.equals(sourceDir);
  }
}
