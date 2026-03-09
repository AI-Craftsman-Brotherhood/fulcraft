package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import com.craftsmanbro.fulcraft.i18n.MessageSource;

/** Severity levels for dynamic features. Used to calculate impact on analysis reliability. */
public enum DynamicFeatureSeverity {

  /** Low impact - mostly observable (e.g., DI annotations) */
  LOW(1),
  /** Medium impact - may affect type resolution (e.g., Class.forName, ServiceLoader) */
  MEDIUM(2),
  /** High impact - significantly affects analysis accuracy (e.g., Method.invoke, defineClass) */
  HIGH(3);

  private final int weight;

  DynamicFeatureSeverity(final int weight) {
    if (weight <= 0) {
      throw new IllegalArgumentException(
          MessageSource.getMessage("analysis.dynamic_feature_severity.error.weight_positive"));
    }
    this.weight = weight;
  }

  public int getWeight() {
    return weight;
  }
}
