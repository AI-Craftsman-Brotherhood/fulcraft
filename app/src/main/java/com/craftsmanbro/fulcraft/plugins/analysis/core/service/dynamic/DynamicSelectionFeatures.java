package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;
import java.util.List;

/**
 * Extracts and holds dynamic features from a MethodInfo for use in defensive selection. This class
 * abstracts the raw DynamicResolution list into countable metrics.
 */
public record DynamicSelectionFeatures(
    double minConfidence,
    int unresolvedCount,
    int externalOrNotFoundCount,
    boolean hasServiceLoader,
    double serviceLoaderMinConfidence,
    int serviceLoaderCandidateCount,
    int highCount,
    int mediumCount,
    int lowCount) {

  private static final double UNRESOLVED_CONFIDENCE_THRESHOLD = 0.8;

  private static final double FEATURE_FALLBACK_CONFIDENCE = 0.79;

  public static DynamicSelectionFeatures from(final MethodInfo method) {
    if (method == null) {
      return emptyFeatures();
    }
    final List<DynamicResolution> resolutions = method.getDynamicResolutions();
    final int dynamicFeatureTotal = method.getDynamicFeatureTotal();
    if (resolutions.isEmpty() && dynamicFeatureTotal == 0) {
      // Safe default: no dynamic ops found -> full confidence
      return emptyFeatures();
    }
    final Accumulator accumulator = new Accumulator(method, dynamicFeatureTotal);
    for (final DynamicResolution resolution : resolutions) {
      accumulator.accept(resolution);
    }
    return accumulator.toFeatures();
  }

  private static DynamicSelectionFeatures emptyFeatures() {
    return new DynamicSelectionFeatures(1.0, 0, 0, false, 1.0, 0, 0, 0, 0);
  }

  private static final class Accumulator {

    private final boolean useResolutionLevels;

    private double minConfidence = 1.0;

    private int unresolvedCount;

    private int externalOrNotFoundCount;

    private boolean hasServiceLoader;

    private double serviceLoaderMinConfidence = 1.0;

    private int serviceLoaderCandidateCount;

    private int highCount;

    private int mediumCount;

    private int lowCount;

    private Accumulator(final MethodInfo method, final int dynamicFeatureTotal) {
      this.useResolutionLevels = dynamicFeatureTotal == 0;
      this.hasServiceLoader = method.hasDynamicFeatureServiceLoader();
      if (!useResolutionLevels) {
        applyDynamicFeatureTotals(method, dynamicFeatureTotal);
      }
    }

    private void applyDynamicFeatureTotals(final MethodInfo method, final int dynamicFeatureTotal) {
      minConfidence = Math.min(minConfidence, FEATURE_FALLBACK_CONFIDENCE);
      unresolvedCount += dynamicFeatureTotal;
      highCount += method.getDynamicFeatureHigh();
      mediumCount += method.getDynamicFeatureMedium();
      lowCount += method.getDynamicFeatureLow();
      if (hasServiceLoader) {
        serviceLoaderMinConfidence =
            Math.min(serviceLoaderMinConfidence, FEATURE_FALLBACK_CONFIDENCE);
      }
    }

    private void accept(final DynamicResolution resolution) {
      if (resolution == null) {
        return;
      }
      final double confidence = normalizeConfidence(resolution.confidence());
      minConfidence = Math.min(minConfidence, confidence);
      if (useResolutionLevels) {
        updateUnresolved(confidence);
        updateTrustLevel(resolution, confidence);
      }
      updateServiceLoader(resolution, confidence);
      updateExternal(resolution);
    }

    private void updateUnresolved(final double confidence) {
      if (confidence < UNRESOLVED_CONFIDENCE_THRESHOLD) {
        unresolvedCount++;
      }
    }

    private void updateServiceLoader(final DynamicResolution resolution, final double confidence) {
      if (!DynamicResolution.SERVICELOADER_PROVIDERS.equals(resolution.subtype())) {
        return;
      }
      hasServiceLoader = true;
      if (confidence < serviceLoaderMinConfidence) {
        serviceLoaderMinConfidence = confidence;
      }
      serviceLoaderCandidateCount += resolution.providers().size();
    }

    private void updateExternal(final DynamicResolution resolution) {
      if (resolution.resolvedClassFqn() == null || resolution.resolvedClassFqn().isBlank()) {
        externalOrNotFoundCount++;
      }
    }

    private void updateTrustLevel(final DynamicResolution resolution, final double confidence) {
      final TrustLevel level =
          resolution.trustLevel() != null
              ? resolution.trustLevel()
              : TrustLevel.fromConfidence(confidence);
      switch (level) {
        case HIGH -> highCount++;
        case MEDIUM -> mediumCount++;
        case LOW -> lowCount++;
      }
    }

    private DynamicSelectionFeatures toFeatures() {
      return new DynamicSelectionFeatures(
          minConfidence,
          unresolvedCount,
          externalOrNotFoundCount,
          hasServiceLoader,
          serviceLoaderMinConfidence,
          serviceLoaderCandidateCount,
          highCount,
          mediumCount,
          lowCount);
    }

    private static double normalizeConfidence(final double confidence) {
      if (Double.isNaN(confidence)) {
        return 0.0;
      }
      if (confidence < 0.0) {
        return 0.0;
      }
      if (confidence > 1.0) {
        return 1.0;
      }
      return confidence;
    }
  }
}
