package com.craftsmanbro.fulcraft.infrastructure.cache.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CacheKeyBuilderTest {

  @Test
  void testWithoutVersionCheck() {
    CacheKeyBuilder builder = CacheKeyBuilder.withoutVersionCheck();

    assertFalse(builder.isVersionCheckEnabled());
    assertEquals("", builder.getVersionHash());
  }

  @Test
  void testWithVersionCheck() {
    String versionHash = "abc123";
    CacheKeyBuilder builder = CacheKeyBuilder.withVersionCheck(versionHash);

    assertTrue(builder.isVersionCheckEnabled());
    assertEquals(versionHash, builder.getVersionHash());
  }

  @Test
  void testWithVersionCheckNullHash() {
    CacheKeyBuilder builder = CacheKeyBuilder.withVersionCheck(null);

    assertTrue(builder.isVersionCheckEnabled());
    assertEquals("", builder.getVersionHash());
  }

  @Test
  void testComputeHash() {
    CacheKeyBuilder builder = CacheKeyBuilder.withoutVersionCheck();

    String hash = builder.computeHash("test content");

    assertNotNull(hash);
    assertEquals(64, hash.length()); // SHA-256 produces 64 hex characters
  }

  @Test
  void testComputeHashNullThrowsException() {
    CacheKeyBuilder builder = CacheKeyBuilder.withoutVersionCheck();
    assertThrows(NullPointerException.class, () -> builder.computeHash(null));
  }

  @Test
  void testComputeHashDeterministic() {
    CacheKeyBuilder builder = CacheKeyBuilder.withoutVersionCheck();

    String hash1 = builder.computeHash("same content");
    String hash2 = builder.computeHash("same content");

    assertEquals(hash1, hash2);
  }

  @Test
  void testComputeHashDifferentContent() {
    CacheKeyBuilder builder = CacheKeyBuilder.withoutVersionCheck();

    String hash1 = builder.computeHash("content A");
    String hash2 = builder.computeHash("content B");

    assertNotEquals(hash1, hash2);
  }

  @Test
  void testMakeKeyDeterministic() {
    CacheKeyBuilder builder = CacheKeyBuilder.withoutVersionCheck();

    String key1 = builder.makeKey("task1", "srcHash", "promptHash", "configKey");
    String key2 = builder.makeKey("task1", "srcHash", "promptHash", "configKey");

    assertEquals(key1, key2);
  }

  @Test
  void testMakeKeyDifferentInputs() {
    CacheKeyBuilder builder = CacheKeyBuilder.withoutVersionCheck();

    String key1 = builder.makeKey("task1", "srcHash", "promptHash", "configKey");
    String key2 = builder.makeKey("task2", "srcHash", "promptHash", "configKey");

    assertNotEquals(key1, key2);
  }

  @Test
  void testMakeKeyWithVersionCheck() {
    CacheKeyBuilder builderNoVersion = CacheKeyBuilder.withoutVersionCheck();
    CacheKeyBuilder builderWithVersion = CacheKeyBuilder.withVersionCheck("v1.0");

    String keyNoVersion = builderNoVersion.makeKey("task", "src", "prompt", "config");
    String keyWithVersion = builderWithVersion.makeKey("task", "src", "prompt", "config");

    // Keys should differ when version check is enabled
    assertNotEquals(keyNoVersion, keyWithVersion);
  }

  @Test
  void testMakeKeyVersionHashAffectsResult() {
    CacheKeyBuilder builderV1 = CacheKeyBuilder.withVersionCheck("v1.0");
    CacheKeyBuilder builderV2 = CacheKeyBuilder.withVersionCheck("v2.0");

    String keyV1 = builderV1.makeKey("task", "src", "prompt", "config");
    String keyV2 = builderV2.makeKey("task", "src", "prompt", "config");

    // Different version hashes should produce different keys
    assertNotEquals(keyV1, keyV2);
  }

  @Test
  void testMakeKeyWithEmptyVersionHashMatchesNoVersionMode() {
    CacheKeyBuilder withoutVersion = CacheKeyBuilder.withoutVersionCheck();
    CacheKeyBuilder withEmptyVersion = CacheKeyBuilder.withVersionCheck("");

    String keyWithoutVersion = withoutVersion.makeKey("task", "src", "prompt", "config");
    String keyWithEmptyVersion = withEmptyVersion.makeKey("task", "src", "prompt", "config");

    assertEquals(keyWithoutVersion, keyWithEmptyVersion);
  }
}
