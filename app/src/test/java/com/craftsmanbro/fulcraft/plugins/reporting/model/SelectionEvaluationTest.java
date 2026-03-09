package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SelectionEvaluationTest {

  @Test
  void okFactoryPopulatesFields() {
    SelectionEvaluation evaluation = SelectionEvaluation.ok(0.2, 0.1);

    assertEquals(SelectionStrength.OK, evaluation.strength());
    assertEquals(0.2, evaluation.excludedRate(), 0.0001);
    assertEquals(0.1, evaluation.compileFailRate(), 0.0001);
    assertEquals(0.0, evaluation.wastedPotential(), 0.0001);
    assertNull(evaluation.dominantFailureReason());
    assertNotNull(evaluation.reason());
    assertTrue(evaluation.reason().contains("20"));
  }

  @Test
  void tooStrictFactoryPopulatesFields() {
    SelectionEvaluation evaluation = SelectionEvaluation.tooStrict(0.5, 0.4, 0.2, 0.1);

    assertEquals(SelectionStrength.TOO_STRICT, evaluation.strength());
    assertEquals(0.5, evaluation.excludedRate(), 0.0001);
    assertEquals(0.2, evaluation.wastedPotential(), 0.0001);
    assertNull(evaluation.dominantFailureReason());
    assertTrue(evaluation.reason().contains("50"));
    assertTrue(evaluation.reason().contains("40"));
    assertTrue(evaluation.reason().contains("20"));
  }

  @Test
  void tooWeakFactoryPopulatesFields() {
    SelectionEvaluation evaluation = SelectionEvaluation.tooWeak(0.1, 0.6, "low_conf", 0.7);

    assertEquals(SelectionStrength.TOO_WEAK, evaluation.strength());
    assertEquals(0.1, evaluation.excludedRate(), 0.0001);
    assertEquals(0.6, evaluation.compileFailRate(), 0.0001);
    assertEquals("low_conf", evaluation.dominantFailureReason());
    assertTrue(evaluation.reason().contains("low_conf"));
    assertTrue(evaluation.reason().contains("70"));
  }
}
