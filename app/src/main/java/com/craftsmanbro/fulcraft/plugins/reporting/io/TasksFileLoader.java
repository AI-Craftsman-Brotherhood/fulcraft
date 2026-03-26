package com.craftsmanbro.fulcraft.plugins.reporting.io;

import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.DefaultTaskEntriesSource;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Loads tasks from tasks files for reporting. */
public class TasksFileLoader {

  private final TaskEntriesSource entriesSource;

  public TasksFileLoader() {
    this.entriesSource = null;
  }

  TasksFileLoader(final TaskEntriesSource entriesSource) {
    this.entriesSource = entriesSource;
  }

  /** Loads tasks from a tasks file (json/yaml/jsonl). */
  public List<TaskRecord> loadTasks(final Path tasksFile) throws IOException {
    java.util.Objects.requireNonNull(
        tasksFile,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "tasksFile must not be null"));
    final TaskEntriesSource source = resolveSource();
    try (var reader = source.read(tasksFile)) {
      final var tasks = new ArrayList<TaskRecord>();
      final Set<String> seen = new HashSet<>();
      for (final var entry : reader) {
        if (entry.hasTask()) {
          addTaskIfNew(entry.task(), tasks, seen);
        }
      }
      return List.copyOf(tasks);
    }
  }

  /**
   * Loads tasks from a BufferedReader (JSONL only).
   *
   * <p>This overload is provided for better testability (D4).
   */
  public List<TaskRecord> loadTasks(final BufferedReader reader) throws IOException {
    java.util.Objects.requireNonNull(
        reader,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "reader must not be null"));
    final TaskEntriesSource source = resolveSource();
    final var tasks = new ArrayList<TaskRecord>();
    final Set<String> seen = new HashSet<>();
    try (var jsonlReader = source.readJsonl(reader)) {
      for (final var entry : jsonlReader) {
        if (entry.hasTask()) {
          addTaskIfNew(entry.task(), tasks, seen);
        }
      }
    }
    // Return an immutable list to ensure safety
    return List.copyOf(tasks);
  }

  private static void addTaskIfNew(
      final TaskRecord task, final List<TaskRecord> tasks, final Set<String> seen) {
    if (task == null) {
      return;
    }
    final String key = buildTaskKey(task);
    if (key == null) {
      tasks.add(task);
      return;
    }
    if (seen.add(key)) {
      tasks.add(task);
    }
  }

  private static String buildTaskKey(final TaskRecord task) {
    final String taskId = task.getTaskId();
    if (taskId != null && !taskId.isBlank()) {
      return taskId;
    }
    final String classPart =
        task.getClassFqn() != null && !task.getClassFqn().isBlank()
            ? task.getClassFqn()
            : task.getFilePath();
    final String methodPart = task.getMethodName();
    if (classPart == null || classPart.isBlank()) {
      return null;
    }
    final String method = methodPart != null ? methodPart : "unknown";
    return (classPart + "#" + method).toLowerCase(Locale.ROOT);
  }

  private TaskEntriesSource resolveSource() {
    if (entriesSource != null) {
      return entriesSource;
    }
    return new DefaultTaskEntriesSource();
  }
}
