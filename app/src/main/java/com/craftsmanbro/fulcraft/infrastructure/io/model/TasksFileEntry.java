package com.craftsmanbro.fulcraft.infrastructure.io.model;

import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.util.Objects;

public final class TasksFileEntry {

  private static final String ARGUMENT_NULL_MESSAGE_KEY = "infra.common.error.argument_null";

  private final TaskRecord task;

  private final GenerationTaskResult result;

  private TasksFileEntry(final TaskRecord task, final GenerationTaskResult result) {
    this.task = task;
    this.result = result;
  }

  public static TasksFileEntry forTask(final TaskRecord task) {
    return new TasksFileEntry(requireNonNullArgument(task, "task"), null);
  }

  public static TasksFileEntry forResult(final GenerationTaskResult result) {
    return new TasksFileEntry(null, requireNonNullArgument(result, "result"));
  }

  public static TasksFileEntry forResultWithTask(
      final GenerationTaskResult result, final TaskRecord task) {
    return new TasksFileEntry(
        requireNonNullArgument(task, "task"), requireNonNullArgument(result, "result"));
  }

  public TaskRecord getTask() {
    return task;
  }

  public GenerationTaskResult getResult() {
    return result;
  }

  public boolean hasTask() {
    return task != null;
  }

  public boolean hasResult() {
    return result != null;
  }

  private static <T> T requireNonNullArgument(final T value, final String argumentName) {
    return Objects.requireNonNull(value, argumentNullMessage(argumentName));
  }

  private static String argumentNullMessage(final String argumentName) {
    return com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
        ARGUMENT_NULL_MESSAGE_KEY, argumentName);
  }
}
