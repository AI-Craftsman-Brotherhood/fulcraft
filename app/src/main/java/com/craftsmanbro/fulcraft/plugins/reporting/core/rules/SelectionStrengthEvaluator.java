package com.craftsmanbro.fulcraft.plugins.reporting.core.rules;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonStats;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.RunSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.SelectionEvaluation;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates the effectiveness of Selection rules based on run and reason summaries.
 *
 * <p>Determines if Selection is:
 *
 * <ul>
 *   <li>TOO_STRICT: Excluding too many tasks that could have succeeded
 *   <li>TOO_WEAK: Not excluding enough tasks that are likely to fail
 *   <li>OK: Appropriately calibrated
 * </ul>
 */
public class SelectionStrengthEvaluator {

  // Default thresholds (configurable via constructor)
  public static final double DEFAULT_MAX_EXCLUDED_RATE = 0.20;

  public static final double DEFAULT_WASTED_POTENTIAL_RATIO = 0.30;

  public static final double DEFAULT_FAILURE_CONCENTRATION = 0.50;

  public static final double DEFAULT_MIN_FAILURE_RATE = 0.15;

  private final double maxExcludedRate;

  private final double wastedPotentialRatio;

  private final double failureConcentration;

  private final double minFailureRate;

  /** Creates an evaluator with default thresholds. */
  public SelectionStrengthEvaluator() {
    this(
        DEFAULT_MAX_EXCLUDED_RATE,
        DEFAULT_WASTED_POTENTIAL_RATIO,
        DEFAULT_FAILURE_CONCENTRATION,
        DEFAULT_MIN_FAILURE_RATE);
  }

  /**
   * Creates an evaluator with custom thresholds.
   *
   * @param maxExcludedRate Maximum acceptable excluded rate (default: 0.20)
   * @param wastedPotentialRatio Threshold for wasted potential ratio (default: 0.30)
   * @param failureConcentration Threshold for failure concentration (default: 0.50)
   * @param minFailureRate Minimum failure rate to consider TOO_WEAK (default: 0.15)
   */
  public SelectionStrengthEvaluator(
      final double maxExcludedRate,
      final double wastedPotentialRatio,
      final double failureConcentration,
      final double minFailureRate) {
    this.maxExcludedRate = maxExcludedRate;
    this.wastedPotentialRatio = wastedPotentialRatio;
    this.failureConcentration = failureConcentration;
    this.minFailureRate = minFailureRate;
  }

  /**
   * Evaluates Selection strength based on run and reason summaries.
   *
   * @param runSummary Run-level aggregated metrics
   * @param reasonSummary Reason-based aggregated metrics
   * @return Evaluation result with strength judgment and reasoning
   */
  public SelectionEvaluation evaluate(
      final RunSummary runSummary, final ReasonSummary reasonSummary) {
    Objects.requireNonNull(
        runSummary, MessageSource.getMessage("report.common.error.argument_null", "runSummary"));
    // Handle empty data
    if (runSummary.getTotalTasks() == 0) {
      Logger.info(MessageSource.getMessage("report.selection.eval.empty"));
      return SelectionEvaluation.ok(0.0, 0.0);
    }
    // Calculate key metrics
    final double excludedRate = calculateExcludedRate(runSummary);
    final double compileFailRate = calculateCompileFailRate(runSummary);
    Logger.debug(
        MessageSource.getMessage(
            "report.selection.eval.debug.metrics", excludedRate, compileFailRate));
    // Check TOO_STRICT: high exclusion rate with wasted potential
    final SelectionEvaluation tooStrictResult =
        checkTooStrict(reasonSummary, excludedRate, compileFailRate);
    if (tooStrictResult != null) {
      Logger.info(
          MessageSource.getMessage("report.selection.eval.too_strict", tooStrictResult.reason()));
      return tooStrictResult;
    }
    // Check TOO_WEAK: failure concentration on specific reasons
    final SelectionEvaluation tooWeakResult =
        checkTooWeak(runSummary, reasonSummary, excludedRate, compileFailRate);
    if (tooWeakResult != null) {
      Logger.info(
          MessageSource.getMessage("report.selection.eval.too_weak", tooWeakResult.reason()));
      return tooWeakResult;
    }
    // Default: OK
    Logger.info(MessageSource.getMessage("report.selection.eval.ok"));
    return SelectionEvaluation.ok(excludedRate, compileFailRate);
  }

