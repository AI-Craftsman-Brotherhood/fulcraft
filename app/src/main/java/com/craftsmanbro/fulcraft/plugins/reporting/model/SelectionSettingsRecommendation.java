package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Complete recommendation for Selection settings adjustments.
 *
 * <p>Combines the evaluation result with specific parameter recommendations and a human-readable
 * summary.
 *
 * @param isDryRun Whether this was a dry-run (recommendations are "suggestions to try")
 * @param evaluation The SelectionStrength evaluation result
 * @param recommendations List of parameter recommendations
 * @param summary Human-readable summary of all recommendations
 */
public record SelectionSettingsRecommendation(
    @JsonProperty("is_dry_run") boolean isDryRun,
    @JsonProperty("evaluation") SelectionEvaluation evaluation,
    @JsonProperty("recommendations") List<ParameterRecommendation> recommendations,
    @JsonProperty("summary") String summary) {

  private static final String KEY_SUMMARY_DRY_RUN =
      "selection.settings.recommendation.no_changes.dry_run";

  private static final String KEY_SUMMARY_ENFORCE =
      "selection.settings.recommendation.no_changes.enforce";

  /** Compact constructor that creates defensive copies of collections. */
  public SelectionSettingsRecommendation {
    recommendations = recommendations != null ? List.copyOf(recommendations) : List.of();
  }

  /** Returns true if there are any recommendations. */
  public boolean hasRecommendations() {
    return !recommendations.isEmpty();
  }

  /** Returns the number of recommendations. */
  public int recommendationCount() {
    return recommendations == null ? 0 : recommendations.size();
  }

  /**
   * Creates an empty recommendation (no changes suggested).
   *
   * @param isDryRun Whether this was a dry-run
   * @param evaluation The evaluation result
   * @return Empty recommendation
   */
  public static SelectionSettingsRecommendation noChanges(
      final boolean isDryRun, final SelectionEvaluation evaluation) {
    final String summary =
        isDryRun
            ? MessageSource.getMessage(KEY_SUMMARY_DRY_RUN)
            : MessageSource.getMessage(KEY_SUMMARY_ENFORCE);
    return new SelectionSettingsRecommendation(isDryRun, evaluation, List.of(), summary);
  }
}
