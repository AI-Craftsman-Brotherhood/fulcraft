package com.craftsmanbro.fulcraft.infrastructure.io.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileEntry;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TasksFileEntry}.
 *
 * <p>Verifies factory methods and getter behavior.
 */
class TasksFileEntryTest {

  // --- forTask tests ---

  @Test
  void forTask_withValidTask_createsEntry() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.MyClass");

    TasksFileEntry entry = TasksFileEntry.forTask(task);

    assertNotNull(entry);
    assertEquals(task, entry.getTask());
    assertNull(entry.getResult());
    assertTrue(entry.hasTask());
    assertFalse(entry.hasResult());
  }

  @Test
  void forTask_withNullTask_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> TasksFileEntry.forTask(null));
  }

  // --- forResult tests ---

  @Test
  void forResult_withValidResult_createsEntry() {
    GenerationTaskResult result = new GenerationTaskResult();
    result.setClassFqn("com.example.MyClass");

    TasksFileEntry entry = TasksFileEntry.forResult(result);

    assertNotNull(entry);
    assertNull(entry.getTask());
    assertEquals(result, entry.getResult());
    assertFalse(entry.hasTask());
    assertTrue(entry.hasResult());
  }

  @Test
  void forResult_withNullResult_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> TasksFileEntry.forResult(null));
  }

  // --- forResultWithTask tests ---

  @Test
  void forResultWithTask_withValidInputs_createsEntry() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.MyClass");
    GenerationTaskResult result = new GenerationTaskResult();
    result.setClassFqn("com.example.MyClass");

    TasksFileEntry entry = TasksFileEntry.forResultWithTask(result, task);

    assertNotNull(entry);
    assertEquals(task, entry.getTask());
    assertEquals(result, entry.getResult());
    assertTrue(entry.hasTask());
    assertTrue(entry.hasResult());
  }

  @Test
  void forResultWithTask_withNullTask_throwsNullPointerException() {
    GenerationTaskResult result = new GenerationTaskResult();
    assertThrows(NullPointerException.class, () -> TasksFileEntry.forResultWithTask(result, null));
  }

  @Test
  void forResultWithTask_withNullResult_throwsNullPointerException() {
    TaskRecord task = new TaskRecord();
    assertThrows(NullPointerException.class, () -> TasksFileEntry.forResultWithTask(null, task));
  }
}
