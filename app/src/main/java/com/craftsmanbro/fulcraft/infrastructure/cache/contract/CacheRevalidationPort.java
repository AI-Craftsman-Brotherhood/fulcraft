package com.craftsmanbro.fulcraft.infrastructure.cache.contract;

import com.craftsmanbro.fulcraft.infrastructure.cache.model.CacheRevalidationResult;

/** Contract for lightweight cache entry revalidation. */
public interface CacheRevalidationPort {

  /**
   * Revalidates cached generated code before it is reused.
   *
   * @param cachedCode cached generated code to inspect
   * @param taskId logical task identifier used for diagnostics
   * @return revalidation outcome for the cached code
   */
  CacheRevalidationResult revalidate(String cachedCode, String taskId);

  /** Returns the total number of revalidation attempts performed so far. */
  int getRevalidationCount();

  /** Returns the number of revalidation attempts that failed. */
  int getFailureCount();

  /** Returns the revalidation success rate as a percentage from 0.0 to 100.0. */
  double getSuccessRate();

  /** Resets the accumulated revalidation statistics. */
  void resetStats();
}
