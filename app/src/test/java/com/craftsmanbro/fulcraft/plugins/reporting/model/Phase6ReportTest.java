package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Phase6ReportTest {

  @Test
  void constructorDefensivelyCopiesCollections() {
    Phase6Report.SummarySection summary =
        new Phase6Report.SummarySection(
            1, 1, 1, 1, 1, 0, 0, 0, 0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0);
    Phase6Report.EvaluationSection evaluation =
        new Phase6Report.EvaluationSection("OK", "reason", 0.1, 0.0, 0.0, null);

    Map<String, Phase6Report.ReasonStatsSection> reasonStats = new HashMap<>();
    reasonStats.put(
        "low_conf", new Phase6Report.ReasonStatsSection(1, 0, 0.0, 0.0, 0, 0, 0.0, 0, 0.0));

    List<Phase6Report.RecommendationSection> recommendations = new ArrayList<>();
    recommendations.add(
        new Phase6Report.RecommendationSection(
            "skip_threshold", 0.1, 0.2, 0.1, "reason", "low_conf"));

    Phase6Report report =
        new Phase6Report("run-1", 123L, true, summary, reasonStats, evaluation, recommendations);

    reasonStats.put(
        "other", new Phase6Report.ReasonStatsSection(1, 0, 0.0, 0.0, 0, 0, 0.0, 0, 0.0));
    recommendations.add(
        new Phase6Report.RecommendationSection("foo", 0.2, 0.3, 0.1, "reason", "other"));

    assertEquals(1, report.reasonStats().size());
    assertEquals(1, report.recommendations().size());
  }

  @Test
  void nullCollectionsBecomeEmptyAndUnmodifiable() {
    Phase6Report.SummarySection summary =
        new Phase6Report.SummarySection(
            1, 1, 1, 1, 1, 0, 0, 0, 0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0);
    Phase6Report.EvaluationSection evaluation =
        new Phase6Report.EvaluationSection("OK", "reason", 0.1, 0.0, 0.0, null);

    Phase6Report report = new Phase6Report("run-1", 123L, false, summary, null, evaluation, null);

    assertNotNull(report.reasonStats());
    assertNotNull(report.recommendations());
    assertTrue(report.reasonStats().isEmpty());
    assertTrue(report.recommendations().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> report.reasonStats().put("x", null));
    assertThrows(UnsupportedOperationException.class, () -> report.recommendations().add(null));
  }
}
