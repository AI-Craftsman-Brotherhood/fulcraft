package com.craftsmanbro.fulcraft.plugins.reporting.core.rules;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ParameterRecommendation;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonStats;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.RunSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.SelectionEvaluation;
import com.craftsmanbro.fulcraft.plugins.reporting.model.SelectionSettingsRecommendation;
import com.craftsmanbro.fulcraft.plugins.reporting.model.SelectionStrength;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Recommends Selection setting adjustments based on run results.
 *
 * <p>Analyzes RunSummary, ReasonSummary, and SelectionEvaluation to suggest parameter changes with
 * human-readable explanations.
 *
 * <p>Target parameters:
 *
 * <ul>
 *   <li>skip_threshold - Threshold for skipping low-confidence tasks
 *   <li>penalty_unresolved_each - Penalty per unresolved call
 *   <li>penalty_low_confidence - Penalty for low confidence
 *   <li>penalty_external_each - Penalty per external call
 * </ul>
 */
public class SelectionSettingsRecommender {

  // Thresholds for triggering recommendations
  private static final double HIGH_FAIL_RATE_THRESHOLD = 0.30;

  private static final double HIGH_SUCCESS_RATE_THRESHOLD = 0.70;

  private static final String REASON_LOW_CONF = "low_conf";

  private static final String REASON_SPI_LOW_CONF = "spi_low_conf";

  private static final String REASON_UNRESOLVED = "unresolved";

  private static final String REASON_BRANCH_CANDIDATES = "branch_candidates";

  private static final String REASON_EXTERNAL = "external";

  private static final String REASON_EXTERNAL_NOT_FOUND = "external_not_found";

  private static final String REASON_TOO_WEAK = "too_weak";

  private static final String PARAM_SKIP_THRESHOLD = "skip_threshold";

  private static final String PARAM_PENALTY_UNRESOLVED_EACH = "penalty_unresolved_each";

  private static final String PARAM_PENALTY_EXTERNAL_EACH = "penalty_external_each";

  private static final String PARAM_PENALTY_LOW_CONFIDENCE = "penalty_low_confidence";

  private static final String KEY_REASON_LOW_CONF_FAIL =
      "selection.settings.recommendation.reason.low_conf_fail";

  private static final String KEY_REASON_UNRESOLVED_FAIL =
      "selection.settings.recommendation.reason.unresolved_fail";

  private static final String KEY_REASON_BRANCH_CANDIDATES_SUCCESS =
      "selection.settings.recommendation.reason.branch_candidates_success";

  private static final String KEY_REASON_TOO_STRICT =
      "selection.settings.recommendation.reason.too_strict";

  private static final String KEY_REASON_DOMINANT_UNRESOLVED =
      "selection.settings.recommendation.reason.dominant.unresolved";

  private static final String KEY_REASON_DOMINANT_EXTERNAL =
      "selection.settings.recommendation.reason.dominant.external";

  private static final String KEY_REASON_DOMINANT_LOW_CONF =
      "selection.settings.recommendation.reason.dominant.low_conf";

  private static final String KEY_REASON_DOMINANT_OTHER =
      "selection.settings.recommendation.reason.dominant.other";

  private static final String KEY_SUMMARY_CHANGE =
      "selection.settings.recommendation.summary.change";

  private static final String KEY_SUMMARY_CHANGE_SEPARATOR =
      "selection.settings.recommendation.summary.change_separator";

  private static final String KEY_SUMMARY_TEMPLATE_DRY_RUN =
      "selection.settings.recommendation.summary.template.dry_run";

  private static final String KEY_SUMMARY_TEMPLATE_ENFORCE =
      "selection.settings.recommendation.summary.template.enforce";

  private static final String KEY_DEBUG_GENERATING = "report.selection.recommend.debug.generating";

  private static final String KEY_DEBUG_SKIP_THRESHOLD_INCREASE =
      "report.selection.recommend.debug.skip_threshold_increase";

