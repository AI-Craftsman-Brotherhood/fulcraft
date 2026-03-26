package com.craftsmanbro.fulcraft.plugins.analysis.model;

import java.util.Locale;

/** Categories of non-deterministic behavior detected during analysis. */
public enum BrittlenessSignal {
  /** Time-dependent calls (e.g., Instant.now, System.currentTimeMillis). */
  TIME,
  /** Randomness sources (e.g., Random, UUID.randomUUID). */
  RANDOM,
  /** Environment-dependent access (e.g., System.getenv). */
  ENVIRONMENT,
  /** Concurrency patterns that can introduce timing variance. */
  CONCURRENCY,
  /** I/O operations that can introduce external variance. */
  IO,
  /** Unordered collection iteration or rendering risks. */
  COLLECTION_ORDER;

  /** Stable lowercase token for prompt/log output. */
  public String token() {
    return name().toLowerCase(Locale.ROOT);
  }
}
