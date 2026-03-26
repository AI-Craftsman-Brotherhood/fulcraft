package com.craftsmanbro.fulcraft.plugins.analysis.flow;

import com.craftsmanbro.fulcraft.plugins.analysis.core.service.report.AnalysisReportService;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Flow responsible for generating analysis reports.
 *
 * <p>This flow delegates to {@link AnalysisReportService} and keeps the pipeline wiring stable.
 */
public class AnalysisReportFlow {

  private final AnalysisReportService analysisReportService;

  public AnalysisReportFlow() {
    this.analysisReportService = new AnalysisReportService();
  }

  /** Constructor for testing with a custom service. */
  AnalysisReportFlow(final AnalysisReportService analysisReportService) {
    this.analysisReportService = Objects.requireNonNull(analysisReportService);
  }

  /**
   * Generates a quality report from analysis summary.
   *
   * @param analysisDir the analysis output directory
   * @return true if report was generated successfully
   */
  public boolean generateQualityReport(final Path analysisDir) {
    return analysisReportService.generateQualityReport(analysisDir);
  }

  /**
   * Generates a quality report and returns the path to the generated report.
   *
   * @param analysisDir the analysis output directory
   * @return the path to the generated report, or null if generation failed
   */
  public Path generateAndGetReportPath(final Path analysisDir) {
    return analysisReportService.generateAndGetReportPath(analysisDir);
  }

  /**
   * Checks if a quality report exists for the project.
   *
   * @param analysisDir the analysis output directory
   * @return true if a quality report exists
   */
  public boolean hasExistingReport(final Path analysisDir) {
    return analysisReportService.hasExistingReport(analysisDir);
  }

  /**
   * Gets the path to the quality report for the project.
   *
   * @param analysisDir the analysis output directory
   * @return the path to the quality report (may not exist)
   */
  public Path getReportPath(final Path analysisDir) {
    return analysisReportService.getReportPath(analysisDir);
  }
}
