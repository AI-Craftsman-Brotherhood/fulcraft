package com.craftsmanbro.fulcraft.plugins.reporting.core.service.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportHistory;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportHistory.HistoricalRun;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrendAnalyzerTest {

  @Test
  void updateSummaryWithTrend_setsDeltaAndAppendsHistory() {
    TrendAnalyzer analyzer = new TrendAnalyzer();

    GenerationSummary first = createSummary("run-1", 1000L, 10, 5, 5, 0, 0.5);
    ReportHistory history = analyzer.updateSummaryWithTrend(first, null);
    assertNull(first.getSuccessRateDelta());
    assertNotNull(history);
    assertEquals(1, history.size());
    assertEquals("run-1", history.getLatestRun().runId());

    GenerationSummary second = createSummary("run-2", 2000L, 10, 6, 4, 0, 0.6);
    history = analyzer.updateSummaryWithTrend(second, history);

    assertEquals(0.1, second.getSuccessRateDelta(), 1e-6);
    assertEquals(2, history.size());
    assertEquals("run-2", history.getLatestRun().runId());
  }

  @Test
  void updateSummaryWithTrend_skipsDeltaWhenCurrentSuccessRateMissing() {
    TrendAnalyzer analyzer = new TrendAnalyzer();

    GenerationSummary first = createSummary("run-1", 1000L, 10, 6, 4, 0, 0.6);
    ReportHistory history = analyzer.updateSummaryWithTrend(first, null);

    GenerationSummary second = createSummary("run-2", 2000L, 10, 4, 6, 0, null);
    history = analyzer.updateSummaryWithTrend(second, history);

    assertNull(second.getSuccessRateDelta());
    assertEquals(2, history.size());
    assertEquals("run-2", history.getLatestRun().runId());
    assertEquals(0.4, history.getLatestRun().successRate(), 1e-6);
  }

  @Test
  void updateSummaryWithTrend_trimsHistoryBeyondMax() {
    TrendAnalyzer analyzer = new TrendAnalyzer();

    List<HistoricalRun> runs = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      runs.add(new HistoricalRun("run-" + i, i, 10, 5, 5, 0, 0.5));
    }
    ReportHistory history = new ReportHistory(runs);

    GenerationSummary summary = createSummary("run-100", 2000L, 10, 7, 3, 0, 0.7);
    ReportHistory updated = analyzer.updateSummaryWithTrend(summary, history);

    assertEquals(100, updated.size());
    assertEquals("run-1", updated.getRuns().get(0).runId());
    assertEquals("run-100", updated.getLatestRun().runId());
  }

  @Test
  void updateSummaryWithTrend_usesLatestRunForNegativeDelta() {
    TrendAnalyzer analyzer = new TrendAnalyzer();

    List<HistoricalRun> runs = new ArrayList<>();
    runs.add(new HistoricalRun("run-1", 1000L, 10, 8, 2, 0, 0.8));
    runs.add(new HistoricalRun("run-2", 2000L, 10, 4, 6, 0, 0.4));
    ReportHistory history = new ReportHistory(runs);

    GenerationSummary current = createSummary("run-3", 3000L, 10, 3, 7, 0, 0.3);
    ReportHistory updated = analyzer.updateSummaryWithTrend(current, history);

    assertEquals(-0.1, current.getSuccessRateDelta(), 1e-6);
    assertEquals(3, updated.size());
    assertEquals("run-3", updated.getLatestRun().runId());
  }

  @Test
  void updateSummaryWithTrend_trimsMultipleRunsWhenHistoryFarBeyondMax() {
    TrendAnalyzer analyzer = new TrendAnalyzer();

    List<HistoricalRun> runs = new ArrayList<>();
    for (int i = 0; i < 103; i++) {
      runs.add(new HistoricalRun("run-" + i, i, 10, 5, 5, 0, 0.5));
    }
    ReportHistory history = new ReportHistory(runs);

    GenerationSummary summary = createSummary("run-103", 3000L, 10, 7, 3, 0, 0.7);
    ReportHistory updated = analyzer.updateSummaryWithTrend(summary, history);

    assertEquals(100, updated.size());
    assertEquals("run-4", updated.getRuns().get(0).runId());
    assertEquals("run-103", updated.getLatestRun().runId());
  }

  @Test
  void updateSummaryWithTrend_rejectsNullSummary() {
    TrendAnalyzer analyzer = new TrendAnalyzer();

    assertThrows(NullPointerException.class, () -> analyzer.updateSummaryWithTrend(null, null));
  }

  private GenerationSummary createSummary(
      String runId,
      long timestamp,
      int totalTasks,
      int succeeded,
      int failed,
      int skipped,
      Double successRate) {
    GenerationSummary summary = new GenerationSummary();
    summary.setProjectId("test-project");
    summary.setRunId(runId);
    summary.setTimestamp(timestamp);
    summary.setTotalTasks(totalTasks);
    summary.setSucceeded(succeeded);
    summary.setFailed(failed);
    summary.setSkipped(skipped);
    if (successRate != null) {
      summary.setSuccessRate(successRate);
    }
    return summary;
  }
}
