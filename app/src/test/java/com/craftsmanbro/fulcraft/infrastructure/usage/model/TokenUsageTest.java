package com.craftsmanbro.fulcraft.infrastructure.usage.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TokenUsageTest {

  @Test
  void gettersReturnConstructorValues() {
    TokenUsage usage = new TokenUsage(10, 5, 42);

    assertEquals(10, usage.getPromptTokens());
    assertEquals(5, usage.getCompletionTokens());
    assertEquals(42, usage.getTotalTokens());
  }

  @Test
  void effectiveTotalUsesTotalWhenPresent() {
    TokenUsage usage = new TokenUsage(10, 5, 42);

    assertEquals(42, usage.effectiveTotal());
  }

  @Test
  void effectiveTotalSumsPositivePartsWhenTotalMissing() {
    TokenUsage usage = new TokenUsage(10, 5, 0);

    assertEquals(15, usage.effectiveTotal());
  }

  @Test
  void effectiveTotalIgnoresNegativeParts() {
    TokenUsage usage = new TokenUsage(-1, 8, 0);

    assertEquals(8, usage.effectiveTotal());
  }

  @Test
  void effectiveTotalFallsBackToPositivePartsWhenTotalNonPositive() {
    TokenUsage usage = new TokenUsage(4, 6, -3);

    assertEquals(10, usage.effectiveTotal());
  }

  @Test
  void effectiveTotalReturnsZeroWhenAllPartsNonPositive() {
    TokenUsage usage = new TokenUsage(-1, 0, 0);

    assertEquals(0, usage.effectiveTotal());
  }
}
