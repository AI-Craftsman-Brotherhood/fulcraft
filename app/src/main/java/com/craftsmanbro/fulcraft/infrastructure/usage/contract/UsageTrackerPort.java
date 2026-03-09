package com.craftsmanbro.fulcraft.infrastructure.usage.contract;

import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageScope;

/**
 * Contract for tracking LLM usage for quota reporting.
 *
 * <p>Implementations should record per-scope usage with day/month rollups.
 */
public interface UsageTrackerPort {

  /**
   * Records usage for the given scope.
   *
   * @param scope scope to record under (project/user)
   * @param requestCount number of requests to add
   * @param tokenCount number of tokens to add
   */
  void recordUsage(UsageScope scope, long requestCount, long tokenCount);
}
