package com.craftsmanbro.fulcraft.plugins.reporting.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.DefaultJsonService;
import com.craftsmanbro.fulcraft.plugins.reporting.model.FixErrorHistory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixErrorHistoryWriterTest {

  @TempDir Path tempDir;

  @Test
  void save_writesHistoryWithSanitizedFilename() throws Exception {
    FixErrorHistoryWriter writer = new FixErrorHistoryWriter(tempDir);
    FixErrorHistory history = new FixErrorHistory("task:1/invalid");
    history.addAttempt("compile", "boom");

    writer.save(history);

    Path outputFile = writer.getOutputDirectory().resolve("task_1_invalid.json");
    assertTrue(Files.exists(outputFile));

    JsonServicePort jsonService = new DefaultJsonService();
    FixErrorHistory loaded = jsonService.readFromFile(outputFile, FixErrorHistory.class);
    assertEquals("task:1/invalid", loaded.getTaskId());
    assertEquals(1, loaded.getAttempts().size());
  }

  @Test
  void save_doesNotWriteWhenTaskIdMissing() {
    FixErrorHistoryWriter writer = new FixErrorHistoryWriter(tempDir);
    FixErrorHistory history = new FixErrorHistory();
    history.setTaskId(" ");

    writer.save(history);

    assertFalse(Files.exists(writer.getOutputDirectory()));
  }

  @Test
  void getOutputDirectory_resolvesUnderLogsRoot() {
    FixErrorHistoryWriter writer = new FixErrorHistoryWriter(tempDir);

    assertEquals(tempDir.resolve("fix_errors"), writer.getOutputDirectory());
  }

  @Test
  void constructor_rejectsNullArguments() {
    JsonServicePort jsonService = new DefaultJsonService();

    assertThrows(NullPointerException.class, () -> new FixErrorHistoryWriter(null));
    assertThrows(NullPointerException.class, () -> new FixErrorHistoryWriter(tempDir, null));
    assertThrows(NullPointerException.class, () -> new FixErrorHistoryWriter(null, jsonService));
  }

  @Test
  void save_swallowsIoFailureWhenLogsRootIsFile() throws Exception {
    Path fileLogsRoot = tempDir.resolve("logs-root-file");
    Files.writeString(fileLogsRoot, "not-a-directory");

    FixErrorHistoryWriter writer = new FixErrorHistoryWriter(fileLogsRoot);
    FixErrorHistory history = new FixErrorHistory("task-1");
    history.addAttempt("compile", "boom");

    assertDoesNotThrow(() -> writer.save(history));
    assertFalse(Files.exists(fileLogsRoot.resolve("fix_errors").resolve("task-1.json")));
  }
}
