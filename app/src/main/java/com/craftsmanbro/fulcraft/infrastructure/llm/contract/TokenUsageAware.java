package com.craftsmanbro.fulcraft.infrastructure.llm.contract;

import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.util.Optional;

/**
 * Contract for LLM clients that can expose the most recent token usage for the current thread.
 *
 * <p>Implementations should update usage after {@code generateTest} completes and clear it when
 * {@code clearContext} is invoked. Callers should treat missing data as "unknown" rather than zero
 * usage.
 */
@FunctionalInterface
public interface TokenUsageAware {

  /**
   * Returns the most recent token usage observed for the current thread.
   *
   * @return latest token usage, or empty when usage is unknown
   */
  Optional<TokenUsage> getLastUsage();
}
