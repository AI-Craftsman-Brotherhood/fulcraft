package com.craftsmanbro.fulcraft.plugins.reporting.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportHistory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportHistoryStoreTest {

  @TempDir Path tempDir;

  @Test
  void loadHistory_returnsEmptyWhenFileMissing() {
    ReportHistoryStore store = new ReportHistoryStore();

    ReportHistory history = store.loadHistory(tempDir);

    assertNotNull(history);
    assertTrue(history.isEmpty());
  }

  @Test
  void saveHistory_roundTripsData() {
    ReportHistoryStore store = new ReportHistoryStore();
    ReportHistory history = new ReportHistory();
    history.addRun(new ReportHistory.HistoricalRun("run-1", 123L, 10, 7, 2, 1, 0.7));

    store.saveHistory(tempDir, history);

    ReportHistory loaded = store.loadHistory(tempDir);
    assertEquals(1, loaded.size());
    ReportHistory.HistoricalRun run = loaded.getRuns().get(0);
    assertEquals("run-1", run.runId());
    assertEquals(10, run.totalTasks());
  }

  @Test
  void loadHistory_returnsEmptyOnInvalidJson() throws Exception {
    ReportHistoryStore store = new ReportHistoryStore();
    Path historyFile = tempDir.resolve("report_history.json");
    Files.writeString(historyFile, "not-json");

    ReportHistory loaded = store.loadHistory(tempDir);

    assertNotNull(loaded);
    assertTrue(loaded.isEmpty());
  }

  @Test
  void getHistoryFilePath_returnsDefaultLocation() {
    ReportHistoryStore store = new ReportHistoryStore();

    assertEquals(tempDir.resolve("report_history.json"), store.getHistoryFilePath(tempDir));
  }

  @Test
  void saveHistory_swallowsIoFailureWhenLogsRootIsFile() throws Exception {
    ReportHistoryStore store = new ReportHistoryStore();
    Path fileLogsRoot = tempDir.resolve("not-a-dir");
    Files.writeString(fileLogsRoot, "x");
    ReportHistory history = new ReportHistory();
    history.addRun(new ReportHistory.HistoricalRun("run-io", 1L, 1, 1, 0, 0, 1.0));

    assertDoesNotThrow(() -> store.saveHistory(fileLogsRoot, history));
    assertTrue(Files.isRegularFile(fileLogsRoot));
  }

  @Test
  void methods_rejectNullArguments() {
    ReportHistoryStore store = new ReportHistoryStore();
    ReportHistory history = new ReportHistory();

    assertThrows(NullPointerException.class, () -> store.loadHistory(null));
    assertThrows(NullPointerException.class, () -> store.saveHistory(null, history));
    assertThrows(NullPointerException.class, () -> store.saveHistory(tempDir, null));
    assertThrows(NullPointerException.class, () -> store.getHistoryFilePath(null));
  }
}
