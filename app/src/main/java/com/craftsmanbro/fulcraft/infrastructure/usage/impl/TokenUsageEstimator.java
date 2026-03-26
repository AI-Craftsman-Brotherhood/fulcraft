package com.craftsmanbro.fulcraft.infrastructure.usage.impl;

public class TokenUsageEstimator {

  private static final int CHARACTERS_PER_TOKEN = 4;

  public long estimateTokens(final String text) {
    if (text == null || text.isEmpty()) {
      return 0L;
    }
    return estimateTokensByCharacterCount(text.length());
  }

  private long estimateTokensByCharacterCount(final int characterCount) {
    // Ceiling division keeps estimation deterministic without floating-point math.
    return (characterCount + (long) CHARACTERS_PER_TOKEN - 1) / CHARACTERS_PER_TOKEN;
  }
}
