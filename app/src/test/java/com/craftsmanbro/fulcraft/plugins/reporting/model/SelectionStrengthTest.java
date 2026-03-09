package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SelectionStrengthTest {

  @Test
  void valuesRemainInExpectedOrder() {
    assertArrayEquals(
        new SelectionStrength[] {
          SelectionStrength.OK, SelectionStrength.TOO_STRICT, SelectionStrength.TOO_WEAK
        },
        SelectionStrength.values());
  }

  @Test
  void valueOfResolvesDeclaredConstants() {
    assertEquals(SelectionStrength.OK, SelectionStrength.valueOf("OK"));
    assertEquals(SelectionStrength.TOO_STRICT, SelectionStrength.valueOf("TOO_STRICT"));
    assertEquals(SelectionStrength.TOO_WEAK, SelectionStrength.valueOf("TOO_WEAK"));
  }
}
