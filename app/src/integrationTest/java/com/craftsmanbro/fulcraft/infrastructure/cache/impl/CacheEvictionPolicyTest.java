package com.craftsmanbro.fulcraft.infrastructure.cache.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class CacheEvictionPolicyTest {

  @TempDir Path tempDir;

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
  }

  @Test
  void testHasTtl() {
    CacheEvictionPolicy policyWithTtl =
        new CacheEvictionPolicy(1000L, null, tempDir, "generation_cache", mapper);
    CacheEvictionPolicy policyWithoutTtl =
        new CacheEvictionPolicy(Long.MAX_VALUE, null, tempDir, "generation_cache", mapper);

    assertTrue(policyWithTtl.hasTtl());
    assertEquals(1000L, policyWithTtl.getTtlMillis());

    assertFalse(policyWithoutTtl.hasTtl());
    assertEquals(Long.MAX_VALUE, policyWithoutTtl.getTtlMillis());
  }

  @Test
  void testEvictExpiredEntriesRemovesExpiredAndInvalid() throws IOException {
    CacheEvictionPolicy policy =
        new CacheEvictionPolicy(50L, null, tempDir, "generation_cache", mapper);

    try (MVStore store = MVStore.open(tempDir.resolve("generation_cache").toString())) {
      MVMap<String, String> map = store.openMap("generation_cache");

      long now = System.currentTimeMillis();

      CacheEntry valid = CacheEntry.of("src", "prompt", "config", "code");
      valid.setTimestamp(now);
      valid.setLastAccessed(now);

      CacheEntry expired = CacheEntry.of("src2", "prompt2", "config2", "code2");
      expired.setTimestamp(now - 5000L);
      expired.setLastAccessed(now - 5000L);

      map.put("valid", mapper.writeValueAsString(valid));
      map.put("expired", mapper.writeValueAsString(expired));
      map.put("invalid", "{not-json}");
      store.commit();

      int evicted = policy.evictExpiredEntries(map, store);

      assertEquals(2, evicted);
      assertTrue(map.containsKey("valid"));
      assertFalse(map.containsKey("expired"));
      assertFalse(map.containsKey("invalid"));
    }
  }

  @Test
  void testEvictExpiredEntriesReturnsZeroWhenMapOrStoreIsNull() {
    CacheEvictionPolicy policy =
        new CacheEvictionPolicy(1000L, null, tempDir, "generation_cache", mapper);

    assertEquals(0, policy.evictExpiredEntries(null, null));
  }

  @Test
  void testEvictExpiredEntriesSkipsWhenTtlIsUnlimited() throws IOException {
    CacheEvictionPolicy policy =
        new CacheEvictionPolicy(Long.MAX_VALUE, null, tempDir, "generation_cache", mapper);

    try (MVStore store = MVStore.open(tempDir.resolve("generation_cache").toString())) {
      MVMap<String, String> map = store.openMap("generation_cache");
      CacheEntry oldEntry = CacheEntry.of("src", "prompt", "config", "code");
      oldEntry.setTimestamp(System.currentTimeMillis() - 100_000L);
      map.put("old", mapper.writeValueAsString(oldEntry));
      store.commit();

      int evicted = policy.evictExpiredEntries(map, store);

      assertEquals(0, evicted);
      assertTrue(map.containsKey("old"));
    }
  }

  @Test
  void testEvictByCapacityRemovesOldestEntry() throws IOException {
    CacheEvictionPolicy policy =
        new CacheEvictionPolicy(Long.MAX_VALUE, 0, tempDir, "generation_cache", mapper);

    try (MVStore store = MVStore.open(tempDir.resolve("generation_cache").toString())) {
      MVMap<String, String> map = store.openMap("generation_cache");

      long baseTime = 1_000_000L;
      for (int i = 0; i < 5; i++) {
        CacheEntry entry = CacheEntry.of("src" + i, "prompt" + i, "config" + i, "code" + i);
        entry.setTimestamp(baseTime + i);
        entry.setLastAccessed(baseTime + i);
        map.put("key-" + i, mapper.writeValueAsString(entry));
      }
      store.commit();
      assertTrue(resolveStoreFileSize() > 0);

      policy.evictByCapacity(map, store);

      assertFalse(map.containsKey("key-0"));
      assertEquals(4, map.size());
    }
  }

  @Test
  void testEvictByCapacityDoesNothingWhenDisabled() throws IOException {
    CacheEvictionPolicy policy =
        new CacheEvictionPolicy(Long.MAX_VALUE, null, tempDir, "generation_cache", mapper);

    try (MVStore store = MVStore.open(tempDir.resolve("generation_cache").toString())) {
      MVMap<String, String> map = store.openMap("generation_cache");
      CacheEntry entry = CacheEntry.of("src", "prompt", "config", "code");
      map.put("key", mapper.writeValueAsString(entry));
      store.commit();

      policy.evictByCapacity(map, store);

      assertEquals(1, map.size());
      assertTrue(map.containsKey("key"));
    }
  }

  @Test
  void testEvictByCapacityFallsBackToTimestampWhenLastAccessedIsMissing() throws IOException {
    CacheEvictionPolicy policy =
        new CacheEvictionPolicy(Long.MAX_VALUE, 0, tempDir, "generation_cache", mapper);

    try (MVStore store = MVStore.open(tempDir.resolve("generation_cache").toString())) {
      MVMap<String, String> map = store.openMap("generation_cache");

      long baseTime = 10_000L;
      for (int i = 0; i < 5; i++) {
        CacheEntry entry = CacheEntry.of("src" + i, "prompt" + i, "config" + i, "code" + i);
        entry.setTimestamp(baseTime + i);
        entry.setLastAccessed(0L); // simulate legacy entries without lastAccessed
        map.put("legacy-" + i, mapper.writeValueAsString(entry));
      }
      store.commit();
      assertTrue(resolveStoreFileSize() > 0);

      policy.evictByCapacity(map, store);

      assertFalse(map.containsKey("legacy-0"));
      assertEquals(4, map.size());
    }
  }

  private long resolveStoreFileSize() throws IOException {
    Path mvDbFile = tempDir.resolve("generation_cache.mv.db");
    Path fallback = tempDir.resolve("generation_cache");
    Path storeFile = Files.exists(mvDbFile) ? mvDbFile : fallback;
    if (!Files.exists(storeFile)) {
      return 0;
    }
    return Files.size(storeFile);
  }
}
