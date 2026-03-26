package com.craftsmanbro.fulcraft.infrastructure.cache.contract;

import java.util.Optional;

/** Contract for persistent generation cache operations. */
public interface CacheStorePort extends AutoCloseable {

  /** Prepares the backing store before cache operations are used. */
  void initialize();

  /**
   * Removes entries that are no longer valid under the configured TTL policy.
   *
   * @return number of entries removed from the store
   */
  int evictExpiredEntries();

  /** Persists the current cache state to the backing store. */
  void saveCache();

  /**
   * Returns cached generated code for the composite cache key.
   *
   * @param taskId logical task identifier
   * @param sourceCode source content used to generate the code
   * @param promptTemplate prompt template used for generation
   * @param configKey configuration key associated with the request
   * @return cached generated code when a matching entry exists
   */
  Optional<String> getCachedCode(
      String taskId, String sourceCode, String promptTemplate, String configKey);

  /**
   * Stores generated code for the composite cache key.
   *
   * @param taskId logical task identifier
   * @param sourceCode source content used to generate the code
   * @param promptTemplate prompt template used for generation
   * @param configKey configuration key associated with the request
   * @param generatedCode generated code to cache
   */
  void putCache(
      String taskId,
      String sourceCode,
      String promptTemplate,
      String configKey,
      String generatedCode);

  /**
   * Invalidates the cache entry for the composite cache key when present.
   *
   * @param taskId logical task identifier
   * @param sourceCode source content used to generate the code
   * @param promptTemplate prompt template used for generation
   * @param configKey configuration key associated with the request
   */
  void invalidateEntry(String taskId, String sourceCode, String promptTemplate, String configKey);

  /**
   * Returns the configured TTL in milliseconds.
   *
   * <p>The value is meaningful only when {@link #hasTtl()} is {@code true}.
   */
  long getTtlMillis();

  /** Returns whether TTL-based cache expiration is enabled. */
  boolean hasTtl();

  /** Returns whether version checking is enabled for cache keys. */
  boolean isVersionCheckEnabled();

  /** Returns the version hash used for cache key versioning when enabled. */
  String getVersionHash();

  /** Releases any resources held by the backing store. */
  @Override
  void close();
}
