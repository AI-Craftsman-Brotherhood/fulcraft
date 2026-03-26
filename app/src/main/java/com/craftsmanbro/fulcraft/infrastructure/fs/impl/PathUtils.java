package com.craftsmanbro.fulcraft.infrastructure.fs.impl;

/**
 * Utility methods for path manipulation.
 *
 * <p>This class provides commonly used path operations that are shared across multiple components.
 */
public final class PathUtils {

  public static final String JAVA_EXTENSION = ".java";
  private static final String UNKNOWN_FILE_NAME = "unknown";
  private static final String EMPTY_DIRECTORY = "";

  private PathUtils() {
    // Utility class
  }

  /**
   * Extracts the file name from a path string.
   *
   * <p>Handles both Unix-style (/) and Windows-style (\) path separators.
   *
   * @param path The full path string, may be null
   * @return The file name portion, or "unknown" if path is null
   */
  public static String getFileName(final String path) {
    if (path == null) {
      return UNKNOWN_FILE_NAME;
    }
    final int lastSlash = lastPathSeparatorIndex(path);
    if (lastSlash == path.length() - 1) {
      return UNKNOWN_FILE_NAME;
    }
    return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
  }

  /**
   * Converts a fully qualified class name to a relative file path.
   *
   * <p>Example: {@code com.example.MyClass} → {@code com/example/MyClass.java}
   *
   * @param classFqn The fully qualified class name
   * @param extension The file extension (e.g., ".java")
   * @return The relative file path
   */
  public static String classNameToPath(final String classFqn, final String extension) {
    if (classFqn == null || classFqn.isEmpty()) {
      return null;
    }
    final String safeExtension = extension == null ? "" : extension;
    return classFqn.replace('.', '/') + safeExtension;
  }

  /**
   * Extracts the directory path from a full path string.
   *
   * <p>Handles both Unix-style (/) and Windows-style (\) path separators.
   *
   * @param path The full path string, may be null
   * @return The directory portion, or empty string if path is null or has no directory
   */
  public static String getDirectory(final String path) {
    if (path == null) {
      return EMPTY_DIRECTORY;
    }
    final int lastSlash = lastPathSeparatorIndex(path);
    return lastSlash >= 0 ? path.substring(0, lastSlash) : EMPTY_DIRECTORY;
  }

  private static int lastPathSeparatorIndex(final String path) {
    return Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
  }
}
