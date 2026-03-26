package com.craftsmanbro.fulcraft.plugins.reporting.core.service.quality;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import java.util.Objects;

/**
 * Integrates JaCoCo coverage data into generation summaries.
 *
 * <p>This service applies line and branch coverage percentages to the generation summary for
 * inclusion in reports.
 */
public class CoverageIntegrator {

  /**
   * Updates the summary with coverage data from JaCoCo.
   *
   * @param summary the generation summary to update
   * @param coverageSummary the coverage summary (nullable)
   */
  public void updateSummaryWithCoverage(
      final GenerationSummary summary, final CoverageSummary coverageSummary) {
    Objects.requireNonNull(
        summary,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "summary must not be null"));
    if (coverageSummary == null) {
      return;
    }
    final Double lineCoverage =
        extractCoverage(coverageSummary.getLineCovered(), coverageSummary.getLineTotal(), "LINE");
    final Double branchCoverage =
        extractCoverage(
            coverageSummary.getBranchCovered(), coverageSummary.getBranchTotal(), "BRANCH");
    if (lineCoverage != null) {
      summary.setLineCoverage(lineCoverage);
      final String percent = String.format(java.util.Locale.ROOT, "%.1f", lineCoverage * 100);
      Logger.info(MessageSource.getMessage("report.coverage.line", percent));
    }
    if (branchCoverage != null) {
      summary.setBranchCoverage(branchCoverage);
      final String percent = String.format(java.util.Locale.ROOT, "%.1f", branchCoverage * 100);
      Logger.info(MessageSource.getMessage("report.coverage.branch", percent));
    }
  }

  /**
   * Extracts coverage percentage from covered/total counters.
   *
   * @param covered covered count
   * @param total total count
   * @param type the coverage type name for logging
   * @return the coverage as a ratio (0.0-1.0), or null if not found
   */
  private Double extractCoverage(final int covered, final int total, final String type) {
    if (total == 0) {
      Logger.debug(MessageSource.getMessage("report.coverage.no_data", type));
      return null;
    }
    return (double) covered / total;
  }
}
