package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of Selection strength evaluation.
 *
 * <p>Contains the judgment (OK/TOO_STRICT/TOO_WEAK), a human-readable explanation, and supporting
 * metrics.
 *
 * @param strength The evaluation result
 * @param reason Human-readable explanation of the judgment
 * @param excludedRate Actual excluded rate (excluded / total tasks)
 * @param compileFailRate Compile failure rate for non-excluded tasks
 * @param wastedPotential Ratio of excluded tasks that could have succeeded
 * @param dominantFailureReason The reason key with most concentrated failures (for TOO_WEAK)
 */
public record SelectionEvaluation(
    @JsonProperty("strength") SelectionStrength strength,
    @JsonProperty("reason") String reason,
    @JsonProperty("excluded_rate") double excludedRate,
    @JsonProperty("compile_fail_rate") double compileFailRate,
    @JsonProperty("wasted_potential") double wastedPotential,
    @JsonProperty("dominant_failure_reason") String dominantFailureReason) {

  private static final String KEY_OK_REASON = "selection.evaluation.reason.ok";

  private static final String KEY_TOO_STRICT_REASON = "selection.evaluation.reason.too_strict";

  private static final String KEY_TOO_WEAK_REASON = "selection.evaluation.reason.too_weak";

  /**
   * Creates an OK evaluation.
   *
   * @param excludedRate The excluded rate
   * @param compileFailRate The compile failure rate
   * @return OK evaluation
   */
  public static SelectionEvaluation ok(final double excludedRate, final double compileFailRate) {
    final String reason =
        String.format(
            MessageSource.getMessage(KEY_OK_REASON), excludedRate * 100, compileFailRate * 100);
    return new SelectionEvaluation(
        SelectionStrength.OK, reason, excludedRate, compileFailRate, 0.0, null);
  }

  /**
   * Creates a TOO_STRICT evaluation.
   *
   * @param excludedRate The excluded rate
   * @param threshold The threshold that was exceeded
   * @param wastedPotential Ratio of excluded tasks that could have succeeded
   * @param compileFailRate The compile failure rate
   * @return TOO_STRICT evaluation
   */
  public static SelectionEvaluation tooStrict(
      final double excludedRate,
      final double threshold,
      final double wastedPotential,
      final double compileFailRate) {
    final String reason =
        String.format(
            MessageSource.getMessage(KEY_TOO_STRICT_REASON),
            excludedRate * 100,
            threshold * 100,
            wastedPotential * 100);
    return new SelectionEvaluation(
        SelectionStrength.TOO_STRICT, reason, excludedRate, compileFailRate, wastedPotential, null);
  }

  /**
   * Creates a TOO_WEAK evaluation.
   *
   * @param excludedRate The excluded rate
   * @param compileFailRate The compile failure rate
   * @param dominantReason The reason key with highest failure concentration
   * @param concentration The concentration ratio for the dominant reason
   * @return TOO_WEAK evaluation
   */
  public static SelectionEvaluation tooWeak(
      final double excludedRate,
      final double compileFailRate,
      final String dominantReason,
      final double concentration) {
    final String reason =
        String.format(
            MessageSource.getMessage(KEY_TOO_WEAK_REASON), concentration * 100, dominantReason);
    return new SelectionEvaluation(
        SelectionStrength.TOO_WEAK, reason, excludedRate, compileFailRate, 0.0, dominantReason);
  }
}
