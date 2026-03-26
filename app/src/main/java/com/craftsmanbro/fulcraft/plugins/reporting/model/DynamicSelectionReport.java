package com.craftsmanbro.fulcraft.plugins.reporting.model;

import java.util.Map;
import java.util.TreeMap;

/** Feature-layer neutral dynamic selection metrics. */
public class DynamicSelectionReport {

  private int totalMethods;

  private int dynamicMethods;

  private int lowConfidenceMethods;

  private int wouldBeExcludedCount;

  private int excludedCount;

  private Map<String, Integer> confidenceDistribution;

  public int getTotalMethods() {
    return totalMethods;
  }

  public void setTotalMethods(final int totalMethods) {
    this.totalMethods = totalMethods;
  }

  public void incrementTotalMethods() {
    totalMethods++;
  }

  public int getDynamicMethods() {
    return dynamicMethods;
  }

  public void setDynamicMethods(final int dynamicMethods) {
    this.dynamicMethods = dynamicMethods;
  }

  public void incrementDynamicMethods() {
    dynamicMethods++;
  }

  public int getLowConfidenceMethods() {
    return lowConfidenceMethods;
  }

  public void setLowConfidenceMethods(final int lowConfidenceMethods) {
    this.lowConfidenceMethods = lowConfidenceMethods;
  }

  public void incrementLowConfidenceMethods() {
    lowConfidenceMethods++;
  }

  public int getWouldBeExcludedCount() {
    return wouldBeExcludedCount;
  }

  public void setWouldBeExcludedCount(final int wouldBeExcludedCount) {
    this.wouldBeExcludedCount = wouldBeExcludedCount;
  }

  public void incrementWouldBeExcluded() {
    wouldBeExcludedCount++;
  }

  public int getExcludedCount() {
    return excludedCount;
  }

  public void setExcludedCount(final int excludedCount) {
    this.excludedCount = excludedCount;
  }

  public void incrementExcluded() {
    excludedCount++;
  }

  public Map<String, Integer> getConfidenceDistribution() {
    if (confidenceDistribution == null) {
      return Map.of();
    }
    return new TreeMap<>(confidenceDistribution);
  }

  public void setConfidenceDistribution(final Map<String, Integer> confidenceDistribution) {
    if (confidenceDistribution == null) {
      this.confidenceDistribution = null;
      return;
    }
    this.confidenceDistribution = new TreeMap<>(confidenceDistribution);
  }

  public void recordConfidence(final double confidence) {
    if (confidenceDistribution == null) {
      confidenceDistribution = new TreeMap<>();
    }
    final String key = String.format(java.util.Locale.ROOT, "%.1f", confidence);
    confidenceDistribution.merge(key, 1, (a, b) -> a + b);
  }
}
