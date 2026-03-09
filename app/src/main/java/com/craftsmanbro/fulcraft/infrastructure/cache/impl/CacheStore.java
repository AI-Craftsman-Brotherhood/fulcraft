package com.craftsmanbro.fulcraft.infrastructure.cache.impl;

import com.craftsmanbro.fulcraft.config.Config.CacheConfig;
import com.craftsmanbro.fulcraft.infrastructure.cache.contract.CacheStorePort;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.version.model.VersionInfo;
import com.google.common.base.CharMatcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/** Manages caching of generated test code to avoid redundant generations. */
public class CacheStore implements CacheStorePort {

  private static final String CACHE_FILE_NAME = "generation_cache";

  private static final int MAX_TASK_ID_LOG_LENGTH = 100;

  private final Path cacheDir;

  private final ObjectMapper mapper;

  private final CacheEncryptor encryptor;

  private final CacheKeyBuilder keyBuilder;

  private final CacheEvictionPolicy evictionPolicy;

  private final boolean evictOnInit;

  private MVStore store;

  private MVMap<String, String> map;

  /**
   * Creates the cache store manager.
   *
   * <p>Note: This does not load the cache from disk. Call {@link #initialize()} explicitly to load
   * the cache.
   *
   * @param projectRoot the root directory of the project
   */
  public CacheStore(final Path projectRoot) {
    this(projectRoot, null, null);
  }

  /**
   * Creates the cache store manager with optional cache configuration.
   *
   * <p>Note: This does not load the cache from disk. Call {@link #initialize()} explicitly to load
   * the cache.
   *
   * @param projectRoot the root directory of the project
   * @param cacheConfig optional cache configuration for TTL settings
   */
  public CacheStore(final Path projectRoot, final CacheConfig cacheConfig) {
    this(projectRoot, cacheConfig, null);
  }

