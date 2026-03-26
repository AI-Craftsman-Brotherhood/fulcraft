package com.craftsmanbro.fulcraft.plugins.reporting.taskio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import org.junit.jupiter.api.Test;

class TaskEntryTest {

  @Test
  void hasTask_returnsTrue_whenTaskPresent() {
    TaskRecord task = new TaskRecord();
    task.setTaskId("t-1");

    TaskEntry entry = new TaskEntry(task);

    assertTrue(entry.hasTask());
    assertSame(task, entry.task());
  }

  @Test
  void hasTask_returnsFalse_whenTaskMissing() {
    TaskEntry entry = new TaskEntry(null);

    assertFalse(entry.hasTask());
    assertNull(entry.task());
  }
}
