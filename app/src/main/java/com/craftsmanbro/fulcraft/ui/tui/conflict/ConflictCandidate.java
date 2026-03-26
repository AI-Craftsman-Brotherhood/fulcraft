package com.craftsmanbro.fulcraft.ui.tui.conflict;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a file that would conflict with an existing file during test generation.
 *
 * <p>This record contains information about both the target file path and the existing file path.
 *
 * @param targetPath the intended output path for the generated test file
 * @param existingPath the path of the existing file that would be overwritten
 * @param fileName the base file name for display purposes
 */
public record ConflictCandidate(Path targetPath, Path existingPath, String fileName) {

  /**
   * Creates a new ConflictCandidate.
   *
   * @param targetPath the target output path (must not be null)
   * @param existingPath the existing file path (must not be null)
   * @param fileName the display file name (must not be null)
   */
  public ConflictCandidate {
    Objects.requireNonNull(targetPath, "targetPath must not be null");
    Objects.requireNonNull(existingPath, "existingPath must not be null");
    Objects.requireNonNull(fileName, "fileName must not be null");
    if (fileName.isBlank()) {
      fileName = resolveDisplayName(targetPath, existingPath);
    }
  }

  /**
   * Creates a ConflictCandidate from target and existing paths.
   *
   * @param targetPath the intended output path for the generated test file
   * @param existingPath the path of the existing file that would be overwritten
   * @return a new ConflictCandidate
   */
  public static ConflictCandidate of(final Path targetPath, final Path existingPath) {
    Objects.requireNonNull(targetPath, "targetPath must not be null");
    Objects.requireNonNull(existingPath, "existingPath must not be null");
    final String resolvedDisplayName = resolveDisplayName(targetPath, existingPath);
    return new ConflictCandidate(targetPath, existingPath, resolvedDisplayName);
  }

  /**
   * Creates a ConflictCandidate from a single path (when target and existing are the same).
   *
   * @param path the file path
   * @return a new ConflictCandidate
   */
  public static ConflictCandidate of(final Path path) {
    Objects.requireNonNull(path, "path must not be null");
    return of(path, path);
  }

  /**
   * Creates a ConflictCandidate from a path string.
   *
   * @param pathString the file path as a string
   * @return a new ConflictCandidate
   */
  public static ConflictCandidate of(final String pathString) {
    Objects.requireNonNull(pathString, "pathString must not be null");
    return of(Path.of(pathString));
  }

  private static String resolveDisplayName(final Path targetPath, final Path existingPath) {
    final String targetDisplayName = resolveFileName(targetPath);
    if (!targetDisplayName.isBlank()) {
      return targetDisplayName;
    }
    return resolveFileName(existingPath);
  }

  private static String resolveFileName(final Path path) {
    final Path pathFileName = path.getFileName();
    return pathFileName != null ? pathFileName.toString() : path.toString();
  }
}
