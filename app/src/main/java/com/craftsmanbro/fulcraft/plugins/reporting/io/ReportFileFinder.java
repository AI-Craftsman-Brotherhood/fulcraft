package com.craftsmanbro.fulcraft.plugins.reporting.io;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.impl.PathOrder;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Helper class for finding test report files. */
public class ReportFileFinder {

  private static final String TEST_PREFIX = "TEST-";

  private static final int MAX_SUFFIX_SEARCH_COUNT = 10;

  /** Resolves the report directory based on project structure. */
  public Path resolveReportDir(final Path projectRoot) {
    final var customReport = System.getenv("TEST_REPORT_DIR");
    if (customReport != null && !customReport.isBlank()) {
      final var customPath = Paths.get(customReport);
      if (Files.isDirectory(customPath)) {
        return customPath;
      }
    }
    // Gradle default
    var reportDir = projectRoot.resolve("build/test-results/test");
    if (!Files.exists(reportDir)) {
      // Maven default
      reportDir = projectRoot.resolve("target/surefire-reports");
    }
    return reportDir;
  }

  /** Finds a report file using multiple naming patterns. */
  public java.util.Optional<Path> findReportFile(
      final Path reportDir, final String baseTestName, final String testClassName) {
    if (reportDir == null || !Files.isDirectory(reportDir)) {
      return java.util.Optional.empty();
    }
    // Pattern 1: Standard JUnit format (TEST-<FQN>.xml)
    final var f1 = reportDir.resolve(TEST_PREFIX + baseTestName + ".xml");
    if (Files.exists(f1)) {
      return java.util.Optional.of(f1);
    }
    // Pattern 2: With suffix (TEST-<FQN>_1.xml, etc.) for multiple runs
    final var suffixMatch = findWithSuffix(reportDir, baseTestName);
    if (suffixMatch.isPresent()) {
      return suffixMatch;
    }
    // Pattern 3: Simple class name only (TEST-<ClassName>.xml)
    final var f2 = reportDir.resolve(TEST_PREFIX + testClassName + ".xml");
    if (Files.exists(f2)) {
      return java.util.Optional.of(f2);
    }
    // Pattern 4: Without TEST- prefix (<FQN>.xml)
    final var f3 = reportDir.resolve(baseTestName + ".xml");
    if (Files.exists(f3)) {
      return java.util.Optional.of(f3);
    }
    // Pattern 5: Glob search for partial matches
    return findByGlob(reportDir, baseTestName, testClassName);
  }

  /** Returns true if the report directory contains any XML files. */
  public boolean hasAnyReportFile(final Path reportDir) {
    if (reportDir == null || !Files.isDirectory(reportDir)) {
      return false;
    }
    try (Stream<Path> stream = Files.list(reportDir)) {
      return stream.anyMatch(
          path -> {
            final var fileName = path.getFileName();
            final String name = fileName != null ? fileName.toString() : path.toString();
            return name.endsWith(".xml");
          });
    } catch (IOException e) {
      Logger.debug(MessageSource.getMessage("report.file_finder.scan_failed", e.getMessage()));
      return false;
    }
  }

  private java.util.Optional<Path> findWithSuffix(final Path reportDir, final String baseTestName) {
    for (int suffix = 1; suffix <= MAX_SUFFIX_SEARCH_COUNT; suffix++) {
      final var fs = reportDir.resolve(TEST_PREFIX + baseTestName + "_" + suffix + ".xml");
      if (Files.exists(fs)) {
        return java.util.Optional.of(fs);
      }
    }
    return java.util.Optional.empty();
  }

  private java.util.Optional<Path> findByGlob(
      final Path reportDir, final String baseTestName, final String testClassName) {
    try (var stream = Files.list(reportDir)) {
      return stream
          .filter(
              path -> {
                final var fileName = path.getFileName();
                final String name = fileName != null ? fileName.toString() : path.toString();
                return name.endsWith(".xml")
                    && (name.contains(testClassName)
                        || name.contains(baseTestName.replace(".", "_")));
              })
          .sorted(PathOrder.STABLE)
          .findFirst();
    } catch (IOException e) {
      Logger.debug(MessageSource.getMessage("report.file_finder.glob_failed", e.getMessage()));
    }
    return java.util.Optional.empty();
  }
}
