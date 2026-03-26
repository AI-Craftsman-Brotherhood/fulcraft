package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Helper for managing path exclusion rules.
 *
 * <p>Note: This is a copy of {@code feature.analysis.core.util.PathExcluder} moved to the
 * infrastructure layer to eliminate the reverse dependency on feature internals.
 */
public final class PathExcluder {

  private static final List<String> DEFAULT_TEST_EXCLUDE_PATHS =
      List.of("src/test", "src/test/java", "app/src/test", "app/src/test/java", "test", "tests");

  private static final List<String> DEFAULT_NON_TEST_EXCLUDE_PATHS =
      List.of(
          "build",
          "target",
          "out",
          "bin",
          "generated",
          "src/generated",
          "src/main/java/generated",
          "build/generated",
          "target/generated");

  private PathExcluder() {}

  /** Builds a list of paths to exclude from analysis. */
  public static List<Path> buildExcludeRoots(final Path rootPath, final List<String> configured) {
    return buildExcludeRoots(rootPath, configured, true);
  }

  /** Builds a list of paths to exclude from analysis with test exclusion toggle. */
  public static List<Path> buildExcludeRoots(
      final Path rootPath, final List<String> configured, final boolean excludeTests) {
    Objects.requireNonNull(
        rootPath,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "rootPath must not be null"));
    final Set<Path> result = new LinkedHashSet<>();
    // Always include defaults
    for (final String raw : DEFAULT_NON_TEST_EXCLUDE_PATHS) {
      final Path candidate = rootPath.resolve(raw).normalize();
      result.add(candidate);
    }
    if (excludeTests) {
      for (final String raw : DEFAULT_TEST_EXCLUDE_PATHS) {
        final Path candidate = rootPath.resolve(raw).normalize();
        result.add(candidate);
      }
    }
    // Add user-provided excludes
    if (configured != null) {
      for (final String raw : configured) {
        if (raw == null || raw.isBlank()) {
          continue;
        }
        Path candidate = Paths.get(raw);
        if (!candidate.isAbsolute()) {
          candidate = rootPath.resolve(candidate);
        }
        result.add(candidate.normalize());
      }
    }
    return List.copyOf(result);
  }

  /** Checks if a file path should be excluded from analysis. */
  public static boolean isExcluded(final Path filePath, final List<Path> excludeRoots) {
    Objects.requireNonNull(
        filePath,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "filePath must not be null"));
    Objects.requireNonNull(
        excludeRoots,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "excludeRoots must not be null"));
    for (final Path exclude : excludeRoots) {
      if (filePath.startsWith(exclude)) {
        return true;
      }
    }
    return false;
  }
}
