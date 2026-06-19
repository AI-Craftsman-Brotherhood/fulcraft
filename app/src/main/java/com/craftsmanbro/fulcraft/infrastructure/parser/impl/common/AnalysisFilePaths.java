package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Produces project-root-relative source file paths so that {@code ClassInfo.filePath} is consistent
 * across analysis engines (JavaParser and Spoon).
 *
 * <p>Both engines must agree on the convention; otherwise downstream report/document writers (e.g.
 * {@code DocumentUtils.generateSourceAlignedReportPath}) emit the same package into divergent
 * directory trees. The chosen convention is <strong>project-root-relative</strong> — e.g. {@code
 * src/main/java/com/foo/Bar.java} — matching the contract documented on that writer and the path
 * produced by the JavaParser engine.
 */
public final class AnalysisFilePaths {

  private AnalysisFilePaths() {
    // utility
  }

  /**
   * Relativize {@code absoluteFile} against the project root. Falls back to the source root, then
   * to the normalized absolute path, when the file is not located under the project root.
   *
   * @param absoluteFile path to the analyzed source file (must not be null)
   * @param projectRoot project root directory (may be null)
   * @param sourceRoot source root directory such as {@code src/main/java} (may be null)
   * @return a path string using the platform separator, never null
   */
  public static String toProjectRelative(
      final Path absoluteFile, final Path projectRoot, final Path sourceRoot) {
    Objects.requireNonNull(absoluteFile, "absoluteFile must not be null");
    final Path abs = absoluteFile.toAbsolutePath().normalize();
    final String fromRoot = relativizeIfChild(abs, projectRoot);
    if (fromRoot != null) {
      return fromRoot;
    }
    final String fromSource = relativizeIfChild(abs, sourceRoot);
    if (fromSource != null) {
      return fromSource;
    }
    return abs.toString();
  }

  private static String relativizeIfChild(final Path absoluteFile, final Path base) {
    if (base == null) {
      return null;
    }
    final Path normalizedBase = base.toAbsolutePath().normalize();
    if (absoluteFile.startsWith(normalizedBase)) {
      return normalizedBase.relativize(absoluteFile).toString();
    }
    return null;
  }
}
