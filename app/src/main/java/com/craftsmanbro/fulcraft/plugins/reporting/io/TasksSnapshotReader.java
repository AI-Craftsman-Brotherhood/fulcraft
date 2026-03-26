package com.craftsmanbro.fulcraft.plugins.reporting.io;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportMetadataKeys;
import com.craftsmanbro.fulcraft.plugins.reporting.core.model.TasksSnapshot;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.DefaultTaskEntriesSource;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads tasks snapshots from tasks files (plan only, no results). */
public class TasksSnapshotReader {

  private final TaskEntriesSource entriesSource;

  public TasksSnapshotReader() {
    this(new DefaultTaskEntriesSource());
  }

  TasksSnapshotReader(final TaskEntriesSource entriesSource) {
    this.entriesSource =
        java.util.Objects.requireNonNull(
            entriesSource,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "entriesSource"));
  }

  public TasksSnapshot load(final RunContext context) {
    final Path tasksFile = resolveTasksFile(context);
    if (tasksFile == null) {
      return TasksSnapshot.empty();
    }
    final List<TaskRecord> tasks = new ArrayList<>();
    try (var reader = entriesSource.read(tasksFile)) {
      for (final var entry : reader) {
        if (entry.hasTask()) {
          tasks.add(entry.task());
        }
      }
    } catch (IOException | RuntimeException e) {
      Logger.warn(
          MessageSource.getMessage("report.tasks_snapshot.read_failed", tasksFile, e.getMessage()));
      return TasksSnapshot.empty();
    }
    if (tasks.isEmpty()) {
      return TasksSnapshot.empty();
    }
    return new TasksSnapshot(tasks);
  }

  private Path resolveTasksFile(final RunContext context) {
    final Path override =
        context.getMetadata(ReportMetadataKeys.TASKS_FILE_OVERRIDE, Path.class).orElse(null);
    if (override != null) {
      return override;
    }
    final Path logsDir =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId()).planDir();
    return entriesSource.resolveExistingTasksFile(logsDir);
  }
}