  private static final String KEY_DEBUG_PENALTY_UNRESOLVED_INCREASE =
      "report.selection.recommend.debug.penalty_unresolved_increase";

  private static final String KEY_DEBUG_PENALTY_UNRESOLVED_DECREASE =
      "report.selection.recommend.debug.penalty_unresolved_decrease";

  private static final String KEY_DEBUG_SKIP_THRESHOLD_DECREASE =
      "report.selection.recommend.debug.skip_threshold_decrease";

  private static final String KEY_DEBUG_PENALTY_INCREASE =
      "report.selection.recommend.debug.penalty_increase";

  // Adjustment amounts
  private static final double SKIP_THRESHOLD_ADJUSTMENT = 0.1;

  private static final double PENALTY_ADJUSTMENT = 0.05;

  private static final double PENALTY_MINOR_ADJUSTMENT = 0.02;

  private static final double PENALTY_MAJOR_ADJUSTMENT = 0.1;

  // Current parameter values
  private final double currentSkipThreshold;

  private final double currentPenaltyUnresolvedEach;

  private final double currentPenaltyLowConfidence;

  private final double currentPenaltyExternalEach;

  /** Creates a recommender with default parameter values. */
  public SelectionSettingsRecommender() {
    this(null);
  }

  /**
   * Creates a recommender with current parameter values from config.
   *
   * @param config SelectionRules configuration (nullable, uses defaults if null)
   */
  public SelectionSettingsRecommender(
      final com.craftsmanbro.fulcraft.plugins.analysis.config.SelectionRulesConfig config) {
    if (config != null) {
      this.currentSkipThreshold = config.getSkipThreshold();
      this.currentPenaltyUnresolvedEach = config.getPenaltyUnresolvedEach();
      this.currentPenaltyLowConfidence = config.getPenaltyLowConfidence();
      this.currentPenaltyExternalEach = config.getPenaltyExternalEach();
    } else {
      // Defaults matching SelectionRulesConfig
      this.currentSkipThreshold = 0.5;
      this.currentPenaltyUnresolvedEach = 0.1;
      this.currentPenaltyLowConfidence = 0.4;
      this.currentPenaltyExternalEach = 0.2;
    }
  }

