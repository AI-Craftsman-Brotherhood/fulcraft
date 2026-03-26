package com.craftsmanbro.fulcraft.infrastructure.cache.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

/**
 * Represents a cache entry for generated test code.
 *
 * <p>This class is package-private and used only within the cache package.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class CacheEntry {

  private String sourceHash;

  private String promptHash;

  private String configHash;

  private String generatedCode;

  private long timestamp;

  private long lastAccessed;

  /** Default constructor for Jackson deserialization. */
  public CacheEntry() {
    // Default constructor for Jackson deserialization
  }

  /**
   * Creates a new CacheEntry with the specified values.
   *
   * @param sourceHash the hash of the source code
   * @param promptHash the hash of the prompt template
   * @param configHash the hash/key of the configuration
   * @param generatedCode the generated code
   * @return a new valid CacheEntry
   */
  public static CacheEntry of(
      final String sourceHash,
      final String promptHash,
      final String configHash,
      final String generatedCode) {
    final CacheEntry cacheEntry = new CacheEntry();
    cacheEntry.setSourceHash(Objects.requireNonNull(sourceHash));
    cacheEntry.setPromptHash(Objects.requireNonNull(promptHash));
    cacheEntry.setConfigHash(Objects.requireNonNull(configHash));
    cacheEntry.setGeneratedCode(Objects.requireNonNull(generatedCode));
    final long currentTimeMillis = System.currentTimeMillis();
    cacheEntry.setTimestamp(currentTimeMillis);
    cacheEntry.setLastAccessed(currentTimeMillis);
    return cacheEntry;
  }

  public String getSourceHash() {
    return sourceHash;
  }

  public void setSourceHash(final String sourceHash) {
    this.sourceHash = sourceHash;
  }

  public String getPromptHash() {
    return promptHash;
  }

  public void setPromptHash(final String promptHash) {
    this.promptHash = promptHash;
  }

  public String getConfigHash() {
    return configHash;
  }

  public void setConfigHash(final String configHash) {
    this.configHash = configHash;
  }

  public String getGeneratedCode() {
    return generatedCode;
  }

  public void setGeneratedCode(final String generatedCode) {
    this.generatedCode = generatedCode;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  public long getLastAccessed() {
    return lastAccessed;
  }

  public void setLastAccessed(final long lastAccessed) {
    this.lastAccessed = lastAccessed;
  }

  /**
   * Checks if this entry is valid.
   *
   * <p>A valid entry must have non-null hashes and generated code.
   *
   * @return true if valid, false otherwise
   */
  public boolean isValid() {
    return sourceHash != null && promptHash != null && configHash != null && generatedCode != null;
  }

  /**
   * Checks if this entry has expired based on the given TTL.
   *
   * @param currentTimeMillis the current time in milliseconds
   * @param ttlMs the TTL in milliseconds (Long.MAX_VALUE means no expiration)
   * @return true if the entry has expired, false otherwise
   */
  public boolean isExpired(final long currentTimeMillis, final long ttlMs) {
    if (ttlMs == Long.MAX_VALUE) {
      // No expiration
      return false;
    }
    if (timestamp <= 0) {
      // Legacy entry without timestamp, treat as not expired
      return false;
    }
    return (currentTimeMillis - timestamp) > ttlMs;
  }
}