  /**
   * Creates the cache store manager with cache configuration and version info.
   *
   * <p>Note: This does not load the cache from disk. Call {@link #initialize()} explicitly to load
   * the cache.
   *
   * @param projectRoot the root directory of the project
   * @param cacheConfig optional cache configuration for TTL and version check settings
   * @param versionInfo optional version info for cache key version hashing
   */
  public CacheStore(
      final Path projectRoot, final CacheConfig cacheConfig, final VersionInfo versionInfo) {
    final Path root =
        Objects.requireNonNull(
            projectRoot,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "projectRoot must not be null"));
    this.cacheDir = root.resolve(".ful").resolve("cache");
    this.mapper =
        tools.jackson.databind.json.JsonMapper.builderWithJackson2Defaults()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
    // Initialize configuration values
    long ttlMillis = Long.MAX_VALUE;
    boolean evictOnInitFlag = true;
    boolean versionCheckEnabled = false;
    boolean encrypt = false;
    String encryptionKey = null;
    Integer maxSizeMb = null;
    if (cacheConfig != null) {
      ttlMillis = cacheConfig.getTtlMillis();
      evictOnInitFlag = Boolean.TRUE.equals(cacheConfig.isEvictOnInit());
      versionCheckEnabled = Boolean.TRUE.equals(cacheConfig.isVersionCheck());
      encrypt = Boolean.TRUE.equals(cacheConfig.isEncrypt());
      maxSizeMb = cacheConfig.getMaxSizeMb();
      if (encrypt) {
        final String keyEnv = cacheConfig.getEncryptionKeyEnv();
        encryptionKey = System.getenv(keyEnv);
        if (encryptionKey == null || encryptionKey.isBlank()) {
          Logger.warn(
              "Encryption enabled but no key found in environment variable: "
                  + keyEnv
                  + ". Cache will NOT be encrypted/decrypted.");
          encrypt = false;
        }
      }
    }
    this.evictOnInit = evictOnInitFlag;
    // Initialize encryptor
    if (encrypt) {
      this.encryptor = CacheEncryptor.withKey(encryptionKey);
    } else {
      this.encryptor = CacheEncryptor.disabled();
    }
    // Initialize key builder
    if (versionInfo != null && versionCheckEnabled) {
      this.keyBuilder = CacheKeyBuilder.withVersionCheck(versionInfo.getCombinedHash());
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Cache version check enabled, versionHash: " + versionInfo));
    } else {
      this.keyBuilder = CacheKeyBuilder.withoutVersionCheck();
    }
    // Initialize eviction policy
    this.evictionPolicy =
        new CacheEvictionPolicy(ttlMillis, maxSizeMb, cacheDir, CACHE_FILE_NAME, mapper);
  }

  /** Initializes the cache store by opening the MVStore. */
  @Override
  public synchronized void initialize() {
    try {
      if (store != null && !store.isClosed()) {
        // Already initialized
        return;
      }
      ensureCacheDirExists();
      openStore();
      this.map = store.openMap(CACHE_FILE_NAME);
      // Evict expired entries on initialization if configured
      if (evictOnInit && evictionPolicy.hasTtl()) {
        final int evictedCount = evictionPolicy.evictExpiredEntries(map, store);
        if (evictedCount > 0) {
          Logger.info(
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "infra.common.log.message",
                  "Evicted " + evictedCount + " expired cache entries on initialization"));
        }
      }
      // Check capacity
      evictionPolicy.evictByCapacity(map, store);
    } catch (IOException | RuntimeException e) {
      closeSafely();
      Logger.error(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Failed to initialize cache store at " + cacheDir),
          e);
      throw new IllegalStateException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to initialize cache store"),
          e);
    }
  }

  private void ensureCacheDirExists() throws IOException {
    if (Files.exists(cacheDir)) {
      if (!Files.isDirectory(cacheDir)) {
        throw new IllegalStateException(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.message",
                "Cache path exists but is not a directory: " + cacheDir));
      }
    } else {
      Files.createDirectories(cacheDir);
    }
  }

  private void openStore() {
    final Path storePath = cacheDir.resolve(CACHE_FILE_NAME);
    try {
      this.store = MVStore.open(storePath.toString());
    } catch (org.h2.mvstore.MVStoreException e) {
      if (e.getMessage() != null && e.getMessage().contains("corrupted")) {
        recoverCorruptedStore(storePath);
      } else {
        throw e;
      }
    }
  }

  private void recoverCorruptedStore(final Path storePath) {
    Logger.warn(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "Cache file corrupted, recreating: " + storePath));
    try {
      Files.deleteIfExists(storePath);
      Files.deleteIfExists(cacheDir.resolve(CACHE_FILE_NAME + ".lock"));
    } catch (IOException deleteEx) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to delete corrupted cache file: " + deleteEx.getMessage()));
    }
    this.store = MVStore.open(storePath.toString());
  }

  /**
   * Evicts all expired cache entries.
   *
   * @return the number of evicted entries
   */
  @Override
  public synchronized int evictExpiredEntries() {
    if (store == null || map == null) {
      return 0;
    }
    return evictionPolicy.evictExpiredEntries(map, store);
  }

  /** Closes the cache store. */
  @Override
  public synchronized void close() {
    closeSafely();
  }

  private void closeSafely() {
    final MVStore currentStore = this.store;
    this.store = null;
    this.map = null;
    if (currentStore != null && !currentStore.isClosed()) {
      try (currentStore) {
        try {
          currentStore.commit();
        } catch (Exception e) {
          Logger.warn(
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "infra.common.log.message", "Failed to commit cache on close"),
              e);
        }
      } catch (Exception e) {
        Logger.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message", "Failed to close cache store"),
            e);
      }
    }
  }

  /** Persists the current cache to disk. */
  @Override
  public synchronized void saveCache() {
    if (store != null && !store.isClosed()) {
      try {
        store.commit();
      } catch (Exception e) {
        Logger.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message", "Failed to commit cache"),
            e);
      }
    }
  }

  /**
   * Retrieves cached code ensuring all keys matches.
   *
   * @param taskId the unique identifier for the task
   * @param sourceCode the source code content
   * @param promptTemplate the prompt template used
   * @param configKey the configuration key (e.g. model name)
   * @return an Optional containing the cached code if found and matching, otherwise empty
   */
  @Override
  public synchronized Optional<String> getCachedCode(
      final String taskId,
      final String sourceCode,
      final String promptTemplate,
      final String configKey) {
    Objects.requireNonNull(
        taskId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "taskId must not be null"));
    Objects.requireNonNull(
        sourceCode,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "sourceCode must not be null"));
    Objects.requireNonNull(
        promptTemplate,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "promptTemplate must not be null"));
    Objects.requireNonNull(
        configKey,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "configKey must not be null"));
    if (store == null || map == null) {
      throw new IllegalStateException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "CacheStore not initialized"));
    }
    final String currentSourceHash = keyBuilder.computeHash(sourceCode);
    final String currentPromptHash = keyBuilder.computeHash(promptTemplate);
    final String key = keyBuilder.makeKey(taskId, currentSourceHash, currentPromptHash, configKey);
    final String json = map.get(key);
    if (json == null) {
      return Optional.empty();
    }
    final CacheEntry entry;
    try {
      entry = mapper.readValue(json, CacheEntry.class);
    } catch (JacksonException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to deserialize cache entry for task: " + sanitizeTaskId(taskId)),
          e);
      removeEntryAndCommit(key);
      return Optional.empty();
    }
    // Check TTL expiration
    if (entry.isExpired(System.currentTimeMillis(), evictionPolicy.getTtlMillis())) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Cache entry expired for task: " + sanitizeTaskId(taskId)));
      removeEntryAndCommit(key);
      return Optional.empty();
    }
    final String currentConfigHash = keyBuilder.computeHash(configKey);
    if (entry.isValid()
        && Objects.equals(entry.getSourceHash(), currentSourceHash)
        && Objects.equals(entry.getPromptHash(), currentPromptHash)
        && Objects.equals(entry.getConfigHash(), currentConfigHash)) {
      final String decryptedCode = encryptor.decrypt(entry.getGeneratedCode());
      if (decryptedCode == null) {
        Logger.debug(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message",
                "Cache entry decryption failed for task: " + sanitizeTaskId(taskId)));
        removeEntryAndCommit(key);
        return Optional.empty();
      }
      // Update lastAccessed
      entry.setLastAccessed(System.currentTimeMillis());
      try {
        final String updatedJson = mapper.writeValueAsString(entry);
        map.put(key, updatedJson);
        store.commit();
      } catch (JacksonException e) {
        Logger.debug(
            "Failed to update cache metadata for task: "
                + sanitizeTaskId(taskId)
                + ": "
                + e.getMessage());
      }
      return Optional.of(decryptedCode);
    }
    // Mismatch found, clean up
    if (Objects.equals(map.get(key), json)) {
      removeEntryAndCommit(key);
    }
    return Optional.empty();
  }

  /**
   * Updates the cache with new generated code.
   *
   * @param taskId the unique identifier for the task
   * @param sourceCode the source code content
   * @param promptTemplate the prompt template used
   * @param configKey the configuration key
   * @param generatedCode the generated code to cache
   */
  @Override
  public synchronized void putCache(
      final String taskId,
      final String sourceCode,
      final String promptTemplate,
      final String configKey,
      final String generatedCode) {
    Objects.requireNonNull(
        taskId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "taskId must not be null"));
    Objects.requireNonNull(
        sourceCode,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "sourceCode must not be null"));
    Objects.requireNonNull(
        promptTemplate,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "promptTemplate must not be null"));
    Objects.requireNonNull(
        configKey,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "configKey must not be null"));
    Objects.requireNonNull(
        generatedCode,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "generatedCode must not be null"));
    if (store == null || map == null) {
      throw new IllegalStateException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "CacheStore not initialized"));
    }
    evictionPolicy.evictByCapacity(map, store);
    final String sourceHash = keyBuilder.computeHash(sourceCode);
    final String promptHash = keyBuilder.computeHash(promptTemplate);
    final String configHash = keyBuilder.computeHash(configKey);
    final String encryptedCode = encryptor.encrypt(generatedCode);
    final CacheEntry entry = CacheEntry.of(sourceHash, promptHash, configHash, encryptedCode);
    try {
      final String json = mapper.writeValueAsString(entry);
      final String key = keyBuilder.makeKey(taskId, sourceHash, promptHash, configKey);
      map.put(key, json);
      store.commit();
      // The store file size is updated on commit, so capacity checks must run again after write.
      evictionPolicy.evictByCapacity(map, store);
    } catch (JacksonException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to serialize cache entry for task: " + sanitizeTaskId(taskId)),
          e);
    } catch (RuntimeException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Failed to write to cache store"),
          e);
      closeSafely();
    }
  }

  /**
   * Returns the configured TTL in milliseconds.
   *
   * @return the TTL in milliseconds, or Long.MAX_VALUE if no TTL is configured
   */
  @Override
  public long getTtlMillis() {
    return evictionPolicy.getTtlMillis();
  }

  /**
   * Returns whether TTL is configured.
   *
   * @return true if TTL is configured (not unlimited)
   */
  @Override
  public boolean hasTtl() {
    return evictionPolicy.hasTtl();
  }

  /**
   * Invalidates a specific cache entry.
   *
   * @param taskId the task ID
   * @param sourceCode the source code content
   * @param promptTemplate the prompt template used
   * @param configKey the configuration key
   */
  @Override
  public synchronized void invalidateEntry(
      final String taskId,
      final String sourceCode,
      final String promptTemplate,
      final String configKey) {
    if (store == null || map == null) {
      return;
    }
    final String sourceHash = keyBuilder.computeHash(sourceCode);
    final String promptHash = keyBuilder.computeHash(promptTemplate);
    final String key = keyBuilder.makeKey(taskId, sourceHash, promptHash, configKey);
    if (map.containsKey(key)) {
      removeEntryAndCommit(key);
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Invalidated cache entry for task: " + sanitizeTaskId(taskId)));
    }
  }

  /**
   * Returns whether version checking is enabled.
   *
   * @return true if version checking is enabled
   */
  @Override
  public boolean isVersionCheckEnabled() {
    return keyBuilder.isVersionCheckEnabled();
  }

  /**
   * Returns the computed version hash.
   *
   * @return the version hash, or empty string if not computed
   */
  @Override
  public String getVersionHash() {
    return keyBuilder.getVersionHash();
  }

  private void removeEntryAndCommit(final String key) {
    map.remove(key);
    store.commit();
  }

  private String sanitizeTaskId(final String taskId) {
    if (taskId == null) {
      return "null";
    }
    final String sanitized = CharMatcher.javaIsoControl().replaceFrom(taskId, '_');
    if (sanitized.length() > MAX_TASK_ID_LOG_LENGTH) {
      return sanitized.substring(0, MAX_TASK_ID_LOG_LENGTH) + "...";
    }
    return sanitized;
  }
}
