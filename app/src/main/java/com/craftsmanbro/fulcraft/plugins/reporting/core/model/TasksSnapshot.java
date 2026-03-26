package com.craftsmanbro.fulcraft.plugins.reporting.core.model;

import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.util.List;

/** Snapshot of tasks loaded from tasks files (plan only, no results). */
public record TasksSnapshot(List<TaskRecord> tasks) {

  public static TasksSnapshot empty() {
    return new TasksSnapshot(List.of());
  }

  public boolean isEmpty() {
    return tasks.isEmpty();
  }
}
