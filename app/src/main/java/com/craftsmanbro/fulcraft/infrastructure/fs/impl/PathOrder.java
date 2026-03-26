package com.craftsmanbro.fulcraft.infrastructure.fs.impl;

import java.nio.file.Path;
import java.util.Comparator;

/**
 * Provides deterministic ordering for Path objects.
 *
 * <p>Used to ensure consistent file enumeration order across different operating systems and file
 * system implementations. This is critical for reproducibility of analysis and generation results.
 */
public final class PathOrder {

  private PathOrder() {
    // Utility class - prevent instantiation
  }

  /**
   * A stable comparator for Path objects that produces consistent ordering regardless of OS or file
   * system.
   *
   * <p>Compares by absolute, normalized path string representation with normalized separators.
   */
  public static final Comparator<Path> STABLE = Comparator.comparing(PathOrder::normalizedPathKey);

  private static String normalizedPathKey(final Path path) {
    return normalizeSeparators(normalizedAbsolutePathString(path));
  }

  private static String normalizedAbsolutePathString(final Path path) {
    return path.toAbsolutePath().normalize().toString();
  }

  private static String normalizeSeparators(final String path) {
    return path.replace('\\', '/');
  }
}
