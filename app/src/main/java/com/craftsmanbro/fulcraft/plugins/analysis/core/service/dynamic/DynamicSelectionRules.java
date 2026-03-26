package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies defensive selection rules to DynamicSelectionFeatures. Determines if a candidate should
 * be skipped, penalized, or accepted.
 */
public class DynamicSelectionRules {

  private static final double LOW_CONFIDENCE_THRESHOLD = 0.8;

  private final com.craftsmanbro.fulcraft.plugins.analysis.config.SelectionRulesConfig config;

  public DynamicSelectionRules(
      final com.craftsmanbro.fulcraft.plugins.analysis.config.SelectionRulesConfig config) {
    this.config =
        config != null
            ? config
            : new com.craftsmanbro.fulcraft.plugins.analysis.config.SelectionRulesConfig();
  }

  public record RuleDecision(double scorePenalty, boolean shouldSkip, List<String> reasons) {

    public RuleDecision {
      reasons = reasons != null ? List.copyOf(reasons) : List.of();
    }
  }

  public RuleDecision evaluate(final DynamicSelectionFeatures features) {
    final List<String> reasons = new ArrayList<>();
    double totalPenalty = 0.0;
    boolean shouldSkip = false;
    final double skipThreshold = config.getSkipThreshold();
    final double penaltyLowConf = config.getPenaltyLowConfidence();
    final double penaltyUnresolved = config.getPenaltyUnresolvedEach();
    final double penaltyExternal = config.getPenaltyExternalEach();
    final double penaltyServiceLoaderLow = config.getPenaltyServiceLoaderLow();
    // 1. Min Confidence Check
    if (features.minConfidence() < LOW_CONFIDENCE_THRESHOLD) {
      totalPenalty += penaltyLowConf;
      reasons.add(
          String.format(
              Locale.ROOT,
              "low_confidence(%.2f)<%.2f",
              features.minConfidence(),
              LOW_CONFIDENCE_THRESHOLD));
    }
    // 2. Unresolved Count
    if (features.unresolvedCount() > 0) {
      final double p = features.unresolvedCount() * penaltyUnresolved;
      totalPenalty += p;
      reasons.add(
          String.format(
              Locale.ROOT, "unresolved(%d)*%.2f", features.unresolvedCount(), penaltyUnresolved));
    }
    // 3. External Count
    if (features.externalOrNotFoundCount() > 0) {
      final double p = features.externalOrNotFoundCount() * penaltyExternal;
      totalPenalty += p;
      reasons.add(
          String.format(
              Locale.ROOT,
              "external(%d)*%.2f",
              features.externalOrNotFoundCount(),
              penaltyExternal));
    }
    // 4. ServiceLoader
    if (features.hasServiceLoader()
        && features.serviceLoaderMinConfidence() < LOW_CONFIDENCE_THRESHOLD) {
      totalPenalty += penaltyServiceLoaderLow;
      reasons.add("spi_low_conf");
    }
    // Determine Skip
    if (features.minConfidence() < skipThreshold) {
      shouldSkip = true;
      reasons.add(String.format(Locale.ROOT, "SKIP:min_conf<%.2f", skipThreshold));
    }
    return new RuleDecision(totalPenalty, shouldSkip, reasons);
  }
}
