package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionSettingsRecommendationTest {

  @Test
  void constructorDefensivelyCopiesRecommendations() {
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.2, 0.1);
    List<ParameterRecommendation> source = new ArrayList<>();
    source.add(new ParameterRecommendation("skip_threshold", 0.1, 0.2, "reason", "trigger"));

    SelectionSettingsRecommendation recommendation =
        new SelectionSettingsRecommendation(false, evaluation, source, "summary");

    source.add(new ParameterRecommendation("skip_threshold", 0.2, 0.3, "reason", "trigger"));

    assertEquals(1, recommendation.recommendationCount());
    assertEquals(1, recommendation.recommendations().size());
    assertThrows(
        UnsupportedOperationException.class,
        () -> recommendation.recommendations().add(source.get(0)));
  }

  @Test
  void noChangesSummaryAlignsWithMode() {
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.1, 0.2);

    SelectionSettingsRecommendation dryRun =
        SelectionSettingsRecommendation.noChanges(true, evaluation);
    SelectionSettingsRecommendation enforce =
        SelectionSettingsRecommendation.noChanges(false, evaluation);

    assertTrue(dryRun.isDryRun());
    assertFalse(enforce.isDryRun());
    assertFalse(dryRun.summary().isBlank());
    assertFalse(enforce.summary().isBlank());
    assertNotEquals(dryRun.summary(), enforce.summary());
    assertEquals(
        MessageSource.getMessage("selection.settings.recommendation.no_changes.dry_run"),
        dryRun.summary());
    assertEquals(
        MessageSource.getMessage("selection.settings.recommendation.no_changes.enforce"),
        enforce.summary());
  }

  @Test
  void nullRecommendationsBecomeEmpty() {
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.1, 0.2);

    SelectionSettingsRecommendation recommendation =
        new SelectionSettingsRecommendation(false, evaluation, null, "summary");

    assertNotNull(recommendation.recommendations());
    assertTrue(recommendation.recommendations().isEmpty());
    assertFalse(recommendation.hasRecommendations());
    assertEquals(0, recommendation.recommendationCount());
  }
}
