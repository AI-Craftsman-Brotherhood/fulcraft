package com.craftsmanbro.fulcraft.infrastructure.cache.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CacheRevalidationResultTest {

  @Test
  void validFactorySetsExpectedState() {
    CacheRevalidationResult result = CacheRevalidationResult.valid(12.5);

    assertTrue(result.isValid());
    assertNull(result.getReason());
    assertEquals(12.5, result.getDurationMs());
  }

  @Test
  void invalidFactorySetsExpectedState() {
    CacheRevalidationResult result = CacheRevalidationResult.invalid("syntax error");

    assertFalse(result.isValid());
    assertEquals("syntax error", result.getReason());
    assertEquals(0.0, result.getDurationMs());
  }

  @Test
  void toStringFormatsValidResultWithTwoDecimalPlaces() {
    CacheRevalidationResult result = CacheRevalidationResult.valid(12.5);

    assertEquals("RevalidationResult{valid=true, durationMs=12.50}", result.toString());
  }

  @Test
  void toStringFormatsInvalidResultWithReason() {
    CacheRevalidationResult result = CacheRevalidationResult.invalid("syntax error");

    assertEquals("RevalidationResult{valid=false, reason='syntax error'}", result.toString());
  }
}
