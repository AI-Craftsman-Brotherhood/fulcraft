package com.craftsmanbro.fulcraft.infrastructure.io.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileReader;
import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileEntry;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tests for default methods in {@link TasksFileReader}.
 *
 * <p>Verifies stream conversion and close behavior.
 */
class TasksFileReaderTest {

  @Test
  void stream_withEntries_returnsOrderedEntriesAndClosesReader() {
    TaskRecord firstTask = new TaskRecord();
    firstTask.setClassFqn("com.example.First");
    TaskRecord secondTask = new TaskRecord();
    secondTask.setClassFqn("com.example.Second");
    try (TestTasksFileReader reader =
        new TestTasksFileReader(
            List.of(TasksFileEntry.forTask(firstTask), TasksFileEntry.forTask(secondTask)))) {

      List<TasksFileEntry> entries;
      try (Stream<TasksFileEntry> stream = reader.stream()) {
        entries = stream.toList();
      }

      assertEquals(2, entries.size());
      assertEquals("com.example.First", entries.get(0).getTask().getClassFqn());
      assertEquals("com.example.Second", entries.get(1).getTask().getClassFqn());
      assertTrue(reader.isClosed());
    }
  }

  @Test
  void stream_closeWithoutConsumption_closesReader() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.Task");
    try (TestTasksFileReader reader =
        new TestTasksFileReader(List.of(TasksFileEntry.forTask(task)))) {
      Stream<TasksFileEntry> stream = reader.stream();
      stream.close();

      assertTrue(reader.isClosed());
    }
  }

  private static final class TestTasksFileReader implements TasksFileReader {
    private final List<TasksFileEntry> entries;
    private boolean closed;

    private TestTasksFileReader(List<TasksFileEntry> entries) {
      this.entries = entries;
    }

    @Override
    public Iterator<TasksFileEntry> iterator() {
      return entries.iterator();
    }

    @Override
    public void close() {
      closed = true;
    }

    private boolean isClosed() {
      return closed;
    }
  }
}
