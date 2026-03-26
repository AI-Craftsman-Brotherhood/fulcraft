package com.craftsmanbro.fulcraft.plugins.reporting.taskio;

import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;

/** Neutral task entry for feature-layer processing (plan only, no result). */
public record TaskEntry(TaskRecord task) {

  public boolean hasTask() {
    return task != null;
  }
}
