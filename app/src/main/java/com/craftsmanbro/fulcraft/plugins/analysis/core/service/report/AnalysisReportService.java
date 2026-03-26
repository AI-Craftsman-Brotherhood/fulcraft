package com.craftsmanbro.fulcraft.plugins.analysis.core.service.report;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.reporting.adapter.QualityReportGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Service responsible for generating analysis reports.
 *
 * <p>This service generates quality reports from analysis summaries, including:
 *
 * <ul>
 *   <li>Type resolution quality metrics
 *   <li>Dynamic feature analysis
 *   <li>Code quality indicators
 * </ul>
 */
public class AnalysisReportService {

  private static final String QUALITY_REPORT_MARKDOWN = "quality_report.md";

  private static final String QUALITY_REPORT_JSON = "quality_report.json";

  private final QualityReportGenerator reportGenerator;

  public AnalysisReportService() {
    this.reportGenerator = new QualityReportGenerator();
  }

  /** Constructor for testing with mock generator. */
  AnalysisReportService(final QualityReportGenerator reportGenerator) {
    this.reportGenerator = Objects.requireNonNull(reportGenerator);
  }

  /**
   * Generates a quality report from analysis summary.
   *
   * @param analysisDir the analysis output directory
   * @return true if report was generated successfully
   */
  public boolean generateQualityReport(final Path analysisDir) {
    Objects.requireNonNull(
        analysisDir,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "analysisDir must not be null"));
    try {
      final boolean generated = reportGenerator.generate(analysisDir);
      if (generated) {
        Logger.debug(
            MessageSource.getMessage("analysis.report_service.generated_for", analysisDir));
      }
      return generated;
    } catch (final IOException e) {
      Logger.warn(
          MessageSource.getMessage("analysis.quality_report.generate_failed", e.getMessage()));
      return false;
    }
  }

  /**
   * Generates a quality report and returns the path to the generated report.
   *
   * @param analysisDir the analysis output directory
   * @return the path to the generated report, or null if generation failed
   */
  public Path generateAndGetReportPath(final Path analysisDir) {
    Objects.requireNonNull(
        analysisDir,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "analysisDir must not be null"));
    try {
      final boolean generated = reportGenerator.generate(analysisDir);
      if (!generated) {
        Logger.warn(MessageSource.getMessage("analysis.quality_report.not_generated"));
        return null;
      }

      final Path existingReportPath = findExistingReportPath(analysisDir);
      if (existingReportPath != null) {
        Logger.debug(
            MessageSource.getMessage("analysis.report_service.generated_at", existingReportPath));
        return existingReportPath;
      }
      Logger.warn(MessageSource.getMessage("analysis.quality_report.file_missing"));
      return null;
    } catch (final IOException e) {
      Logger.warn(
          MessageSource.getMessage("analysis.quality_report.generate_failed", e.getMessage()));
      return null;
    }
  }

  /**
   * Checks if a quality report exists for the project.
   *
   * @param analysisDir the analysis output directory
   * @return true if a quality report exists
   */
  public boolean hasExistingReport(final Path analysisDir) {
    Objects.requireNonNull(
        analysisDir,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "analysisDir must not be null"));
    return findExistingReportPath(analysisDir) != null;
  }

  /**
   * Gets the path to the quality report for the project.
   *
   * @param analysisDir the analysis output directory
   * @return the path to the quality report, or the default Markdown path if missing
   */
  public Path getReportPath(final Path analysisDir) {
    Objects.requireNonNull(
        analysisDir,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "analysisDir must not be null"));
    final Path existingReportPath = findExistingReportPath(analysisDir);
    if (existingReportPath != null) {
      return existingReportPath;
    }
    return analysisDir.resolve(QUALITY_REPORT_MARKDOWN);
  }

  private Path findExistingReportPath(final Path analysisDir) {
    final Path markdownPath = analysisDir.resolve(QUALITY_REPORT_MARKDOWN);
    if (Files.exists(markdownPath)) {
      return markdownPath;
    }
    final Path jsonPath = analysisDir.resolve(QUALITY_REPORT_JSON);
    if (Files.exists(jsonPath)) {
      return jsonPath;
    }
    return null;
  }
}
