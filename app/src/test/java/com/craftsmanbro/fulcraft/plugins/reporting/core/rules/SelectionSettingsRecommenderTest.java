package com.craftsmanbro.fulcraft.plugins.reporting.core.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ParameterRecommendation;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonStats;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.RunSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.SelectionEvaluation;
import com.craftsmanbro.fulcraft.plugins.reporting.model.SelectionSettingsRecommendation;
import com.craftsmanbro.fulcraft.plugins.reporting.model.SelectionStrength;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for SelectionSettingsRecommender. */
class SelectionSettingsRecommenderTest {

  private SelectionSettingsRecommender recommender;

  @BeforeEach
  void setUp() {
    recommender = new SelectionSettingsRecommender();
  }

  @Test
  void testNullRunSummary_throwsException() {
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.1, 0.1);
    assertThrows(NullPointerException.class, () -> recommender.recommend(null, null, evaluation));
  }

  @Test
  void testNullEvaluation_throwsException() {
    RunSummary runSummary = createRunSummary(10, 1, 8, 7);
    assertThrows(NullPointerException.class, () -> recommender.recommend(runSummary, null, null));
  }

  @Test
  void testLowConfHighFailRate_recommendsHigherSkipThreshold() {
    // Given: low_conf reason with 40% compile fail rate (> 30% threshold)
    RunSummary runSummary = createRunSummary(100, 10, 80, 60);
    ReasonSummary reasonSummary = createReasonSummaryWithLowConfFailures();
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.1, 0.2);

    // When
    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    // Then
    assertTrue(result.hasRecommendations());
    Optional<ParameterRecommendation> skipThresholdRec =
        result.recommendations().stream()
            .filter(r -> "skip_threshold".equals(r.parameterName()))
            .findFirst();
    assertTrue(skipThresholdRec.isPresent());
    assertEquals("low_conf", skipThresholdRec.get().triggerReason());
    assertTrue(skipThresholdRec.get().recommendedValue() > skipThresholdRec.get().currentValue());
  }

  @Test
  void testSpiLowConfHighFailRate_usesSpiLowConfAsTrigger() {
    RunSummary runSummary = createRunSummary(100, 10, 80, 60);
    ReasonSummary reasonSummary = createReasonSummaryWithSpiLowConfFailures();
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.1, 0.2);

    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    Optional<ParameterRecommendation> skipThresholdRec =
        result.recommendations().stream()
            .filter(r -> "skip_threshold".equals(r.parameterName()))
            .findFirst();
    assertTrue(skipThresholdRec.isPresent());
    assertEquals("spi_low_conf", skipThresholdRec.get().triggerReason());
  }

  @Test
  void testSkipThresholdIncrease_isBoundedAtOne() {
    Config.SelectionRules config = new Config.SelectionRules();
    config.setSkipThreshold(0.95);
    SelectionSettingsRecommender boundedRecommender = new SelectionSettingsRecommender(config);

    RunSummary runSummary = createRunSummary(100, 10, 80, 60);
    ReasonSummary reasonSummary = createReasonSummaryWithLowConfFailures();
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.1, 0.2);

    SelectionSettingsRecommendation result =
        boundedRecommender.recommend(runSummary, reasonSummary, evaluation);

    Optional<ParameterRecommendation> skipThresholdRec =
        result.recommendations().stream()
            .filter(r -> "skip_threshold".equals(r.parameterName()))
            .findFirst();
    assertTrue(skipThresholdRec.isPresent());
    assertEquals(1.0, skipThresholdRec.get().recommendedValue(), 0.0);
  }

  @Test
  void testSkipThresholdAlreadyOne_noChangeRecommendationIsAdded() {
    Config.SelectionRules config = new Config.SelectionRules();
    config.setSkipThreshold(1.0);
    SelectionSettingsRecommender boundedRecommender = new SelectionSettingsRecommender(config);

    RunSummary runSummary = createRunSummary(100, 10, 80, 60);
    ReasonSummary reasonSummary = createReasonSummaryWithLowConfFailures();
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.1, 0.2);

    SelectionSettingsRecommendation result =
        boundedRecommender.recommend(runSummary, reasonSummary, evaluation);

    assertFalse(result.hasRecommendations());
    assertTrue(result.recommendations().isEmpty());
  }

  @Test
  void testUnresolvedHighFailRate_recommendsHigherPenalty() {
    // Given: unresolved reason with 35% fail rate
    RunSummary runSummary = createRunSummary(100, 5, 85, 70);
    ReasonSummary reasonSummary = createReasonSummaryWithUnresolvedFailures();
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.05, 0.15);

    // When
    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    // Then
    assertTrue(result.hasRecommendations());
    Optional<ParameterRecommendation> penaltyRec =
        result.recommendations().stream()
            .filter(r -> "penalty_unresolved_each".equals(r.parameterName()))
            .findFirst();
    assertTrue(penaltyRec.isPresent());
    assertEquals("unresolved", penaltyRec.get().triggerReason());
    assertTrue(penaltyRec.get().recommendedValue() > penaltyRec.get().currentValue());
  }

  @Test
  void testBranchCandidatesHighSuccessRate_recommendsLowerPenalty() {
    // Given: branch_candidates with 80% success rate
    RunSummary runSummary = createRunSummary(100, 5, 95, 85);
    ReasonSummary reasonSummary = createReasonSummaryWithBranchCandidatesSuccess();
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.05, 0.05);

    // When
    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    // Then
    assertTrue(result.hasRecommendations());
    Optional<ParameterRecommendation> penaltyRec =
        result.recommendations().stream()
            .filter(r -> "penalty_unresolved_each".equals(r.parameterName()))
            .findFirst();
    assertTrue(penaltyRec.isPresent());
    assertEquals("branch_candidates", penaltyRec.get().triggerReason());
    assertTrue(penaltyRec.get().recommendedValue() < penaltyRec.get().currentValue());
  }

  @Test
  void testTooStrict_recommendsLowerSkipThreshold() {
    // Given: TOO_STRICT evaluation
    RunSummary runSummary = createRunSummary(100, 30, 60, 50);
    ReasonSummary reasonSummary = new ReasonSummary(); // Empty, no reason-based recommendations
    SelectionEvaluation evaluation = SelectionEvaluation.tooStrict(0.30, 0.20, 0.40, 0.15);

    // When
    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    // Then
    assertTrue(result.hasRecommendations());
    Optional<ParameterRecommendation> skipRec =
        result.recommendations().stream()
            .filter(r -> "skip_threshold".equals(r.parameterName()))
            .findFirst();
    assertTrue(skipRec.isPresent());
    assertEquals("too_strict", skipRec.get().triggerReason());
    assertTrue(skipRec.get().recommendedValue() < skipRec.get().currentValue());
  }

  @Test
  void testTooStrictWithExistingSkipIncrease_doesNotAddConflictingDecrease() {
    RunSummary runSummary = createRunSummary(100, 30, 60, 50);
    ReasonSummary reasonSummary = createReasonSummaryWithLowConfFailures();
    SelectionEvaluation evaluation = SelectionEvaluation.tooStrict(0.30, 0.20, 0.40, 0.15);

    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    long skipThresholdRecommendations =
        result.recommendations().stream()
            .filter(r -> "skip_threshold".equals(r.parameterName()))
            .count();
    assertEquals(1, skipThresholdRecommendations);
    ParameterRecommendation recommendation =
        result.recommendations().stream()
            .filter(r -> "skip_threshold".equals(r.parameterName()))
            .findFirst()
            .orElseThrow();
    assertTrue(recommendation.isIncrease());
  }

  @Test
  void testTooWeak_recommendsHigherPenalty() {
    // Given: TOO_WEAK evaluation with dominant reason
    RunSummary runSummary = createRunSummary(100, 5, 60, 40);
    ReasonSummary reasonSummary = new ReasonSummary();
    SelectionEvaluation evaluation = SelectionEvaluation.tooWeak(0.05, 0.40, "low_conf", 0.65);

    // When
    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    // Then
    assertTrue(result.hasRecommendations());
    Optional<ParameterRecommendation> penaltyRec =
        result.recommendations().stream()
            .filter(r -> "penalty_low_confidence".equals(r.parameterName()))
            .findFirst();
    assertTrue(penaltyRec.isPresent());
    assertEquals("low_conf", penaltyRec.get().triggerReason());
    assertTrue(penaltyRec.get().recommendedValue() > penaltyRec.get().currentValue());
  }

  @Test
  void testTooWeakWithUnresolvedDominantReason_recommendsUnresolvedPenalty() {
    RunSummary runSummary = createRunSummary(100, 5, 60, 40);
    ReasonSummary reasonSummary = new ReasonSummary();
    SelectionEvaluation evaluation = SelectionEvaluation.tooWeak(0.05, 0.40, "unresolved", 0.65);

    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    Optional<ParameterRecommendation> penaltyRec =
        result.recommendations().stream()
            .filter(r -> "penalty_unresolved_each".equals(r.parameterName()))
            .findFirst();
    assertTrue(penaltyRec.isPresent());
    assertEquals("unresolved", penaltyRec.get().triggerReason());
  }

  @Test
  void testTooWeakWithExternalDominantReason_recommendsExternalPenalty() {
    RunSummary runSummary = createRunSummary(100, 5, 60, 40);
    ReasonSummary reasonSummary = new ReasonSummary();
    SelectionEvaluation evaluation = SelectionEvaluation.tooWeak(0.05, 0.40, "external", 0.65);

    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    Optional<ParameterRecommendation> penaltyRec =
        result.recommendations().stream()
            .filter(r -> "penalty_external_each".equals(r.parameterName()))
            .findFirst();
    assertTrue(penaltyRec.isPresent());
    assertEquals("external", penaltyRec.get().triggerReason());
  }

  @Test
  void testTooWeakWithExternalNotFoundDominantReason_recommendsExternalPenalty() {
    RunSummary runSummary = createRunSummary(100, 5, 60, 40);
    ReasonSummary reasonSummary = new ReasonSummary();
    SelectionEvaluation evaluation =
        SelectionEvaluation.tooWeak(0.05, 0.40, "external_not_found", 0.65);

    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    Optional<ParameterRecommendation> penaltyRec =
        result.recommendations().stream()
            .filter(r -> "penalty_external_each".equals(r.parameterName()))
            .findFirst();
    assertTrue(penaltyRec.isPresent());
    assertEquals("external_not_found", penaltyRec.get().triggerReason());
  }

  @Test
  void testTooWeakWithUnknownDominantReason_fallsBackToLowConfidencePenalty() {
    RunSummary runSummary = createRunSummary(100, 5, 60, 40);
    ReasonSummary reasonSummary = new ReasonSummary();
    SelectionEvaluation evaluation = SelectionEvaluation.tooWeak(0.05, 0.40, "custom_reason", 0.65);

    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    Optional<ParameterRecommendation> penaltyRec =
        result.recommendations().stream()
            .filter(r -> "penalty_low_confidence".equals(r.parameterName()))
            .findFirst();
    assertTrue(penaltyRec.isPresent());
    assertEquals("custom_reason", penaltyRec.get().triggerReason());
  }

  @Test
  void testTooWeakWithNullDominantReason_usesTooWeakFallbackTrigger() {
    RunSummary runSummary = createRunSummary(100, 5, 60, 40);
    ReasonSummary reasonSummary = new ReasonSummary();
    SelectionEvaluation evaluation =
        new SelectionEvaluation(SelectionStrength.TOO_WEAK, "fallback", 0.05, 0.40, 0.0, null);

    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    Optional<ParameterRecommendation> penaltyRec =
        result.recommendations().stream()
            .filter(r -> "penalty_low_confidence".equals(r.parameterName()))
            .findFirst();
    assertTrue(penaltyRec.isPresent());
    assertEquals("too_weak", penaltyRec.get().triggerReason());
  }

  @Test
  void testNoIssues_noRecommendations() {
    // Given: Good metrics, no issues
    RunSummary runSummary = createRunSummary(100, 5, 95, 90);
    ReasonSummary reasonSummary = createReasonSummaryWithGoodMetrics();
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.05, 0.05);

    // When
    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    // Then
    assertFalse(result.hasRecommendations());
    assertEquals(SelectionStrength.OK, result.evaluation().strength());
    assertNotNull(result.summary());
  }

  @Test
  void testDryRunMode_summaryIncludesTrialSuggestion() {
    // Given: dry-run mode with high fail rate
    RunSummary runSummary = createRunSummary(100, 10, 60, 50);
    runSummary.setDryRun(true);
    ReasonSummary reasonSummary = createReasonSummaryWithLowConfFailures();
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.1, 0.4);

    // When
    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    // Then
    assertTrue(result.isDryRun());
    assertTrue(result.hasRecommendations());
    assertTrue(result.summary().contains("skip_threshold"));
  }

  @Test
  void testConfiguredValues_usesCurrentValues() {
    // Given: Custom config values
    Config.SelectionRules config = new Config.SelectionRules();
    config.setSkipThreshold(0.7);
    config.setPenaltyUnresolvedEach(0.15);
    config.setPenaltyLowConfidence(0.5);

    SelectionSettingsRecommender customRecommender = new SelectionSettingsRecommender(config);

    // Then
    assertEquals(0.7, customRecommender.getCurrentSkipThreshold());
    assertEquals(0.15, customRecommender.getCurrentPenaltyUnresolvedEach());
    assertEquals(0.5, customRecommender.getCurrentPenaltyLowConfidence());
  }

  @Test
  void testConflictingRules_prioritizesIncrease() {
    // Given: Both unresolved high fail rate AND branch_candidates high success
    // Should not recommend both increase and decrease for same parameter
    RunSummary runSummary = createRunSummary(100, 5, 70, 60);
    ReasonSummary reasonSummary = createReasonSummaryWithConflictingSignals();
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.05, 0.30);

    // When
    SelectionSettingsRecommendation result =
        recommender.recommend(runSummary, reasonSummary, evaluation);

    // Then
    long penaltyCount =
        result.recommendations().stream()
            .filter(r -> "penalty_unresolved_each".equals(r.parameterName()))
            .count();
    // Should only have one recommendation for this parameter (increase takes
    // priority)
    assertEquals(1, penaltyCount);
  }

  // === Helper Methods ===

  private RunSummary createRunSummary(int total, int excluded, int compileSuccess, int testPass) {
    RunSummary summary = new RunSummary();
    summary.setTotalTasks(total);
    summary.setExcludedCount(excluded);
    summary.setExecutedTasks(total - excluded);
    summary.setCompileSuccessCount(compileSuccess);
    summary.setTestPassCount(testPass);
    return summary;
  }

  private ReasonSummary createReasonSummaryWithLowConfFailures() {
    ReasonSummary summary = new ReasonSummary();

    // low_conf with 40% fail rate (12 fail out of 30 tasks)
    ReasonStats lowConfStats = summary.getOrCreateStats("low_conf");
    for (int i = 0; i < 30; i++) {
      lowConfStats.incrementTaskCount();
    }
    for (int i = 0; i < 12; i++) {
      lowConfStats.incrementCompileFail();
    }
    for (int i = 0; i < 18; i++) {
      lowConfStats.incrementCompileSuccess();
    }

    return summary;
  }

  private ReasonSummary createReasonSummaryWithUnresolvedFailures() {
    ReasonSummary summary = new ReasonSummary();

    // unresolved with 35% fail rate
    ReasonStats stats = summary.getOrCreateStats("unresolved");
    for (int i = 0; i < 20; i++) {
      stats.incrementTaskCount();
    }
    for (int i = 0; i < 7; i++) {
      stats.incrementCompileFail();
    }
    for (int i = 0; i < 13; i++) {
      stats.incrementCompileSuccess();
    }

    return summary;
  }

  private ReasonSummary createReasonSummaryWithSpiLowConfFailures() {
    ReasonSummary summary = new ReasonSummary();

    ReasonStats lowConfStats = summary.getOrCreateStats("low_conf");
    for (int i = 0; i < 20; i++) {
      lowConfStats.incrementTaskCount();
      lowConfStats.incrementCompileSuccess();
    }

    // spi_low_conf with 45% fail rate
    ReasonStats spiStats = summary.getOrCreateStats("spi_low_conf");
    for (int i = 0; i < 20; i++) {
      spiStats.incrementTaskCount();
    }
    for (int i = 0; i < 9; i++) {
      spiStats.incrementCompileFail();
    }
    for (int i = 0; i < 11; i++) {
      spiStats.incrementCompileSuccess();
    }

    return summary;
  }

  private ReasonSummary createReasonSummaryWithBranchCandidatesSuccess() {
    ReasonSummary summary = new ReasonSummary();

    // branch_candidates with 80% success rate
    ReasonStats stats = summary.getOrCreateStats("branch_candidates");
    for (int i = 0; i < 25; i++) {
      stats.incrementTaskCount();
    }
    for (int i = 0; i < 25; i++) {
      stats.incrementCompileSuccess();
    }
    for (int i = 0; i < 20; i++) {
      stats.incrementTestPass();
    }

    return summary;
  }

  private ReasonSummary createReasonSummaryWithGoodMetrics() {
    ReasonSummary summary = new ReasonSummary();

    // low_conf with only 10% fail rate (good)
    ReasonStats stats = summary.getOrCreateStats("low_conf");
    for (int i = 0; i < 20; i++) {
      stats.incrementTaskCount();
    }
    for (int i = 0; i < 2; i++) {
      stats.incrementCompileFail();
    }
    for (int i = 0; i < 18; i++) {
      stats.incrementCompileSuccess();
    }

    return summary;
  }

  private ReasonSummary createReasonSummaryWithConflictingSignals() {
    ReasonSummary summary = new ReasonSummary();

    // unresolved with 35% fail rate (triggers increase)
    ReasonStats unresolvedStats = summary.getOrCreateStats("unresolved");
    for (int i = 0; i < 20; i++) {
      unresolvedStats.incrementTaskCount();
    }
    for (int i = 0; i < 7; i++) {
      unresolvedStats.incrementCompileFail();
    }
    for (int i = 0; i < 13; i++) {
      unresolvedStats.incrementCompileSuccess();
    }

    // branch_candidates with 80% success rate (would trigger decrease)
    ReasonStats branchStats = summary.getOrCreateStats("branch_candidates");
    for (int i = 0; i < 20; i++) {
      branchStats.incrementTaskCount();
    }
    for (int i = 0; i < 20; i++) {
      branchStats.incrementCompileSuccess();
    }
    for (int i = 0; i < 16; i++) {
      branchStats.incrementTestPass();
    }

    return summary;
  }
}
