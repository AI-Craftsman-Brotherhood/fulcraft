package com.craftsmanbro.fulcraft.infrastructure.version.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Immutable version snapshot used for cache key computation and diagnostics. */
public final class VersionInfo {

  private static final String UNKNOWN_VERSION = "unknown";
  private static final String APP_VERSION_SEGMENT_PREFIX = "app:";
  private static final String CONFIG_HASH_SEGMENT_PREFIX = "|cfg:";
  private static final String LOCKFILE_HASH_SEGMENT_PREFIX = "|lock:";
  private static final int HASH_PREVIEW_LENGTH = 12;
  private static final String HASH_PREVIEW_SUFFIX = "...";
  private static final String ARGUMENT_NULL_MESSAGE_KEY = "infra.common.error.argument_null";

  private final String applicationVersion;

  private final String configHash;

  private final String lockfileHash;

  private final String combinedHash;

  public VersionInfo(
      final String applicationVersion, final String configHash, final String lockfileHash) {
    this.applicationVersion = normalizeApplicationVersion(applicationVersion);
    this.configHash = normalizeConfigHash(configHash);
    this.lockfileHash = lockfileHash;
    this.combinedHash = computeCombinedHash();
  }

  /** Creates an empty info when version checking is disabled. */
  public static VersionInfo empty() {
    return new VersionInfo(UNKNOWN_VERSION, "", null);
  }

  public String getApplicationVersion() {
    return applicationVersion;
  }

  public String getConfigHash() {
    return configHash;
  }

  public String getLockfileHash() {
    return lockfileHash;
  }

  /** Combined hash used for version-aware cache keys. */
  public String getCombinedHash() {
    return combinedHash;
  }

  public boolean hasVersion() {
    return !UNKNOWN_VERSION.equals(applicationVersion);
  }

  @Override
  public String toString() {
    return "VersionInfo{"
        + "appVersion='"
        + applicationVersion
        + '\''
        + ", configHash='"
        + truncateHash(configHash)
        + '\''
        + (lockfileHash != null ? ", lockHash='" + truncateHash(lockfileHash) + '\'' : "")
        + ", combinedHash='"
        + truncateHash(combinedHash)
        + '\''
        + '}';
  }

  private String computeCombinedHash() {
    return computeHash(buildCombinedHashSource());
  }

  private String buildCombinedHashSource() {
    final StringBuilder builder = new StringBuilder();
    builder.append(APP_VERSION_SEGMENT_PREFIX).append(applicationVersion);
    builder.append(CONFIG_HASH_SEGMENT_PREFIX).append(configHash);
    if (lockfileHash != null) {
      builder.append(LOCKFILE_HASH_SEGMENT_PREFIX).append(lockfileHash);
    }
    return builder.toString();
  }

  private static String computeHash(final String content) {
    final String safeContent =
        Objects.requireNonNull(
            content,
            MessageSource.getMessage(ARGUMENT_NULL_MESSAGE_KEY, "content must not be null"));
    return Hashing.sha256()
        .hashString(safeContent, Objects.requireNonNull(StandardCharsets.UTF_8))
        .toString();
  }

  private static String normalizeApplicationVersion(final String applicationVersion) {
    if (applicationVersion == null || applicationVersion.isBlank()) {
      return UNKNOWN_VERSION;
    }
    return applicationVersion.trim();
  }

  private static String normalizeConfigHash(final String configHash) {
    return configHash == null ? "" : configHash;
  }

  private static String truncateHash(final String hash) {
    if (hash == null || hash.length() <= HASH_PREVIEW_LENGTH) {
      return hash;
    }
    return hash.substring(0, HASH_PREVIEW_LENGTH) + HASH_PREVIEW_SUFFIX;
  }
}
