package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrustLevelTest {

  @Test
  void fromConfidence_mapsThresholdEdges() {
    assertEquals(TrustLevel.HIGH, TrustLevel.fromConfidence(0.9));
    assertEquals(TrustLevel.MEDIUM, TrustLevel.fromConfidence(0.6));
  }

  @Test
  void fromConfidence_mapsRanges() {
    assertEquals(TrustLevel.HIGH, TrustLevel.fromConfidence(1.0));
    assertEquals(TrustLevel.MEDIUM, TrustLevel.fromConfidence(0.89));
    assertEquals(TrustLevel.LOW, TrustLevel.fromConfidence(0.59));
    assertEquals(TrustLevel.LOW, TrustLevel.fromConfidence(0.0));
  }

  @Test
  void fromConfidence_handlesOutOfRangeAndNaN() {
    assertEquals(TrustLevel.HIGH, TrustLevel.fromConfidence(2.0));
    assertEquals(TrustLevel.LOW, TrustLevel.fromConfidence(-0.1));
    assertEquals(TrustLevel.LOW, TrustLevel.fromConfidence(Double.NaN));
  }
}
