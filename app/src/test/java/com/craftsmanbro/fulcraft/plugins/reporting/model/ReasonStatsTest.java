package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReasonStatsTest {

  @Test
  void ratesAreZeroWhenNoTasks() {
    ReasonStats stats = new ReasonStats();

    assertEquals(0.0, stats.getCompileFailRate(), 0.0001);
    assertEquals(0.0, stats.getRuntimeFailRate(), 0.0001);
    assertEquals(0.0, stats.getFixAttemptRate(), 0.0001);
    assertEquals(0.0, stats.getFixSuccessRate(), 0.0001);
    assertEquals(0.0, stats.getWouldBeExcludedRate(), 0.0001);
    assertEquals(0.0, stats.getExcludedRate(), 0.0001);
  }

  @Test
  void incrementsDriveComputedRates() {
    ReasonStats stats = new ReasonStats();

    for (int i = 0; i < 4; i++) {
      stats.incrementTaskCount();
    }
    stats.incrementCompileSuccess();
    stats.incrementCompileFail();
    stats.incrementTestFail();
    stats.incrementTestFail();
    stats.incrementFixAttempted();
    stats.incrementFixAttempted();
    stats.incrementFixSuccess();
    stats.incrementWouldBeExcluded();
    stats.incrementExcluded();

    assertEquals(4, stats.getTaskCount());
    assertEquals(1, stats.getCompileSuccessCount());
    assertEquals(1, stats.getCompileFailCount());
    assertEquals(2, stats.getTestFailCount());
    assertEquals(2, stats.getFixAttemptedCount());
    assertEquals(1, stats.getFixSuccessCount());
    assertEquals(1, stats.getWouldBeExcludedCount());
    assertEquals(1, stats.getExcludedCount());

    assertEquals(0.25, stats.getCompileFailRate(), 0.0001);
    assertEquals(0.5, stats.getRuntimeFailRate(), 0.0001);
    assertEquals(0.5, stats.getFixAttemptRate(), 0.0001);
    assertEquals(0.5, stats.getFixSuccessRate(), 0.0001);
    assertEquals(0.25, stats.getWouldBeExcludedRate(), 0.0001);
    assertEquals(0.25, stats.getExcludedRate(), 0.0001);
  }
}
