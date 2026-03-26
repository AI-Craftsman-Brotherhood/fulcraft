package com.craftsmanbro.fulcraft.plugins.analysis.model;

/** Discrete trust levels derived from numeric confidence values. */
public enum TrustLevel {
  HIGH,
  MEDIUM,
  LOW;

  public static final double HIGH_THRESHOLD = 0.9;

  public static final double MEDIUM_THRESHOLD = 0.6;

  public static TrustLevel fromConfidence(final double confidence) {
    if (Double.isNaN(confidence)) {
      return LOW;
    }
    final double normalized = Math.clamp(confidence, 0.0, 1.0);
    if (normalized >= HIGH_THRESHOLD) {
      return HIGH;
    }
    if (normalized >= MEDIUM_THRESHOLD) {
      return MEDIUM;
    }
    return LOW;
  }
}