  /**
   * Generates recommendations based on run results and evaluation.
   *
   * @param runSummary Run-level aggregated metrics
   * @param reasonSummary Reason-based aggregated metrics (nullable)
   * @param evaluation SelectionStrength evaluation result
   * @return Recommendation with suggested parameter changes
   */
  public SelectionSettingsRecommendation recommend(
      final RunSummary runSummary,
      final ReasonSummary reasonSummary,
      final SelectionEvaluation evaluation) {
    Objects.requireNonNull(
        runSummary,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "runSummary"));
    Objects.requireNonNull(
        evaluation,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "evaluation"));
    final boolean isDryRun = runSummary.isDryRun();
    final List<ParameterRecommendation> recommendations = new ArrayList<>();
    Logger.debug(MessageSource.getMessage(KEY_DEBUG_GENERATING, evaluation.strength(), isDryRun));
    // Rule 1: Check low_conf / spi_low_conf failure rate
    checkLowConfidenceFailures(reasonSummary, recommendations);
    // Rule 2: Check unresolved failure rate
    checkUnresolvedFailures(reasonSummary, recommendations);
    // Rule 3: Check branch_candidates success rate
    checkBranchCandidatesSuccess(reasonSummary, recommendations);
    // Rule 4: Handle TOO_STRICT evaluation
    checkTooStrictEvaluation(evaluation, recommendations);
    // Rule 5: Handle TOO_WEAK evaluation
    checkTooWeakEvaluation(evaluation, recommendations);
    // Generate summary
    final String summary = generateSummary(isDryRun, recommendations);
    if (recommendations.isEmpty()) {
      Logger.info(MessageSource.getMessage("report.selection.recommend.none"));
      return SelectionSettingsRecommendation.noChanges(isDryRun, evaluation);
    }
    Logger.info(
        MessageSource.getMessage("report.selection.recommend.generated", recommendations.size()));
    return new SelectionSettingsRecommendation(isDryRun, evaluation, recommendations, summary);
  }

  /**
   * Rule 1: low_conf or spi_low_conf causing high compile failure rate.
   *
   * <p>Recommendation: Increase skip_threshold to exclude low-confidence tasks.
   */
  private void checkLowConfidenceFailures(
      final ReasonSummary reasonSummary, final List<ParameterRecommendation> recommendations) {
    if (reasonSummary == null) {
      return;
    }
    final double lowConfFailRate = calculateFailRate(reasonSummary, REASON_LOW_CONF);
    final double spiLowConfFailRate = calculateFailRate(reasonSummary, REASON_SPI_LOW_CONF);
    final double combinedFailRate = Math.max(lowConfFailRate, spiLowConfFailRate);
    if (combinedFailRate > HIGH_FAIL_RATE_THRESHOLD) {
      final String triggerReason =
          lowConfFailRate >= spiLowConfFailRate ? REASON_LOW_CONF : REASON_SPI_LOW_CONF;
      final ParameterRecommendation recommendation =
          increaseBounded(
              PARAM_SKIP_THRESHOLD,
              currentSkipThreshold,
              SKIP_THRESHOLD_ADJUSTMENT,
              1.0,
              MessageSource.getMessage(
                  KEY_REASON_LOW_CONF_FAIL, triggerReason, formatPercent(combinedFailRate)),
              triggerReason);
      addIfChanged(recommendations, recommendation);
      Logger.debug(
          MessageSource.getMessage(
              KEY_DEBUG_SKIP_THRESHOLD_INCREASE, triggerReason, formatDecimal(combinedFailRate)));
    }
  }

  /**
   * Rule 2: unresolved causing high compile failure rate.
   *
   * <p>Recommendation: Increase penalty_unresolved_each.
   */
  private void checkUnresolvedFailures(
      final ReasonSummary reasonSummary, final List<ParameterRecommendation> recommendations) {
    if (reasonSummary == null) {
      return;
    }
    final double unresolvedFailRate = calculateFailRate(reasonSummary, REASON_UNRESOLVED);
    if (unresolvedFailRate > HIGH_FAIL_RATE_THRESHOLD) {
      recommendations.add(
          ParameterRecommendation.increase(
              PARAM_PENALTY_UNRESOLVED_EACH,
              currentPenaltyUnresolvedEach,
              PENALTY_ADJUSTMENT,
              MessageSource.getMessage(
                  KEY_REASON_UNRESOLVED_FAIL, formatPercent(unresolvedFailRate)),
              REASON_UNRESOLVED));
      Logger.debug(
          MessageSource.getMessage(
              KEY_DEBUG_PENALTY_UNRESOLVED_INCREASE, formatDecimal(unresolvedFailRate)));
    }
  }

  /**
   * Rule 3: branch_candidates with high success rate.
   *
   * <p>Recommendation: Decrease penalty_unresolved_each (branch candidates may be penalized too
   * harshly).
   */
  private void checkBranchCandidatesSuccess(
      final ReasonSummary reasonSummary, final List<ParameterRecommendation> recommendations) {
    if (reasonSummary == null) {
      return;
    }
    final double branchSuccessRate = calculateSuccessRate(reasonSummary, REASON_BRANCH_CANDIDATES);
    if (branchSuccessRate > HIGH_SUCCESS_RATE_THRESHOLD) {
      // Don't add conflicting recommendation if we already recommended increase
      final boolean hasIncreaseRecommendation =
          recommendations.stream()
              .anyMatch(
                  r -> PARAM_PENALTY_UNRESOLVED_EACH.equals(r.parameterName()) && r.isIncrease());
      if (!hasIncreaseRecommendation) {
        recommendations.add(
            ParameterRecommendation.decrease(
                PARAM_PENALTY_UNRESOLVED_EACH,
                currentPenaltyUnresolvedEach,
                PENALTY_MINOR_ADJUSTMENT,
                MessageSource.getMessage(
                    KEY_REASON_BRANCH_CANDIDATES_SUCCESS, formatPercent(branchSuccessRate)),
                REASON_BRANCH_CANDIDATES));
        Logger.debug(
            MessageSource.getMessage(
                KEY_DEBUG_PENALTY_UNRESOLVED_DECREASE, formatDecimal(branchSuccessRate)));
      }
    }
  }

  /**
   * Rule 4: TOO_STRICT evaluation.
   *
   * <p>Recommendation: Decrease skip_threshold to include more tasks.
   */
  private void checkTooStrictEvaluation(
      final SelectionEvaluation evaluation, final List<ParameterRecommendation> recommendations) {
    if (evaluation.strength() != SelectionStrength.TOO_STRICT) {
      return;
    }
    // Don't add conflicting recommendation if we already recommended increase
    final boolean hasIncreaseRecommendation =
        recommendations.stream()
            .anyMatch(r -> PARAM_SKIP_THRESHOLD.equals(r.parameterName()) && r.isIncrease());
    if (!hasIncreaseRecommendation) {
      final ParameterRecommendation recommendation =
          ParameterRecommendation.decrease(
              PARAM_SKIP_THRESHOLD,
              currentSkipThreshold,
              SKIP_THRESHOLD_ADJUSTMENT,
              MessageSource.getMessage(
                  KEY_REASON_TOO_STRICT, formatPercent(evaluation.excludedRate())),
              "too_strict");
      addIfChanged(recommendations, recommendation);
      Logger.debug(MessageSource.getMessage(KEY_DEBUG_SKIP_THRESHOLD_DECREASE));
    }
  }

  /**
   * Rule 5: TOO_WEAK evaluation.
   *
   * <p>Recommendation: Increase penalty_low_confidence to filter out more risky tasks.
   */
  private void checkTooWeakEvaluation(
      final SelectionEvaluation evaluation, final List<ParameterRecommendation> recommendations) {
    if (evaluation.strength() != SelectionStrength.TOO_WEAK) {
      return;
    }
    final String dominantReason = evaluation.dominantFailureReason();
    recommendations.add(recommendForDominantReason(dominantReason));
    Logger.debug(MessageSource.getMessage(KEY_DEBUG_PENALTY_INCREASE));
  }

  /** Calculate compile failure rate for a specific reason. */
  private double calculateFailRate(final ReasonSummary reasonSummary, final String reasonKey) {
    final Map<String, ReasonStats> stats = reasonSummary.getReasonStats();
    if (!stats.containsKey(reasonKey)) {
      return 0.0;
    }
    final ReasonStats reasonStats = stats.get(reasonKey);
    return reasonStats.getCompileFailRate();
  }

  /** Calculate test pass (success) rate for a specific reason. */
  private double calculateSuccessRate(final ReasonSummary reasonSummary, final String reasonKey) {
    final Map<String, ReasonStats> stats = reasonSummary.getReasonStats();
    if (!stats.containsKey(reasonKey)) {
      return 0.0;
    }
    final ReasonStats reasonStats = stats.get(reasonKey);
    return reasonStats.getTaskCount() == 0
        ? 0.0
        : (double) reasonStats.getTestPassCount() / reasonStats.getTaskCount();
  }

  private ParameterRecommendation recommendForDominantReason(final String dominantReason) {
    final String normalized = dominantReason != null ? dominantReason : REASON_TOO_WEAK;
    return switch (normalized) {
      case REASON_UNRESOLVED ->
          ParameterRecommendation.increase(
              PARAM_PENALTY_UNRESOLVED_EACH,
              currentPenaltyUnresolvedEach,
              PENALTY_MAJOR_ADJUSTMENT,
              MessageSource.getMessage(KEY_REASON_DOMINANT_UNRESOLVED),
              REASON_UNRESOLVED);
      case REASON_EXTERNAL, REASON_EXTERNAL_NOT_FOUND ->
          ParameterRecommendation.increase(
              PARAM_PENALTY_EXTERNAL_EACH,
              currentPenaltyExternalEach,
              PENALTY_MAJOR_ADJUSTMENT,
              MessageSource.getMessage(KEY_REASON_DOMINANT_EXTERNAL),
              normalized);
      case REASON_LOW_CONF, REASON_SPI_LOW_CONF ->
          ParameterRecommendation.increase(
              PARAM_PENALTY_LOW_CONFIDENCE,
              currentPenaltyLowConfidence,
              PENALTY_MAJOR_ADJUSTMENT,
              MessageSource.getMessage(KEY_REASON_DOMINANT_LOW_CONF),
              normalized);
      default ->
          ParameterRecommendation.increase(
              PARAM_PENALTY_LOW_CONFIDENCE,
              currentPenaltyLowConfidence,
              PENALTY_MAJOR_ADJUSTMENT,
              MessageSource.getMessage(KEY_REASON_DOMINANT_OTHER, normalized),
              normalized);
    };
  }

  private ParameterRecommendation increaseBounded(
      final String name,
      final double current,
      final double increase,
      final double maxValue,
      final String reason,
      final String trigger) {
    final double recommendedValue = Math.min(maxValue, current + increase);
    return new ParameterRecommendation(name, current, recommendedValue, reason, trigger);
  }

  private void addIfChanged(
      final List<ParameterRecommendation> recommendations,
      final ParameterRecommendation recommendation) {
    if (recommendation != null && Double.compare(recommendation.change(), 0.0) != 0) {
      recommendations.add(recommendation);
    }
  }

  /** Generate human-readable summary of recommendations. */
  private String generateSummary(
      final boolean isDryRun, final List<ParameterRecommendation> recommendations) {
    if (recommendations.isEmpty()) {
      return isDryRun
          ? MessageSource.getMessage("selection.settings.recommendation.no_changes.dry_run")
          : MessageSource.getMessage("selection.settings.recommendation.no_changes.enforce");
    }
    final List<String> changes = new ArrayList<>();
    for (final ParameterRecommendation rec : recommendations) {
      changes.add(
          MessageSource.getMessage(
              KEY_SUMMARY_CHANGE,
              rec.parameterName(),
              formatDecimal(rec.currentValue()),
              formatDecimal(rec.recommendedValue())));
    }
    final String separator = MessageSource.getMessage(KEY_SUMMARY_CHANGE_SEPARATOR);
    final String joinedChanges = String.join(separator, changes);
    final String summaryKey =
        isDryRun ? KEY_SUMMARY_TEMPLATE_DRY_RUN : KEY_SUMMARY_TEMPLATE_ENFORCE;
    return MessageSource.getMessage(summaryKey, joinedChanges);
  }

  private String formatPercent(final double ratio) {
    return formatWithLocale("%.0f", ratio * 100);
  }

  private String formatDecimal(final double value) {
    return formatWithLocale("%.2f", value);
  }

  private String formatWithLocale(final String pattern, final double value) {
    Locale locale = MessageSource.getLocale();
    if (locale == null) {
      locale = Locale.ROOT;
    }
    return String.format(locale, pattern, value);
  }

  // Getters for testing
  public double getCurrentSkipThreshold() {
    return currentSkipThreshold;
  }

  public double getCurrentPenaltyUnresolvedEach() {
    return currentPenaltyUnresolvedEach;
  }

  public double getCurrentPenaltyLowConfidence() {
    return currentPenaltyLowConfidence;
  }
}
