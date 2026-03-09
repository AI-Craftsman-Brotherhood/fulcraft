package com.craftsmanbro.fulcraft.infrastructure.cache.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CacheEntryTest {

  @Test
  void testOfFactoryMethod() {
    CacheEntry entry = CacheEntry.of("srcHash", "promptHash", "configHash", "generatedCode");

    assertNotNull(entry);
    assertEquals("srcHash", entry.getSourceHash());
    assertEquals("promptHash", entry.getPromptHash());
    assertEquals("configHash", entry.getConfigHash());
    assertEquals("generatedCode", entry.getGeneratedCode());
    assertTrue(entry.getTimestamp() > 0);
    assertTrue(entry.getLastAccessed() > 0);
    assertEquals(entry.getTimestamp(), entry.getLastAccessed());
  }

  @Test
  void testOfFactoryMethodThrowsOnNullArguments() {
    assertThrows(
        NullPointerException.class, () -> CacheEntry.of(null, "promptHash", "configHash", "code"));
    assertThrows(
        NullPointerException.class, () -> CacheEntry.of("srcHash", null, "configHash", "code"));
    assertThrows(
        NullPointerException.class, () -> CacheEntry.of("srcHash", "promptHash", null, "code"));
    assertThrows(
        NullPointerException.class,
        () -> CacheEntry.of("srcHash", "promptHash", "configHash", null));
  }

  @Test
  void testIsValidWithAllFields() {
    CacheEntry entry = CacheEntry.of("src", "prompt", "config", "code");

    assertTrue(entry.isValid());
  }

  @Test
  void testIsValidWithNullSourceHash() {
    CacheEntry entry = new CacheEntry();
    entry.setSourceHash(null);
    entry.setPromptHash("prompt");
    entry.setConfigHash("config");
    entry.setGeneratedCode("code");

    assertFalse(entry.isValid());
  }

  @Test
  void testIsValidWithNullPromptHash() {
    CacheEntry entry = new CacheEntry();
    entry.setSourceHash("src");
    entry.setPromptHash(null);
    entry.setConfigHash("config");
    entry.setGeneratedCode("code");

    assertFalse(entry.isValid());
  }

  @Test
  void testIsValidWithNullConfigHash() {
    CacheEntry entry = new CacheEntry();
    entry.setSourceHash("src");
    entry.setPromptHash("prompt");
    entry.setConfigHash(null);
    entry.setGeneratedCode("code");

    assertFalse(entry.isValid());
  }

  @Test
  void testIsValidWithNullGeneratedCode() {
    CacheEntry entry = new CacheEntry();
    entry.setSourceHash("src");
    entry.setPromptHash("prompt");
    entry.setConfigHash("config");
    entry.setGeneratedCode(null);

    assertFalse(entry.isValid());
  }

  @Test
  void testIsExpiredWithMaxValueTtl() {
    CacheEntry entry = CacheEntry.of("src", "prompt", "config", "code");

    // Long.MAX_VALUE means no expiration
    assertFalse(entry.isExpired(System.currentTimeMillis(), Long.MAX_VALUE));
  }

  @Test
  void testIsExpiredWithZeroTimestamp() {
    CacheEntry entry = new CacheEntry();
    entry.setTimestamp(0);

    // Legacy entry with zero timestamp should not be considered expired
    assertFalse(entry.isExpired(System.currentTimeMillis(), 1000));
  }

  @Test
  void testIsExpiredWithNegativeTimestamp() {
    CacheEntry entry = new CacheEntry();
    entry.setTimestamp(-1);

    // Entry with negative timestamp should not be considered expired
    assertFalse(entry.isExpired(System.currentTimeMillis(), 1000));
  }

  @Test
  void testIsExpiredWithRecentTimestamp() {
    CacheEntry entry = CacheEntry.of("src", "prompt", "config", "code");
    long currentTime = System.currentTimeMillis();
    long ttl = 60_000; // 1 minute

    // Entry just created should not be expired
    assertFalse(entry.isExpired(currentTime, ttl));
  }

  @Test
  void testIsExpiredAtExactTtlBoundary() {
    CacheEntry entry = new CacheEntry();
    entry.setTimestamp(1_000L);

    assertFalse(entry.isExpired(2_000L, 1_000L));
    assertTrue(entry.isExpired(2_001L, 1_000L));
  }

  @Test
  void testIsExpiredWithOldTimestamp() {
    CacheEntry entry = new CacheEntry();
    entry.setTimestamp(System.currentTimeMillis() - 120_000); // 2 minutes ago

    long ttl = 60_000; // 1 minute

    // Entry older than TTL should be expired
    assertTrue(entry.isExpired(System.currentTimeMillis(), ttl));
  }

  @Test
  void testSettersAndGetters() {
    CacheEntry entry = new CacheEntry();

    entry.setSourceHash("srcHash");
    entry.setPromptHash("promptHash");
    entry.setConfigHash("configHash");
    entry.setGeneratedCode("code");
    entry.setTimestamp(1000L);
    entry.setLastAccessed(2000L);

    assertEquals("srcHash", entry.getSourceHash());
    assertEquals("promptHash", entry.getPromptHash());
    assertEquals("configHash", entry.getConfigHash());
    assertEquals("code", entry.getGeneratedCode());
    assertEquals(1000L, entry.getTimestamp());
    assertEquals(2000L, entry.getLastAccessed());
  }

  @Test
  void testDefaultConstructor() {
    CacheEntry entry = new CacheEntry();

    assertNull(entry.getSourceHash());
    assertNull(entry.getPromptHash());
    assertNull(entry.getConfigHash());
    assertNull(entry.getGeneratedCode());
    assertEquals(0L, entry.getTimestamp());
    assertEquals(0L, entry.getLastAccessed());
    assertFalse(entry.isValid());
  }
}
