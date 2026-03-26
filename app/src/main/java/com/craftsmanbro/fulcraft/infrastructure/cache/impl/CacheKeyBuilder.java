package com.craftsmanbro.fulcraft.infrastructure.cache.impl;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Builds cache keys and computes hashes for cache entries.
 *
 * <p>This class is package-private and used only within the cache package.
 */
final class CacheKeyBuilder {

  private final boolean versionCheckEnabled;

  private final String versionHash;

  /**
   * Creates a key builder without version checking.
   *
   * @return a key builder with version checking disabled
   */
  public static CacheKeyBuilder withoutVersionCheck() {
    return new CacheKeyBuilder(false, "");
  }

  /**
   * Creates a key builder with version checking enabled.
   *
   * @param versionHash the version hash to include in keys
   * @return a key builder with version checking enabled
   */
  public static CacheKeyBuilder withVersionCheck(final String versionHash) {
    return new CacheKeyBuilder(true, versionHash != null ? versionHash : "");
  }

  private CacheKeyBuilder(final boolean versionCheckEnabled, final String versionHash) {
    this.versionCheckEnabled = versionCheckEnabled;
    this.versionHash = versionHash;
  }

  /**
   * Returns whether version checking is enabled.
   *
   * @return true if version checking is enabled
   */
  public boolean isVersionCheckEnabled() {
    return versionCheckEnabled;
  }

  /**
   * Returns the version hash.
   *
   * @return the version hash, or empty string if not set
   */
  public String getVersionHash() {
    return versionHash;
  }

  /**
   * Computes the SHA-256 hash of the given content.
   *
   * @param content the content to hash
   * @return the hex-encoded SHA-256 hash
   */
  public String computeHash(final String content) {
    final String contentToHash =
        Objects.requireNonNull(
            content,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "content must not be null"));
    return Hashing.sha256()
        .hashString(contentToHash, Objects.requireNonNull(StandardCharsets.UTF_8))
        .toString();
  }

  /**
   * Creates a cache key from the given components.
   *
   * @param taskId the task identifier
   * @param sourceHash the source code hash
   * @param promptHash the prompt template hash
   * @param configKey the configuration key
   * @return the computed cache key
   */
  public String makeKey(
      final String taskId,
      final String sourceHash,
      final String promptHash,
      final String configKey) {
    // Use length-prefixed format to prevent ambiguity
    final StringBuilder rawKeyBuilder = new StringBuilder();
    rawKeyBuilder.append(taskId.length()).append(":").append(taskId);
    rawKeyBuilder.append("|").append(sourceHash.length()).append(":").append(sourceHash);
    rawKeyBuilder.append("|").append(promptHash.length()).append(":").append(promptHash);
    rawKeyBuilder.append("|").append(configKey.length()).append(":").append(configKey);
    // Include version hash if version checking is enabled
    if (versionCheckEnabled && versionHash != null && !versionHash.isEmpty()) {
      rawKeyBuilder.append("|ver:").append(versionHash);
    }
    return computeHash(rawKeyBuilder.toString());
  }
}
