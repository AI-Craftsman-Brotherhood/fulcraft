package com.craftsmanbro.fulcraft.infrastructure.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.usage.impl.LocalFileUsageStore;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageRecord;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageScope;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileUsageStoreTest {

  @TempDir Path tempDir;

  @Test
  void recordUsageWritesDailyAndMonthlyUsage() {
    Clock clock = Clock.fixed(Instant.parse("2024-01-15T12:00:00Z"), ZoneOffset.UTC);
    LocalFileUsageStore store = new LocalFileUsageStore(tempDir, clock);

    store.recordUsage(UsageScope.PROJECT, 2, 10);
    store.recordUsage(UsageScope.PROJECT, 1, 4);

    UsageSnapshot snapshot = store.getSnapshot();
    UsageSnapshot.ScopeUsage scopeUsage = snapshot.getScopes().get("project");

    assertNotNull(scopeUsage);
    UsageRecord day = scopeUsage.getDay().get("2024-01-15");
    UsageRecord month = scopeUsage.getMonth().get("2024-01");
    assertNotNull(day);
    assertNotNull(month);
    assertEquals(3, day.getRequestCount());
    assertEquals(14, day.getTokenCount());
    assertEquals(3, month.getRequestCount());
    assertEquals(14, month.getTokenCount());
  }

  @Test
  void recordUsageIgnoresNullScope() {
    LocalFileUsageStore store = new LocalFileUsageStore(tempDir);

    store.recordUsage(null, 1, 1);

    Path usageFile = tempDir.resolve(".ful").resolve("usage.json");
    assertFalse(Files.exists(usageFile));
  }

  @Test
  void recordUsageIgnoresZeroCounts() {
    LocalFileUsageStore store = new LocalFileUsageStore(tempDir);

    store.recordUsage(UsageScope.USER, 0, 0);

    Path usageFile = tempDir.resolve(".ful").resolve("usage.json");
    assertFalse(Files.exists(usageFile));
  }

  @Test
  void recordUsageClampsNegativeDeltas() {
    Clock clock = Clock.fixed(Instant.parse("2024-01-15T12:00:00Z"), ZoneOffset.UTC);
    LocalFileUsageStore store = new LocalFileUsageStore(tempDir, clock);

    store.recordUsage(UsageScope.USER, -5, 7);

    UsageSnapshot snapshot = store.getSnapshot();
    UsageSnapshot.ScopeUsage scopeUsage = snapshot.getScopes().get("user");
    UsageRecord day = scopeUsage.getDay().get("2024-01-15");

    assertEquals(0, day.getRequestCount());
    assertEquals(7, day.getTokenCount());
  }

  @Test
  void getSnapshotBacksUpCorruptedFile() throws Exception {
    LocalFileUsageStore store = new LocalFileUsageStore(tempDir);
    Path usageFile = tempDir.resolve(".ful").resolve("usage.json");
    Files.createDirectories(usageFile.getParent());
    Files.writeString(usageFile, "{ invalid");

    UsageSnapshot snapshot = store.getSnapshot();

    Path backupFile = usageFile.resolveSibling("usage.json.corrupted");
    assertTrue(snapshot.getScopes().isEmpty());
    assertTrue(Files.exists(backupFile));
    assertFalse(Files.exists(usageFile));
  }
}
