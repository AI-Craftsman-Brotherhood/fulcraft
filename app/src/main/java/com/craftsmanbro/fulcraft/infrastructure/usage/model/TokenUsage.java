package com.craftsmanbro.fulcraft.infrastructure.usage.model;

public class TokenUsage {

  private final long promptTokens;

  private final long completionTokens;

  private final long totalTokens;

  public TokenUsage(final long promptTokens, final long completionTokens, final long totalTokens) {
    this.promptTokens = promptTokens;
    this.completionTokens = completionTokens;
    this.totalTokens = totalTokens;
  }

  public long getPromptTokens() {
    return promptTokens;
  }

  public long getCompletionTokens() {
    return completionTokens;
  }

  public long getTotalTokens() {
    return totalTokens;
  }

  public long effectiveTotal() {
    if (totalTokens > 0L) {
      return totalTokens;
    }
    return clampNonNegative(promptTokens) + clampNonNegative(completionTokens);
  }

  private static long clampNonNegative(final long value) {
    return Math.max(0L, value);
  }
}
