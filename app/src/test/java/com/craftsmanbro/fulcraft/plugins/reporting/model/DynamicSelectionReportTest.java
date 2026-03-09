package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DynamicSelectionReportTest {

  @Test
  void incrementsAndConfidenceBucketsAreTracked() {
    DynamicSelectionReport report = new DynamicSelectionReport();

    report.incrementTotalMethods();
    report.incrementDynamicMethods();
    report.incrementLowConfidenceMethods();
    report.incrementWouldBeExcluded();
    report.incrementExcluded();
    report.recordConfidence(0.81);
    report.recordConfidence(0.89);
    report.recordConfidence(0.84);

    assertEquals(1, report.getTotalMethods());
    assertEquals(1, report.getDynamicMethods());
    assertEquals(1, report.getLowConfidenceMethods());
    assertEquals(1, report.getWouldBeExcludedCount());
    assertEquals(1, report.getExcludedCount());
    assertEquals(2, report.getConfidenceDistribution().get("0.8"));
    assertEquals(1, report.getConfidenceDistribution().get("0.9"));
  }

  @Test
  void confidenceDistributionIsSortedAndDefensivelyCopied() {
    DynamicSelectionReport report = new DynamicSelectionReport();
    Map<String, Integer> source = new HashMap<>();
    source.put("b", 2);
    source.put("a", 1);

    report.setConfidenceDistribution(source);
    source.put("c", 3);

    Map<String, Integer> firstSnapshot = report.getConfidenceDistribution();
    List<String> keys = new ArrayList<>(firstSnapshot.keySet());

    assertEquals(List.of("a", "b"), keys);
    assertFalse(firstSnapshot.containsKey("c"));

    firstSnapshot.put("x", 9);
    assertFalse(report.getConfidenceDistribution().containsKey("x"));
  }

  @Test
  void nullConfidenceDistributionReturnsEmptyUnmodifiableMap() {
    DynamicSelectionReport report = new DynamicSelectionReport();
    report.setConfidenceDistribution(null);

    Map<String, Integer> distribution = report.getConfidenceDistribution();

    assertTrue(distribution.isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> distribution.put("x", 1));
  }
}
