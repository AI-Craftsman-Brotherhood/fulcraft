package com.craftsmanbro.fulcraft.plugins.reporting.contract;

import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import java.nio.file.Path;
import java.util.Optional;

/** Secondary port for resolving and parsing test report files. */
public interface ReportFileAccessor {

  /**
   * Resolves the report directory for the project.
   *
   * @param projectRoot the project root
   * @return the report directory path
   */
  Path resolveReportDir(Path projectRoot);

  /**
   * Returns true if the report directory exists and is readable.
   *
   * @param reportDir the report directory
   * @return true if the directory exists and is a directory
   */
  boolean isReportsDirectory(Path reportDir);

  /**
   * Returns true if any report file exists under the report directory.
   *
   * @param reportDir the report directory
   * @return true if any report file exists
   */
  boolean hasAnyReportFile(Path reportDir);

  /**
   * Finds a report file for the specified test identifiers.
   *
   * @param reportDir the report directory
   * @param baseTestName base test name (FQN)
   * @param testClassName test class name
   * @return optional report file path
   */
  Optional<Path> findReportFile(Path reportDir, String baseTestName, String testClassName);

  /**
   * Returns true if the report file exists.
   *
   * @param reportFile the report file path
   * @return true if the file exists
   */
  boolean reportFileExists(Path reportFile);

  /**
   * Parses the report file into the provided result model.
   *
   * @param reportFile the report file path
   * @param result the result to populate
   * @return true if parsing succeeded
   */
  boolean parseReport(Path reportFile, ReportTaskResult result);
}
