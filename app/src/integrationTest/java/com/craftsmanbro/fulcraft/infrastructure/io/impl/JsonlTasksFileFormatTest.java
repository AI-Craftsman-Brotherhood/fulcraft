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
 * Tests for {@link JsonlTasksFileFormat}.
 *
 * <p>Verifies JSONL reading and writing of tasks.
 */
class JsonlTasksFileFormatTest {

  @TempDir Path tempDir;

  private JsonlTasksFileFormat format;

  @BeforeEach
  void setUp() {
    format = new JsonlTasksFileFormat(new ObjectMapper());
  }

  // --- Constructor tests ---

  @Test
  void constructor_withNullMapper_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new JsonlTasksFileFormat(null));
  }

  // --- read tests ---

  @Test
  void read_withNullPath_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> format.read((Path) null));
  }

  @Test
  void read_withValidJsonlFile_returnsReader() throws IOException {
    Path jsonlFile = tempDir.resolve("tasks.jsonl");
    Files.writeString(
        jsonlFile,
        """
            {"class_fqn": "com.example.Class1", "method_name": "method1"}
            {"class_fqn": "com.example.Class2", "method_name": "method2"}
            """);

    try (TasksFileReader reader = format.read(jsonlFile)) {
      assertNotNull(reader);
      int count = 0;
      for (TasksFileEntry entry : reader) {
        assertTrue(entry.hasTask());
        count++;
      }
      assertEquals(2, count);
    }
  }

  @Test
  void read_withEmptyFile_returnsEmptyReader() throws IOException {
    Path jsonlFile = tempDir.resolve("empty.jsonl");
    Files.writeString(jsonlFile, "");

    try (TasksFileReader reader = format.read(jsonlFile)) {
      assertNotNull(reader);
      assertFalse(reader.iterator().hasNext());
    }
  }

  @Test
  void read_withBlankLines_skipsBlankLines() throws IOException {
    Path jsonlFile = tempDir.resolve("blanks.jsonl");
    Files.writeString(
        jsonlFile,
        """
            {"class_fqn": "com.example.Class1", "method_name": "method1"}

            {"class_fqn": "com.example.Class2", "method_name": "method2"}

            """);

    try (TasksFileReader reader = format.read(jsonlFile)) {
      int count = 0;
      for (TasksFileEntry entry : reader) {
        assertTrue(entry.hasTask());
        count++;
      }
      assertEquals(2, count);
    }
  }

  @Test
  void read_withNonExistentFile_throwsIOException() {
    Path nonExistent = tempDir.resolve("nonexistent.jsonl");

    assertThrows(IOException.class, () -> format.read(nonExistent));
  }

  // --- write tests ---

  @Test
  void write_withNullPath_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> format.write(List.of(), List.of(), null));
  }

  @Test
  void write_withEmptyLists_createsEmptyFile() throws IOException {
    Path outputFile = tempDir.resolve("output.jsonl");

    format.write(List.of(), List.of(), outputFile);

    assertTrue(Files.exists(outputFile));
    assertEquals("", Files.readString(outputFile).trim());
  }

  @Test
  void write_withTasks_writesOneTaskPerLine() throws IOException {
    Path outputFile = tempDir.resolve("output.jsonl");
    TaskRecord task1 = new TaskRecord();
    task1.setClassFqn("com.example.Class1");
    task1.setMethodName("method1");
    TaskRecord task2 = new TaskRecord();
    task2.setClassFqn("com.example.Class2");
    task2.setMethodName("method2");

    format.write(List.of(task1, task2), List.of(), outputFile);

    String content = Files.readString(outputFile);
    String[] lines = content.trim().split("\n");
    assertEquals(2, lines.length);
    assertTrue(lines[0].contains("Class1"));
    assertTrue(lines[1].contains("Class2"));
  }

  @Test
  void write_withNullTasksIterable_createsEmptyFile() throws IOException {
    Path outputFile = tempDir.resolve("output.jsonl");

    format.write(null, null, outputFile);

    assertEquals("", Files.readString(outputFile).trim());
  }

  // --- Round-trip tests ---

  @Test
  void roundTrip_writeThenRead_preservesData() throws IOException {
    Path file = tempDir.resolve("roundtrip.jsonl");
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

  // --- stream() tests ---

  @Test
  void stream_returnsStreamOfEntries() throws IOException {
    Path jsonlFile = tempDir.resolve("stream.jsonl");
    Files.writeString(
        jsonlFile,
        """
            {"class_fqn": "com.example.Class1", "method_name": "method1"}
            {"class_fqn": "com.example.Class2", "method_name": "method2"}
            """);

    try (TasksFileReader reader = format.read(jsonlFile)) {
      long count = reader.stream().count();
      assertEquals(2, count);
    }
  }
}
