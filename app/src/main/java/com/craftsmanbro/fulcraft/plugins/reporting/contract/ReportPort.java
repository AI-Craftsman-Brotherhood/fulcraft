package com.craftsmanbro.fulcraft.plugins.reporting.contract;

import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import java.nio.file.Path;

/**
 * Primary port for the reporting phase.
 *
 * <p>Implemented by the reporting flow to orchestrate report generation.
 */
public interface ReportPort {

  /**
   * Generates reports based on the configured format(s).
   *
   * @throws ReportWriteException if report generation or file writing fails
   */
  void generateReports() throws ReportWriteException;

  /**
   * Generates a report in a specific format.
   *
   * @param format the report format to generate
   * @throws ReportWriteException if the format is not supported or writing fails
   */
  void generateReport(ReportFormat format) throws ReportWriteException;

  /**
   * Returns the resolved output directory for reports.
   *
   * @return the output directory path
   */
  Path getOutputDirectory();
}
