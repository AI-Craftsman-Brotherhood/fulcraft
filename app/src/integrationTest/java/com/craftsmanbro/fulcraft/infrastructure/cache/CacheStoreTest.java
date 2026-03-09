package com.craftsmanbro.fulcraft.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.cache.impl.CacheStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheStoreTest {

  @TempDir Path tempDir;

  private CacheStore cacheStore;

  @BeforeEach
  void setUp() {
    cacheStore = new CacheStore(tempDir);
    cacheStore.initialize();
  }

  @AfterEach
  void tearDown() {
    if (cacheStore != null) {
      cacheStore.close();
    }
  }

  @Test
  void testPutAndGetCachedCode() {
    String taskId = "task-001";
    String sourceCode = "public class Foo { void bar() {} }";
    String promptTemplate = "Generate test for ${method}";
    String configKey = "gpt-4";
    String generatedCode = "public class FooTest { @Test void testBar() {} }";

    cacheStore.putCache(taskId, sourceCode, promptTemplate, configKey, generatedCode);

    Optional<String> cached =
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey);

    assertTrue(cached.isPresent());
    assertEquals(generatedCode, cached.get());
  }

  @Test
  void testCacheMiss() {
    String taskId = "task-nonexistent";
    String sourceCode = "public class Foo {}";
    String promptTemplate = "template";
    String configKey = "model";

    Optional<String> cached =
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey);

    assertFalse(cached.isPresent());
  }

  @Test
  void testCacheMismatchOnSourceChange() {
    String taskId = "task-002";
    String sourceCodeOriginal = "public class Foo { void bar() {} }";
    String sourceCodeModified = "public class Foo { void bar() { doSomething(); } }";
    String promptTemplate = "template";
    String configKey = "model";
    String generatedCode = "generated test code";

    cacheStore.putCache(taskId, sourceCodeOriginal, promptTemplate, configKey, generatedCode);

    // Try to retrieve with modified source code - should miss
    Optional<String> cached =
        cacheStore.getCachedCode(taskId, sourceCodeModified, promptTemplate, configKey);

    assertFalse(cached.isPresent());
  }

  @Test
  void testCacheMismatchOnPromptChange() {
    String taskId = "task-003";
    String sourceCode = "public class Foo {}";
    String promptTemplateOriginal = "Generate test v1";
    String promptTemplateModified = "Generate test v2";
    String configKey = "model";
    String generatedCode = "generated test code";

    cacheStore.putCache(taskId, sourceCode, promptTemplateOriginal, configKey, generatedCode);

    Optional<String> cached =
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplateModified, configKey);

    assertFalse(cached.isPresent());
  }

  @Test
  void testCacheMismatchOnConfigChange() {
    String taskId = "task-004";
    String sourceCode = "public class Foo {}";
    String promptTemplate = "template";
    String configKeyOriginal = "gpt-4";
    String configKeyModified = "gpt-3.5";
    String generatedCode = "generated test code";

    cacheStore.putCache(taskId, sourceCode, promptTemplate, configKeyOriginal, generatedCode);

    Optional<String> cached =
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKeyModified);

    assertFalse(cached.isPresent());
  }

  @Test
  void testInvalidateEntry() {
    String taskId = "task-005";
    String sourceCode = "public class Foo {}";
    String promptTemplate = "template";
    String configKey = "model";
    String generatedCode = "generated test code";

    cacheStore.putCache(taskId, sourceCode, promptTemplate, configKey, generatedCode);

    // Verify cache hit
    assertTrue(cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey).isPresent());

    // Invalidate
    cacheStore.invalidateEntry(taskId, sourceCode, promptTemplate, configKey);

    // Verify cache miss after invalidation
    assertFalse(
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey).isPresent());
  }

  @Test
  void testSaveCache() {
    String taskId = "task-006";
    String sourceCode = "public class Foo {}";
    String promptTemplate = "template";
    String configKey = "model";
    String generatedCode = "generated test code";

    cacheStore.putCache(taskId, sourceCode, promptTemplate, configKey, generatedCode);
    cacheStore.saveCache();

    // Close and reopen
    cacheStore.close();
    cacheStore = new CacheStore(tempDir);
    cacheStore.initialize();

    // Verify data persisted
    Optional<String> cached =
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey);
    assertTrue(cached.isPresent());
    assertEquals(generatedCode, cached.get());
  }

  @Test
  void testMultipleEntries() {
    for (int i = 0; i < 10; i++) {
      String taskId = "task-multi-" + i;
      String sourceCode = "public class Foo" + i + " {}";
      String promptTemplate = "template" + i;
      String configKey = "model";
      String generatedCode = "generated code " + i;

      cacheStore.putCache(taskId, sourceCode, promptTemplate, configKey, generatedCode);
    }

    // Verify all entries can be retrieved
    for (int i = 0; i < 10; i++) {
      String taskId = "task-multi-" + i;
      String sourceCode = "public class Foo" + i + " {}";
      String promptTemplate = "template" + i;
      String configKey = "model";

      Optional<String> cached =
          cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey);
      assertTrue(cached.isPresent(), "Missing cache for task " + i);
      assertEquals("generated code " + i, cached.get());
    }
  }

  @Test
  void testDefaultTtlConfiguration() {
    // Default configuration should have no TTL
    assertFalse(cacheStore.hasTtl());
    assertEquals(Long.MAX_VALUE, cacheStore.getTtlMillis());
  }

  @Test
  void testVersionCheckDisabledByDefault() {
    assertFalse(cacheStore.isVersionCheckEnabled());
    assertEquals("", cacheStore.getVersionHash());
  }

  @Test
  // False positive: NPE thrown before Closeable created
  void testNullProjectRootThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new CacheStore(null);
        });
  }

  @Test
  void testNullTaskIdThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          cacheStore.getCachedCode(null, "source", "prompt", "config");
        });
  }

  @Test
  void testNullSourceCodeThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          cacheStore.getCachedCode("task", null, "prompt", "config");
        });
  }

  @Test
  void testNullPromptTemplateThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          cacheStore.getCachedCode("task", "source", null, "config");
        });
  }

  @Test
  void testNullConfigKeyThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          cacheStore.getCachedCode("task", "source", "prompt", null);
        });
  }

  @Test
  void testPutNullArgumentsThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          cacheStore.putCache(null, "source", "prompt", "config", "code");
        });
    assertThrows(
        NullPointerException.class,
        () -> {
          cacheStore.putCache("task", null, "prompt", "config", "code");
        });
    assertThrows(
        NullPointerException.class,
        () -> {
          cacheStore.putCache("task", "source", null, "config", "code");
        });
    assertThrows(
        NullPointerException.class,
        () -> {
          cacheStore.putCache("task", "source", "prompt", null, "code");
        });
    assertThrows(
        NullPointerException.class,
        () -> {
          cacheStore.putCache("task", "source", "prompt", "config", null);
        });
  }

  @Test
  void testCacheNotInitializedThrowsException() {
    try (CacheStore uninitializedStore = new CacheStore(tempDir)) {
      assertThrows(
          IllegalStateException.class,
          () -> {
            uninitializedStore.getCachedCode("task", "source", "prompt", "config");
          });
    }
  }

  @Test
  void testPutCacheNotInitializedThrowsException() {
    Path root = tempDir.resolve("uninitialized-put");
    try (CacheStore uninitializedStore = new CacheStore(root)) {
      assertThrows(
          IllegalStateException.class,
          () -> uninitializedStore.putCache("task", "source", "prompt", "config", "code"));
    }
  }

  @Test
  void testEvictExpiredEntriesWithNoTtl() {
    // Without TTL, eviction should return 0
    int evicted = cacheStore.evictExpiredEntries();
    assertEquals(0, evicted);
  }

  @Test
  void testCloseAndReinitialize() {
    String taskId = "task-reinit";
    String sourceCode = "public class Foo {}";
    String promptTemplate = "template";
    String configKey = "model";
    String generatedCode = "generated code";

    cacheStore.putCache(taskId, sourceCode, promptTemplate, configKey, generatedCode);
    cacheStore.close();

    // Reinitialize
    cacheStore = new CacheStore(tempDir);
    cacheStore.initialize();

    // Should still have the data
    Optional<String> cached =
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey);
    assertTrue(cached.isPresent());
  }

  @Test
  void testDoubleInitializeIsIdempotent() {
    // Second initialize should be a no-op
    cacheStore.initialize();
    cacheStore.initialize();

    // Store should still work
    cacheStore.putCache("task", "source", "prompt", "config", "code");
    assertTrue(cacheStore.getCachedCode("task", "source", "prompt", "config").isPresent());
  }

  @Test
  void testDoubleCloseIsIdempotent() {
    cacheStore.close();
    cacheStore.close(); // Should not throw
    // Verify store is closed - subsequent operations should fail gracefully
    assertTrue(true, "Double close did not throw exception");
  }

  @Test
  void testLargeContent() {
    String taskId = "task-large";
    String largeSource = "public class Foo { " + "void method() { int x = 1; } ".repeat(1000) + "}";
    String largePrompt = "Generate test ".repeat(100);
    String configKey = "model";
    String largeCode = "public class FooTest { @Test void test() {} }".repeat(50);

    cacheStore.putCache(taskId, largeSource, largePrompt, configKey, largeCode);

    Optional<String> cached = cacheStore.getCachedCode(taskId, largeSource, largePrompt, configKey);
    assertTrue(cached.isPresent());
    assertEquals(largeCode, cached.get());
  }

  @Test
  void testSpecialCharactersInContent() {
    String taskId = "task-special";
    String sourceCode = "public class Foo { String s = \"日本語テスト\\n\\t\"; }";
    String promptTemplate = "Generate test for ${method} with émojis 🎉";
    String configKey = "model-v1.0";
    String generatedCode = "// Test with special chars: <>&\"'";

    cacheStore.putCache(taskId, sourceCode, promptTemplate, configKey, generatedCode);

    Optional<String> cached =
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey);
    assertTrue(cached.isPresent());
    assertEquals(generatedCode, cached.get());
  }

  @Test
  void testCacheDirCreatedIfNotExists() throws IOException {
    Path newProjectRoot = tempDir.resolve("newproject");
    Files.createDirectories(newProjectRoot);

    try (CacheStore newStore = new CacheStore(newProjectRoot)) {
      newStore.initialize();

      Path cacheDir = newProjectRoot.resolve(".ful").resolve("cache");
      assertTrue(Files.exists(cacheDir));
      assertTrue(Files.isDirectory(cacheDir));
    }
  }

  @Test
  void testInitializeFailsWhenCachePathIsFile() throws IOException {
    Path projectRoot = tempDir.resolve("project-with-file-cache");
    Path fulDir = projectRoot.resolve(".ful");
    Files.createDirectories(fulDir);
    Files.createFile(fulDir.resolve("cache"));

    try (CacheStore storeWithInvalidCachePath = new CacheStore(projectRoot)) {
      assertThrows(IllegalStateException.class, storeWithInvalidCachePath::initialize);
    }
  }

  @Test
  void testInvalidateEntryOnUninitializedStoreIsNoOp() {
    Path projectRoot = tempDir.resolve("project-uninitialized-invalidate");

    try (CacheStore uninitializedStore = new CacheStore(projectRoot)) {
      assertDoesNotThrow(
          () -> uninitializedStore.invalidateEntry("task", "source", "prompt", "config"));
    }
  }

  @Test
  void testUpdateExistingEntry() {
    String taskId = "task-update";
    String sourceCode = "public class Foo {}";
    String promptTemplate = "template";
    String configKey = "model";

    String generatedCodeV1 = "generated code v1";
    String generatedCodeV2 = "generated code v2";

    cacheStore.putCache(taskId, sourceCode, promptTemplate, configKey, generatedCodeV1);
    cacheStore.putCache(taskId, sourceCode, promptTemplate, configKey, generatedCodeV2);

    Optional<String> cached =
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey);
    assertTrue(cached.isPresent());
    assertEquals(generatedCodeV2, cached.get());
  }

  @Test
  void testGetCachedCodeRemovesInvalidEntryPersistently() {
    String taskId = "task-invalid-json";
    String sourceCode = "public class Foo {}";
    String promptTemplate = "template";
    String configKey = "model";

    cacheStore.putCache(taskId, sourceCode, promptTemplate, configKey, "generated code");
    cacheStore.close();

    try (MVStore store = MVStore.open(resolveStorePath().toString())) {
      MVMap<String, String> map = store.openMap("generation_cache");
      String cacheKey = map.keySet().iterator().next();
      map.put(cacheKey, "{not-json}");
      store.commit();
    }

    cacheStore = new CacheStore(tempDir);
    cacheStore.initialize();

    assertFalse(
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey).isPresent());

    cacheStore.close();
    cacheStore = new CacheStore(tempDir);
    cacheStore.initialize();

    assertFalse(
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey).isPresent());
  }

  @Test
  void testPutCacheEvictsWhenWritePushesStoreOverCapacity() {
    cacheStore.close();
    Config.CacheConfig cacheConfig = new Config.CacheConfig();
    cacheConfig.setMaxSizeMb(1);
    cacheStore = new CacheStore(tempDir, cacheConfig);
    cacheStore.initialize();

    String taskId = "task-capacity-overflow";
    String sourceCode = "public class Foo {}";
    String promptTemplate = "template";
    String configKey = "model";
    String generatedCode = "generated test code".repeat(120_000);

    cacheStore.putCache(taskId, sourceCode, promptTemplate, configKey, generatedCode);

    assertFalse(
        cacheStore.getCachedCode(taskId, sourceCode, promptTemplate, configKey).isPresent());
  }

  private Path resolveStorePath() {
    return tempDir.resolve(".ful").resolve("cache").resolve("generation_cache");
  }
}
