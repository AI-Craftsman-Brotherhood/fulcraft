package com.craftsmanbro.fulcraft.infrastructure.io.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileReader;
import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileEntry;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests for {@link JsonTasksFileFormat}.
 *
 * <p>Verifies JSON reading and writing of tasks.
 */
class JsonTasksFileFormatTest {

  @TempDir Path tempDir;

  private JsonTasksFileFormat format;

  @BeforeEach
  void setUp() {
    format = new JsonTasksFileFormat(new ObjectMapper());
  }

  // --- Constructor tests ---

  @Test
  void constructor_withNullMapper_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new JsonTasksFileFormat(null));
  }

  // --- read tests ---

  @Test
  void read_withNullPath_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> format.read(null));
  }

  @Test
  void read_withValidJsonFile_returnsReader() throws IOException {
    Path jsonFile = tempDir.resolve("tasks.json");
    Files.writeString(
        jsonFile,
        """
            {
              "tasks": [
                {"class_fqn": "com.example.MyClass", "method_name": "testMethod"}
              ],
              "results": []
            }
            """);

    try (TasksFileReader reader = format.read(jsonFile)) {
      assertNotNull(reader);
      int count = 0;
      for (TasksFileEntry entry : reader) {
        assertTrue(entry.hasTask());
        assertEquals("com.example.MyClass", entry.getTask().getClassFqn());
        count++;
      }
      assertEquals(1, count);
    }
  }

  @Test
  void read_withEmptyTasksArray_returnsEmptyReader() throws IOException {
    Path jsonFile = tempDir.resolve("empty.json");
    Files.writeString(jsonFile, "{\"tasks\": [], \"results\": []}");

    try (TasksFileReader reader = format.read(jsonFile)) {
      assertNotNull(reader);
      assertFalse(reader.iterator().hasNext());
    }
  }

  @Test
  void read_withNonExistentFile_throwsIOException() {
    Path nonExistent = tempDir.resolve("nonexistent.json");

    assertThrows(IOException.class, () -> format.read(nonExistent));
  }

  // --- write tests ---

  @Test
  void write_withNullPath_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> format.write(List.of(), List.of(), null));
  }

  @Test
  void write_withEmptyLists_createsValidJson() throws IOException {
    Path outputFile = tempDir.resolve("output.json");

    format.write(List.of(), List.of(), outputFile);

    assertTrue(Files.exists(outputFile));
    String content = Files.readString(outputFile);
    assertTrue(content.contains("\"tasks\""));
    assertTrue(content.contains("\"results\""));
  }

  @Test
  void write_withTasks_writesTasksToJson() throws IOException {
    Path outputFile = tempDir.resolve("output.json");
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.TestClass");
    task.setMethodName("testMethod");

    format.write(List.of(task), List.of(), outputFile);

    String content = Files.readString(outputFile);
    assertTrue(content.contains("com.example.TestClass"));
    assertTrue(content.contains("testMethod"));
  }

  @Test
  void write_withNullTasksIterable_createsEmptyArray() throws IOException {
    Path outputFile = tempDir.resolve("output.json");

    format.write(null, null, outputFile);

    String content = Files.readString(outputFile);
    assertTrue(content.contains("\"tasks\""));
    assertTrue(content.contains("[]"));
  }

  // --- Round-trip tests ---

  @Test
  void roundTrip_writeThenRead_preservesData() throws IOException {
    Path file = tempDir.resolve("roundtrip.json");
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.RoundTrip");
    task.setMethodName("roundTripMethod");

    format.write(List.of(task), List.of(), file);

    try (TasksFileReader reader = format.read(file)) {
      TasksFileEntry entry = reader.iterator().next();
      assertNotNull(entry.getTask());
      assertEquals("com.example.RoundTrip", entry.getTask().getClassFqn());
      assertEquals("roundTripMethod", entry.getTask().getMethodName());
    }
  }
}
