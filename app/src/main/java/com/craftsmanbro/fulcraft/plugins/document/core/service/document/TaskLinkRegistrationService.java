package com.craftsmanbro.fulcraft.plugins.document.core.service.document;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.DefaultTaskEntriesSource;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesReader;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Registers test links from task artifacts (plan only). */
public class TaskLinkRegistrationService {

  private final TaskEntriesSource taskEntriesSource;

  public TaskLinkRegistrationService() {
    this(new DefaultTaskEntriesSource());
  }

  TaskLinkRegistrationService(final TaskEntriesSource taskEntriesSource) {
    this.taskEntriesSource =
        Objects.requireNonNull(
            taskEntriesSource,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "taskEntriesSource"));
  }

  public void registerTaskLinksFromTasksFile(
      final TestLinkResolver resolver, final Path tasksFile) {
    Objects.requireNonNull(
        resolver,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "resolver"));
    Objects.requireNonNull(
        tasksFile,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "tasksFile"));
    final List<TaskRecord> tasks = readTasks(tasksFile);
    if (tasks == null || tasks.isEmpty()) {
      return;
    }
    registerTaskLinks(resolver, tasks);
  }

  /**
   * Resolves the existing tasks file in the given directory.
   *
   * @param directory the directory to search
   * @return the path to the tasks file, or null if not found
   */
  public Path resolveExistingTasksFile(final Path directory) {
    Objects.requireNonNull(
        directory,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "directory"));
    return taskEntriesSource.resolveExistingTasksFile(directory);
  }

  private List<TaskRecord> readTasks(final Path tasksFile) {
    final List<TaskRecord> tasks = new ArrayList<>();
    try (TaskEntriesReader reader = taskEntriesSource.read(tasksFile)) {
      for (final var entry : reader) {
        if (entry.hasTask()) {
          final TaskRecord task = entry.task();
          if (task != null) {
            tasks.add(task);
          }
        }
      }
    } catch (IOException e) {
      Logger.warn(msg("document.flow.task_links.load_failed", e.getMessage()));
      return List.of();
    }
    return tasks;
  }

  private void registerTaskLinks(final TestLinkResolver resolver, final List<TaskRecord> tasks) {
    for (final TaskRecord task : tasks) {
      if (shouldRegisterTaskLink(task)) {
        resolver.registerTaskLink(task, null);
      }
    }
  }

  private boolean shouldRegisterTaskLink(final TaskRecord task) {
    return task.getSelected() == null || task.getSelected();
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