  /**
   * Check if Selection is TOO_STRICT.
   *
   * <p>Condition: excludedRate > threshold AND excluded tasks had high success potential
   */
  private SelectionEvaluation checkTooStrict(
      final ReasonSummary reasonSummary, final double excludedRate, final double compileFailRate) {
    if (excludedRate <= maxExcludedRate) {
      // Not exceeding threshold
      return null;
    }
    // Calculate wasted potential: estimate how many excluded tasks could have
    // succeeded
    final double wastedPotential = estimateWastedPotential(reasonSummary);
    if (wastedPotential >= wastedPotentialRatio) {
      return SelectionEvaluation.tooStrict(
          excludedRate, maxExcludedRate, wastedPotential, compileFailRate);
    }
    // High exclusion but low wasted potential - still concerning but not TOO_STRICT
    return null;
  }

  /**
   * Check if Selection is TOO_WEAK.
   *
   * <p>Condition: failure rate is high AND failures concentrate on specific reasons
   */
  private SelectionEvaluation checkTooWeak(
      final RunSummary runSummary,
      final ReasonSummary reasonSummary,
      final double excludedRate,
      final double compileFailRate) {
    if (reasonSummary == null || reasonSummary.getReasonStats().isEmpty()) {
      // No reason data to analyze
      return null;
    }
    // Check if failure rate is significant
    final double overallFailRate = 1.0 - runSummary.getPassRate();
    if (overallFailRate < minFailureRate) {
      // Low failure rate, no need to strengthen
      return null;
    }
    // Find the dominant failure reason
    String dominantReason = null;
    double maxConcentration = 0.0;
    int totalFailures = 0;
    for (final Map.Entry<String, ReasonStats> entry : reasonSummary.getReasonStats().entrySet()) {
      final ReasonStats stats = entry.getValue();
      final int failures = stats.getTestFailCount();
      totalFailures += failures;
    }
    if (totalFailures == 0) {
      return null;
    }
    for (final Map.Entry<String, ReasonStats> entry : reasonSummary.getReasonStats().entrySet()) {
      final ReasonStats stats = entry.getValue();
      final int failures = stats.getTestFailCount();
      final double concentration = (double) failures / totalFailures;
      if (concentration > maxConcentration) {
        maxConcentration = concentration;
        dominantReason = entry.getKey();
      }
    }
    if (maxConcentration >= failureConcentration && dominantReason != null) {
      return SelectionEvaluation.tooWeak(
          excludedRate, compileFailRate, dominantReason, maxConcentration);
    }
    return null;
  }

  /**
   * Estimate wasted potential: ratio of excluded tasks that could have succeeded.
   *
   * <p>Uses reason-level success rates as proxy for excluded task success potential.
   */
  private double estimateWastedPotential(final ReasonSummary reasonSummary) {
    if (reasonSummary == null || reasonSummary.getReasonStats().isEmpty()) {
      return 0.0;
    }
    int totalExcluded = 0;
    int potentialSuccess = 0;
    for (final ReasonStats stats : reasonSummary.getReasonStats().values()) {
      final int excluded = stats.getExcludedCount() + stats.getWouldBeExcludedCount();
      if (excluded == 0) {
        continue;
      }
      totalExcluded += excluded;
      // Estimate success potential based on reason's compile/pass rate
      final double successRate =
          stats.getTaskCount() > 0 ? (double) stats.getTestPassCount() / stats.getTaskCount() : 0.0;
      // Weight by the reason's success rate
      potentialSuccess += (int) (excluded * successRate);
    }
    return totalExcluded == 0 ? 0.0 : (double) potentialSuccess / totalExcluded;
  }

  private double calculateExcludedRate(final RunSummary runSummary) {
    if (runSummary.isDryRun()) {
      return runSummary.getWouldBeExcludedRate();
    }
    return runSummary.getExcludedRate();
  }

  private double calculateCompileFailRate(final RunSummary runSummary) {
    final int executed = runSummary.getExecutedTasks();
    if (executed == 0) {
      return 0.0;
    }
    return 1.0 - runSummary.getCompileRate();
  }

  // Getters for testing
  public double getMaxExcludedRate() {
    return maxExcludedRate;
  }

  public double getWastedPotentialRatio() {
    return wastedPotentialRatio;
  }

  public double getFailureConcentration() {
    return failureConcentration;
  }

  public double getMinFailureRate() {
    return minFailureRate;
  }
}
