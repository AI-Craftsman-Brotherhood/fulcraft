package com.craftsmanbro.fulcraft.infrastructure.io.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileEntry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests for {@link StructuredTasksFileReader}.
 *
 * <p>Verifies structured tasks/results parsing and iterator behavior.
 */
class StructuredTasksFileReaderTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void constructor_withNullInputStream_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class, () -> new StructuredTasksFileReader(null, OBJECT_MAPPER));
  }

  @Test
  void constructor_withNullObjectMapper_throwsNullPointerException() {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
    assertThrows(
        NullPointerException.class, () -> new StructuredTasksFileReader(inputStream, null));
  }

  @Test
  void iterator_withTasksAndResults_readsEntriesInOrder() throws IOException {
    String content =
        """
        {
          "metadata": {"version": 1},
          "tasks": [
            {"task_id": "task-1", "class_fqn": "com.example.TaskOne", "method_name": "one"},
            {"task_id": "task-2", "class_fqn": "com.example.TaskTwo", "method_name": "two"}
          ],
          "ignored": [1, 2, 3],
          "results": [
            {
              "taskId": "task-1",
              "classFqn": "com.example.TaskOne",
              "methodName": "one",
              "status": "SUCCESS"
            }
          ]
        }
        """;

    try (StructuredTasksFileReader reader =
        new StructuredTasksFileReader(inputStream(content), OBJECT_MAPPER)) {
      List<TasksFileEntry> entries = new ArrayList<>();
      for (TasksFileEntry entry : reader) {
        entries.add(entry);
      }

      assertEquals(3, entries.size());
      assertTrue(entries.get(0).hasTask());
      assertFalse(entries.get(0).hasResult());
      assertEquals("com.example.TaskOne", entries.get(0).getTask().getClassFqn());
      assertEquals("one", entries.get(0).getTask().getMethodName());

      assertTrue(entries.get(1).hasTask());
      assertFalse(entries.get(1).hasResult());
      assertEquals("com.example.TaskTwo", entries.get(1).getTask().getClassFqn());

      assertFalse(entries.get(2).hasTask());
      assertTrue(entries.get(2).hasResult());
      assertEquals("SUCCESS", entries.get(2).getResult().getStatus());
      assertEquals("task-1", entries.get(2).getResult().getTaskId());
    }
  }

  @Test
  void iterator_withInvalidTaskEntry_skipsInvalidEntry() throws IOException {
    String content =
        """
        {
          "tasks": [
            {"class_fqn": "com.example.ValidOne", "method_name": "first"},
            {"class_fqn": {"invalid": true}, "method_name": "broken"},
            {"class_fqn": "com.example.ValidTwo", "method_name": "second"}
          ],
          "results": []
        }
        """;

    try (StructuredTasksFileReader reader =
        new StructuredTasksFileReader(inputStream(content), OBJECT_MAPPER)) {
      List<TasksFileEntry> entries = new ArrayList<>();
      for (TasksFileEntry entry : reader) {
        entries.add(entry);
      }

      assertEquals(2, entries.size());
      assertEquals("first", entries.get(0).getTask().getMethodName());
      assertEquals("second", entries.get(1).getTask().getMethodName());
    }
  }

  @Test
  void iterator_withInvalidRoot_throwsIllegalStateException() throws IOException {
    try (StructuredTasksFileReader reader =
        new StructuredTasksFileReader(inputStream("[]"), OBJECT_MAPPER)) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> reader.iterator().hasNext());
      assertTrue(exception.getMessage().contains("Failed to read tasks file entry"));
    }
  }

  @Test
  void next_whenExhausted_throwsNoSuchElementException() throws IOException {
    try (StructuredTasksFileReader reader =
        new StructuredTasksFileReader(
            inputStream("{\"tasks\":[],\"results\":[]}"), OBJECT_MAPPER)) {
      Iterator<TasksFileEntry> iterator = reader.iterator();

      assertFalse(iterator.hasNext());
      assertThrows(NoSuchElementException.class, iterator::next);
    }
  }

  @Test
  void close_closesInputStream() throws IOException {
    CloseTrackingInputStream inputStream =
        new CloseTrackingInputStream("{\"tasks\":[],\"results\":[]}");
    StructuredTasksFileReader reader = new StructuredTasksFileReader(inputStream, OBJECT_MAPPER);

    reader.close();

    assertTrue(inputStream.isClosed());
  }

  private static ByteArrayInputStream inputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  private static final class CloseTrackingInputStream extends ByteArrayInputStream {
    private boolean closed;

    private CloseTrackingInputStream(String content) {
      super(content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }

    private boolean isClosed() {
      return closed;
    }
  }
}
