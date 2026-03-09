package com.craftsmanbro.fulcraft.infrastructure.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.craftsmanbro.fulcraft.infrastructure.usage.impl.TokenUsageEstimator;
import org.junit.jupiter.api.Test;

class TokenUsageEstimatorTest {

  @Test
  void returnsZeroForNullOrEmpty() {
    TokenUsageEstimator estimator = new TokenUsageEstimator();

    assertEquals(0L, estimator.estimateTokens(null));
    assertEquals(0L, estimator.estimateTokens(""));
  }

  @Test
  void roundsUpByCharacterCount() {
    TokenUsageEstimator estimator = new TokenUsageEstimator();

    assertEquals(1L, estimator.estimateTokens("a"));
    assertEquals(1L, estimator.estimateTokens("abcd"));
    assertEquals(2L, estimator.estimateTokens("abcde"));
    assertEquals(3L, estimator.estimateTokens("abcdefghij"));
  }
}
