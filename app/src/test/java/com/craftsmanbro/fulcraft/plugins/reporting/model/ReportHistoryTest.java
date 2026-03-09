package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportHistoryTest {

  @Test
  void emptyHistoryReportsNoRuns() {
    ReportHistory history = new ReportHistory();

    assertTrue(history.isEmpty());
    assertEquals(0, history.size());
    assertNull(history.getLatestRun());
  }

  @Test
  void addRunStoresLatestAndDefensiveCopy() {
    ReportHistory.HistoricalRun run =
        new ReportHistory.HistoricalRun("run-1", 123L, 10, 7, 2, 1, 0.7);

    List<ReportHistory.HistoricalRun> source = new ArrayList<>();
    source.add(run);
    ReportHistory history = new ReportHistory(source);

    source.add(new ReportHistory.HistoricalRun("run-2", 456L, 5, 3, 1, 1, 0.6));

    assertFalse(history.isEmpty());
    assertEquals(1, history.size());
    assertEquals(run, history.getLatestRun());
    List<ReportHistory.HistoricalRun> runs = history.getRuns();
    assertThrows(UnsupportedOperationException.class, () -> runs.add(run));

    history.addRun(new ReportHistory.HistoricalRun("run-2", 456L, 5, 3, 1, 1, 0.6));
    assertEquals(2, history.size());
    assertEquals("run-2", history.getLatestRun().runId());
  }

  @Test
  void fromSummaryUsesProvidedSuccessRate() {
    GenerationSummary summary = new GenerationSummary();
    summary.setRunId("run-1");
    summary.setTimestamp(1000L);
    summary.setTotalTasks(10);
    summary.setSucceeded(5);
    summary.setFailed(3);
    summary.setSkipped(2);
    summary.setSuccessRate(0.42);

    ReportHistory.HistoricalRun run = ReportHistory.fromSummary(summary);

    assertEquals(0.42, run.successRate(), 0.0001);
  }

  @Test
  void fromSummaryComputesSuccessRateWhenMissing() {
    GenerationSummary summary = new GenerationSummary();
    summary.setRunId("run-1");
    summary.setTimestamp(1000L);
    summary.setTotalTasks(10);
    summary.setSucceeded(4);
    summary.setFailed(4);
    summary.setSkipped(2);

    ReportHistory.HistoricalRun run = ReportHistory.fromSummary(summary);

    assertEquals(0.5, run.successRate(), 0.0001);
  }

  @Test
  void fromSummaryHandlesZeroProcessedTasks() {
    GenerationSummary summary = new GenerationSummary();
    summary.setRunId("run-1");
    summary.setTimestamp(1000L);
    summary.setTotalTasks(1);
    summary.setSucceeded(0);
    summary.setFailed(0);
    summary.setSkipped(2);

    ReportHistory.HistoricalRun run = ReportHistory.fromSummary(summary);

    assertEquals(0.0, run.successRate(), 0.0001);
  }
}
