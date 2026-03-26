package com.craftsmanbro.fulcraft.infrastructure.cache.impl;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Handles cache eviction policies: TTL-based and capacity-based (LRU).
 *
 * <p>This class is package-private and used only within the cache package.
 */
class CacheEvictionPolicy {

  private final ObjectMapper mapper;

  private final long ttlMillis;

  private final Integer maxSizeMb;

  private final Path cacheDir;

  private final String cacheFileName;

  /**
   * Creates an eviction policy with the specified configuration.
   *
   * @param ttlMillis TTL in milliseconds (Long.MAX_VALUE means no expiration)
   * @param maxSizeMb maximum cache size in MB (null means no limit)
   * @param cacheDir the cache directory
   * @param cacheFileName the cache file name
   * @param mapper the ObjectMapper for JSON deserialization
   */
  CacheEvictionPolicy(
      final long ttlMillis,
      final Integer maxSizeMb,
      final Path cacheDir,
      final String cacheFileName,
      final ObjectMapper mapper) {
    this.ttlMillis = ttlMillis;
    this.maxSizeMb = maxSizeMb;
    this.cacheDir = cacheDir;
    this.cacheFileName = cacheFileName;
    this.mapper = mapper;
  }

  /**
   * Returns the configured TTL in milliseconds.
   *
   * @return the TTL in milliseconds
   */
  public long getTtlMillis() {
    return ttlMillis;
  }

  /**
   * Returns whether TTL is configured.
   *
   * @return true if TTL is configured (not unlimited)
   */
  public boolean hasTtl() {
    return ttlMillis != Long.MAX_VALUE;
  }

  /**
   * Evicts all expired cache entries based on TTL.
   *
   * @param map the MVMap containing cache entries
   * @param store the MVStore for committing changes
   * @return the number of evicted entries
   */
  public int evictExpiredEntries(final MVMap<String, String> map, final MVStore store) {
    if (map == null || store == null) {
      return 0;
    }
    if (ttlMillis == Long.MAX_VALUE) {
      // No TTL configured
      return 0;
    }
    final long currentTime = System.currentTimeMillis();
    final List<String> keysToRemove = new ArrayList<>();
    for (final var entry : map.entrySet()) {
      final String key = entry.getKey();
      final String json = entry.getValue();
      if (json == null) {
        continue;
      }
      try {
        final CacheEntry cacheEntry = mapper.readValue(json, CacheEntry.class);
        if (cacheEntry.isExpired(currentTime, ttlMillis)) {
          keysToRemove.add(key);
        }
      } catch (JacksonException e) {
        keysToRemove.add(key);
        Logger.debug(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message", "Removing invalid cache entry during eviction"));
      }
    }
    for (final String key : keysToRemove) {
      map.remove(key);
    }
    if (!keysToRemove.isEmpty()) {
      store.commit();
    }
    return keysToRemove.size();
  }

  /**
   * Evicts entries based on capacity (LRU policy).
   *
   * @param map the MVMap containing cache entries
   * @param store the MVStore for committing changes
   */
  public void evictByCapacity(final MVMap<String, String> map, final MVStore store) {
    if (!isCapacityEvictionEnabled() || map == null) {
      return;
    }
    try {
      final Path dbFile = resolveStoreFilePath();
      final Optional<EvictionInfo> evictionInfo = loadEvictionInfo(dbFile);
      if (evictionInfo.isEmpty()) {
        return;
      }
      final EvictionInfo info = evictionInfo.get();
      Logger.info(
          "Cache size ("
              + (info.fileSizeBytes() / 1024 / 1024)
              + "MB) exceeds limit ("
              + maxSizeMb
              + "MB). Evicting entries...");
      final List<Map.Entry<String, CacheEntry>> entries = loadEntriesForEviction(map);
      if (entries.isEmpty()) {
        return;
      }
      // Sort by lastAccessed (LRU) - older timestamp first
      entries.sort(Comparator.comparingLong(this::resolveLastAccessed));
      final int evictedCount = evictOldest(entries, map);
      store.commit();
      Logger.info(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Evicted " + evictedCount + " entries to reduce cache size."));
    } catch (IOException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to check/evict cache capacity: " + e.getMessage()));
    }
  }

  private boolean isCapacityEvictionEnabled() {
    return maxSizeMb != null;
  }

  private Optional<EvictionInfo> loadEvictionInfo(final Path dbFile) throws IOException {
    if (!Files.exists(dbFile)) {
      return Optional.empty();
    }
    final long fileSize = Files.size(dbFile);
    final long maxSizeBytes = maxSizeMb * 1024L * 1024L;
    if (fileSize <= maxSizeBytes) {
      return Optional.empty();
    }
    return Optional.of(new EvictionInfo(fileSize));
  }

  private List<Map.Entry<String, CacheEntry>> loadEntriesForEviction(
      final MVMap<String, String> map) {
    final List<Map.Entry<String, CacheEntry>> entries = new ArrayList<>();
    for (final Map.Entry<String, String> entry : map.entrySet()) {
      parseCacheEntry(entry).ifPresent(entries::add);
    }
    return entries;
  }

  private Optional<Map.Entry<String, CacheEntry>> parseCacheEntry(
      final Map.Entry<String, String> entry) {
    try {
      final CacheEntry cacheEntry = mapper.readValue(entry.getValue(), CacheEntry.class);
      return Optional.of(Map.entry(entry.getKey(), cacheEntry));
    } catch (JacksonException e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Skipping invalid cache entry during eviction: " + e.getMessage()));
      return Optional.empty();
    }
  }

  private long resolveLastAccessed(final Map.Entry<String, CacheEntry> entry) {
    final long last = entry.getValue().getLastAccessed();
    return last > 0 ? last : entry.getValue().getTimestamp();
  }

  private int evictOldest(
      final List<Map.Entry<String, CacheEntry>> entries, final MVMap<String, String> map) {
    final int toRemove = Math.max(1, entries.size() / 5);
    int evictedCount = 0;
    for (int i = 0; i < toRemove && i < entries.size(); i++) {
      map.remove(entries.get(i).getKey());
      evictedCount++;
    }
    return evictedCount;
  }

  private Path resolveStoreFilePath() {
    final Path mvStoreFile = cacheDir.resolve(cacheFileName + ".mv.db");
    if (Files.exists(mvStoreFile)) {
      return mvStoreFile;
    }
    return cacheDir.resolve(cacheFileName);
  }

  private record EvictionInfo(long fileSizeBytes) {}
}
