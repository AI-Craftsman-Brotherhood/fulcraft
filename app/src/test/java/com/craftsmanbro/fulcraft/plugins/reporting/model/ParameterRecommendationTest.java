package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParameterRecommendationTest {

  @Test
  void increaseCreatesPositiveChange() {
    ParameterRecommendation recommendation =
        ParameterRecommendation.increase("skip_threshold", 0.2, 0.1, "reason", "trigger");

    assertEquals(0.2, recommendation.currentValue(), 0.0001);
    assertEquals(0.3, recommendation.recommendedValue(), 0.0001);
    assertEquals(0.1, recommendation.change(), 0.0001);
    assertTrue(recommendation.isIncrease());
  }

  @Test
  void decreaseDoesNotGoBelowZero() {
    ParameterRecommendation recommendation =
        ParameterRecommendation.decrease("skip_threshold", 0.1, 0.2, "reason", "trigger");

    assertEquals(0.0, recommendation.recommendedValue(), 0.0001);
    assertEquals(-0.1, recommendation.change(), 0.0001);
    assertFalse(recommendation.isIncrease());
  }
}
