package com.craftsmanbro.fulcraft.plugins.reporting.core.service.quality;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportHistory;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportHistory.HistoricalRun;
import java.util.Objects;

/**
 * Analyzes trends across historical runs.
 *
 * <p>This service calculates success rate deltas compared to previous runs, enabling measurement of
 * improvement effects over time.
 */
public class TrendAnalyzer {

  private static final int MAX_HISTORY_SIZE = 100;

  /**
   * Updates the summary with trend data (successRateDelta) based on previous runs.
   *
   * @param summary the current generation summary
   * @param history the history to update (nullable)
   * @return the updated history
   */
  public ReportHistory updateSummaryWithTrend(
      final GenerationSummary summary, final ReportHistory history) {
    Objects.requireNonNull(
        summary,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "summary must not be null"));
    final ReportHistory working = history != null ? history : new ReportHistory();
    final HistoricalRun previousRun = working.getLatestRun();
    if (previousRun != null && summary.getSuccessRate() != null) {
      final double delta = summary.getSuccessRate() - previousRun.successRate();
      summary.setSuccessRateDelta(delta);
      final String ratePct =
          String.format(java.util.Locale.ROOT, "%.1f", summary.getSuccessRate() * 100);
      final String deltaPct = String.format(java.util.Locale.ROOT, "%+.1f", delta * 100);
      Logger.info(MessageSource.getMessage("report.trend.success_rate", ratePct, deltaPct));
    }
    // Add current run to history
    final HistoricalRun currentRun = ReportHistory.fromSummary(summary);
    working.addRun(currentRun);
    // Trim history if too large
    while (working.size() > MAX_HISTORY_SIZE) {
      final var runs = new java.util.ArrayList<>(working.getRuns());
      runs.remove(0);
      working.setRuns(runs);
    }
    return working;
  }
}
