package com.craftsmanbro.fulcraft.plugins.reporting.core.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonStats;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.RunSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.SelectionEvaluation;
import com.craftsmanbro.fulcraft.plugins.reporting.model.SelectionStrength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelectionStrengthEvaluatorTest {

  private SelectionStrengthEvaluator evaluator;

  @BeforeEach
  void setUp() {
    evaluator = new SelectionStrengthEvaluator();
  }

  @Test
  void testEmptyData_returnsOK() {
    RunSummary runSummary = new RunSummary();
    runSummary.setTotalTasks(0);

    SelectionEvaluation result = evaluator.evaluate(runSummary, null);

    assertEquals(SelectionStrength.OK, result.strength());
    assertEquals(0.0, result.excludedRate());
  }

  @Test
  void testNullRunSummary_throwsException() {
    assertThrows(NullPointerException.class, () -> evaluator.evaluate(null, new ReasonSummary()));
  }

  @Test
  void testNormalCase_returnsOK() {
    RunSummary runSummary = createRunSummary(100, 10, 80, 70);
    ReasonSummary reasonSummary = createReasonSummary(false);

    SelectionEvaluation result = evaluator.evaluate(runSummary, reasonSummary);

    assertEquals(SelectionStrength.OK, result.strength());
    assertEquals(0.10, result.excludedRate(), 0.01);
    assertFalse(result.reason().isBlank());
  }

  @Test
  void testHighExclusionRate_tooStrict() {
    // 25% excluded (above 20% threshold)
    RunSummary runSummary = createRunSummary(100, 25, 60, 50);
    ReasonSummary reasonSummary = createReasonSummaryWithHighSuccessRate();

    SelectionEvaluation result = evaluator.evaluate(runSummary, reasonSummary);

    assertEquals(SelectionStrength.TOO_STRICT, result.strength());
    assertEquals(0.25, result.excludedRate(), 0.01);
    assertFalse(result.reason().isBlank());
    assertTrue(result.reason().contains("25"));
    assertTrue(result.reason().contains("20"));
  }

  @Test
  void testBorderlineExclusionRate_notTooStrict() {
    // Exactly 20% (at threshold, not above)
    RunSummary runSummary = createRunSummary(100, 20, 70, 60);
    ReasonSummary reasonSummary = createReasonSummaryWithHighSuccessRate();

    SelectionEvaluation result = evaluator.evaluate(runSummary, reasonSummary);

    // At threshold, not exceeding - should be OK
    assertEquals(SelectionStrength.OK, result.strength());
  }

  @Test
  void testFailureConcentration_tooWeak() {
    // Low exclusion, but failures concentrated on one reason
    RunSummary runSummary = createRunSummary(100, 5, 80, 60);
    ReasonSummary reasonSummary = createReasonSummaryWithConcentratedFailures();

    SelectionEvaluation result = evaluator.evaluate(runSummary, reasonSummary);

    assertEquals(SelectionStrength.TOO_WEAK, result.strength());
    assertNotNull(result.dominantFailureReason());
    assertTrue(result.reason().contains(result.dominantFailureReason()));
  }

  @Test
  void testLowFailureRate_notTooWeak() {
    // Low failure rate (below minFailureRate 15%), even with concentration
    // executedTasks=95, testPass=85 => passRate=89.5%, failRate=10.5% < 15%
    RunSummary runSummary = createRunSummary(100, 5, 90, 85);
    ReasonSummary reasonSummary = createReasonSummaryWithConcentratedFailures();

    SelectionEvaluation result = evaluator.evaluate(runSummary, reasonSummary);

    // Pass rate is high (85/95=89.5%), failure rate is 10.5% < 15% threshold
    assertEquals(SelectionStrength.OK, result.strength());
  }

  @Test
  void testNoConcentration_notTooWeak() {
    // Failures distributed across reasons
    RunSummary runSummary = createRunSummary(100, 5, 80, 50);
    ReasonSummary reasonSummary = createReasonSummaryWithDistributedFailures();

    SelectionEvaluation result = evaluator.evaluate(runSummary, reasonSummary);

    assertEquals(SelectionStrength.OK, result.strength());
    assertNull(result.dominantFailureReason());
  }

  @Test
  void testHighExclusionWithoutReasonData_notTooStrict() {
    RunSummary runSummary = createRunSummary(100, 40, 45, 40);

    SelectionEvaluation result = evaluator.evaluate(runSummary, null);

    assertEquals(SelectionStrength.OK, result.strength());
    assertEquals(0.40, result.excludedRate(), 0.01);
  }

  @Test
  void testHighExclusionLowWastedPotential_notTooStrict() {
    RunSummary runSummary = createRunSummary(100, 30, 65, 60);
    ReasonSummary reasonSummary = createReasonSummaryWithLowWastedPotential();

    SelectionEvaluation result = evaluator.evaluate(runSummary, reasonSummary);

    assertEquals(SelectionStrength.OK, result.strength());
  }

  @Test
  void testNoTestFailures_notTooWeak() {
    RunSummary runSummary = createRunSummary(100, 5, 70, 60);
    ReasonSummary reasonSummary = new ReasonSummary();
    ReasonStats lowConfStats = reasonSummary.getOrCreateStats("low_conf");
    for (int i = 0; i < 20; i++) {
      lowConfStats.incrementTaskCount();
      lowConfStats.incrementCompileSuccess();
    }

    SelectionEvaluation result = evaluator.evaluate(runSummary, reasonSummary);

    assertEquals(SelectionStrength.OK, result.strength());
    assertNull(result.dominantFailureReason());
  }

  @Test
  void testFailureConcentrationAtThreshold_tooWeak() {
    RunSummary runSummary = createRunSummary(100, 5, 65, 50);
    ReasonSummary reasonSummary = new ReasonSummary();

    ReasonStats reasonA = reasonSummary.getOrCreateStats("a_reason");
    for (int i = 0; i < 10; i++) {
      reasonA.incrementTaskCount();
      reasonA.incrementTestFail();
    }

    ReasonStats reasonB = reasonSummary.getOrCreateStats("z_reason");
    for (int i = 0; i < 10; i++) {
      reasonB.incrementTaskCount();
      reasonB.incrementTestFail();
    }

    SelectionEvaluation result = evaluator.evaluate(runSummary, reasonSummary);

    assertEquals(SelectionStrength.TOO_WEAK, result.strength());
    assertEquals("a_reason", result.dominantFailureReason());
  }

  @Test
  void testDryRunMode_usesWouldBeExcluded() {
    RunSummary runSummary = new RunSummary();
    runSummary.setTotalTasks(100);
    runSummary.setExecutedTasks(90);
    runSummary.setCompileSuccessCount(85);
    runSummary.setTestPassCount(80);
    runSummary.setDryRun(true);
    runSummary.setWouldBeExcludedCount(30); // 30% would-be-excluded

    ReasonSummary reasonSummary = createReasonSummaryWithHighSuccessRate();

    SelectionEvaluation result = evaluator.evaluate(runSummary, reasonSummary);

    assertEquals(SelectionStrength.TOO_STRICT, result.strength());
    assertEquals(0.30, result.excludedRate(), 0.01);
  }

  @Test
  void testCustomThresholds() {
    // Custom evaluator with stricter thresholds
    SelectionStrengthEvaluator strictEvaluator =
        new SelectionStrengthEvaluator(0.10, 0.20, 0.40, 0.10);

    RunSummary runSummary = createRunSummary(100, 15, 80, 70);
    ReasonSummary reasonSummary = createReasonSummaryWithHighSuccessRate();

    SelectionEvaluation result = strictEvaluator.evaluate(runSummary, reasonSummary);

    // 15% excluded > 10% threshold - should be TOO_STRICT with custom threshold
    assertEquals(SelectionStrength.TOO_STRICT, result.strength());
  }

  @Test
  void testNoExecutedTasks_compileFailRateIsZero() {
    RunSummary runSummary = new RunSummary();
    runSummary.setTotalTasks(10);
    runSummary.setExcludedCount(10);
    runSummary.setExecutedTasks(0);
    runSummary.setCompileSuccessCount(0);
    runSummary.setTestPassCount(0);

    ReasonSummary reasonSummary = new ReasonSummary();
    ReasonStats stats = reasonSummary.getOrCreateStats("low_conf");
    for (int i = 0; i < 10; i++) {
      stats.incrementTaskCount();
      stats.incrementTestPass();
      stats.incrementExcluded();
    }

    SelectionEvaluation result = evaluator.evaluate(runSummary, reasonSummary);

    assertEquals(SelectionStrength.TOO_STRICT, result.strength());
    assertEquals(0.0, result.compileFailRate(), 0.0);
  }

  @Test
  void testNoReasonData_ok() {
    RunSummary runSummary = createRunSummary(100, 10, 80, 70);

    SelectionEvaluation result = evaluator.evaluate(runSummary, null);

    assertEquals(SelectionStrength.OK, result.strength());
  }

  @Test
  void testDefaultThresholds() {
    assertEquals(0.20, evaluator.getMaxExcludedRate());
    assertEquals(0.30, evaluator.getWastedPotentialRatio());
    assertEquals(0.50, evaluator.getFailureConcentration());
    assertEquals(0.15, evaluator.getMinFailureRate());
  }

  // Helper methods

  private RunSummary createRunSummary(int total, int excluded, int compileSuccess, int testPass) {
    RunSummary summary = new RunSummary();
    summary.setTotalTasks(total);
    summary.setExcludedCount(excluded);
    summary.setExecutedTasks(total - excluded);
    summary.setCompileSuccessCount(compileSuccess);
    summary.setTestPassCount(testPass);
    summary.setDryRun(false);
    return summary;
  }

  private ReasonSummary createReasonSummary(boolean includeExcluded) {
    ReasonSummary summary = new ReasonSummary();
    summary.setRunId("test-run");
    summary.setDryRun(false);

    ReasonStats stats = summary.getOrCreateStats("low_conf");
    for (int i = 0; i < 10; i++) {
      stats.incrementTaskCount();
      stats.incrementCompileSuccess();
    }
    for (int i = 0; i < 7; i++) {
      stats.incrementTestPass();
    }

    return summary;
  }

  private ReasonSummary createReasonSummaryWithHighSuccessRate() {
    ReasonSummary summary = new ReasonSummary();
    summary.setRunId("test-run");
    summary.setDryRun(false);

    // Create stats with high success rate (wasted potential)
    ReasonStats stats = summary.getOrCreateStats("low_conf");
    for (int i = 0; i < 30; i++) {
      stats.incrementTaskCount();
      stats.incrementCompileSuccess();
      stats.incrementExcluded();
    }
    // 50% pass rate in this reason
    for (int i = 0; i < 15; i++) {
      stats.incrementTestPass();
    }

    return summary;
  }

  private ReasonSummary createReasonSummaryWithConcentratedFailures() {
    ReasonSummary summary = new ReasonSummary();
    summary.setRunId("test-run");
    summary.setDryRun(false);

    // "low_conf" has 60% of all failures
    ReasonStats lowConfStats = summary.getOrCreateStats("low_conf");
    for (int i = 0; i < 30; i++) {
      lowConfStats.incrementTaskCount();
    }
    for (int i = 0; i < 18; i++) {
      lowConfStats.incrementCompileFail(); // 18 failures
      lowConfStats.incrementTestFail();
    }
    for (int i = 0; i < 12; i++) {
      lowConfStats.incrementCompileSuccess();
      lowConfStats.incrementTestPass();
    }

    // "unresolved" has 40% of failures
    ReasonStats unresolvedStats = summary.getOrCreateStats("unresolved");
    for (int i = 0; i < 20; i++) {
      unresolvedStats.incrementTaskCount();
    }
    for (int i = 0; i < 12; i++) {
      unresolvedStats.incrementCompileFail(); // 12 failures
      unresolvedStats.incrementTestFail();
    }
    for (int i = 0; i < 8; i++) {
      unresolvedStats.incrementCompileSuccess();
      unresolvedStats.incrementTestPass();
    }

    return summary;
  }

  private ReasonSummary createReasonSummaryWithLowWastedPotential() {
    ReasonSummary summary = new ReasonSummary();
    summary.setRunId("test-run");
    summary.setDryRun(false);

    ReasonStats stats = summary.getOrCreateStats("low_conf");
    for (int i = 0; i < 30; i++) {
      stats.incrementTaskCount();
      stats.incrementExcluded();
    }

    return summary;
  }

  private ReasonSummary createReasonSummaryWithDistributedFailures() {
    ReasonSummary summary = new ReasonSummary();
    summary.setRunId("test-run");
    summary.setDryRun(false);

    // Failures distributed across 4 reasons (each ~25%)
    String[] reasons = {"low_conf", "unresolved", "external", "spi_low_conf"};
    for (String reason : reasons) {
      ReasonStats stats = summary.getOrCreateStats(reason);
      for (int i = 0; i < 10; i++) {
        stats.incrementTaskCount();
      }
      for (int i = 0; i < 5; i++) {
        stats.incrementCompileFail(); // 5 failures each
        stats.incrementTestFail();
      }
      for (int i = 0; i < 5; i++) {
        stats.incrementCompileSuccess();
        stats.incrementTestPass();
      }
    }

    return summary;
  }
}
