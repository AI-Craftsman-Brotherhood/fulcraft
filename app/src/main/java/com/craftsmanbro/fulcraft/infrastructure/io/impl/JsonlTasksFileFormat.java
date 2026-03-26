package com.craftsmanbro.fulcraft.infrastructure.io.impl;

import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileFormat;
import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileReader;
import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileEntry;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.node.ObjectNode;

public class JsonlTasksFileFormat implements TasksFileFormat {

  private final ObjectMapper objectMapper;

  public JsonlTasksFileFormat(final ObjectMapper objectMapper) {
    Objects.requireNonNull(
        objectMapper,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "objectMapper"));
    this.objectMapper =
        objectMapper
            .rebuild()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.INDENT_OUTPUT, false)
            .build();
  }

  @Override
  public TasksFileReader read(final Path path) throws IOException {
    Objects.requireNonNull(
        path,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "path"));
    final BufferedReader reader = Files.newBufferedReader(path);
    return new JsonlTasksFileReader(reader, objectMapper, path.toString());
  }

  public TasksFileReader read(final BufferedReader reader) {
    return new JsonlTasksFileReader(reader, objectMapper, "tasks.jsonl");
  }

  @Override
  public void write(
      final Iterable<TaskRecord> tasks,
      final Iterable<GenerationTaskResult> results,
      final Path path)
      throws IOException {
    Objects.requireNonNull(
        path,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "path"));
    final List<TaskRecord> nonNullTasks = new ArrayList<>();
    if (tasks != null) {
      for (final TaskRecord taskRecord : tasks) {
        if (taskRecord != null) {
          nonNullTasks.add(taskRecord);
        }
      }
    }
    final Map<String, TaskRecord> tasksByLookupKey = buildTaskIndex(nonNullTasks);
    try (BufferedWriter writer = Files.newBufferedWriter(path)) {
      for (final TaskRecord taskRecord : nonNullTasks) {
        writer.write(objectMapper.writeValueAsString(taskRecord));
        writer.newLine();
      }
      if (results == null) {
        return;
      }
      for (final GenerationTaskResult result : results) {
        if (result == null) {
          continue;
        }
        final TaskRecord matchingTask =
            tasksByLookupKey.get(
                buildKey(result.getTaskId(), result.getClassFqn(), result.getMethodName()));
        writer.write(serializeResult(result, matchingTask));
        writer.newLine();
      }
    }
  }

  private Map<String, TaskRecord> buildTaskIndex(final Iterable<TaskRecord> tasks) {
    final Map<String, TaskRecord> tasksByLookupKey = new HashMap<>();
    if (tasks == null) {
      return tasksByLookupKey;
    }
    for (final TaskRecord taskRecord : tasks) {
      if (taskRecord == null) {
        continue;
      }
      tasksByLookupKey.put(
          buildKey(taskRecord.getTaskId(), taskRecord.getClassFqn(), taskRecord.getMethodName()),
          taskRecord);
    }
    return tasksByLookupKey;
  }

  private String serializeResult(final GenerationTaskResult result, final TaskRecord task)
      throws JacksonException {
    if (task == null) {
      return objectMapper.writeValueAsString(result);
    }
    final ObjectNode mergedNode = objectMapper.valueToTree(task);
    final ObjectNode resultNode = objectMapper.valueToTree(result);
    mergedNode.setAll(resultNode);
    return objectMapper.writeValueAsString(mergedNode);
  }

  private String buildKey(final String taskId, final String classFqn, final String methodName) {
    if (taskId != null && !taskId.isBlank()) {
      return taskId;
    }
    final String classKeyPart = classFqn != null ? classFqn : "unknown";
    final String methodKeyPart = methodName != null ? methodName : "unknown";
    return (classKeyPart + "#" + methodKeyPart).toLowerCase(Locale.ROOT);
  }

  private static final class JsonlTasksFileReader implements TasksFileReader {

    private final BufferedReader reader;

    private final String sourceName;

    private final Iterator<TasksFileEntry> iterator;

    private JsonlTasksFileReader(
        final BufferedReader reader, final ObjectMapper objectMapper, final String sourceName) {
      this.reader =
          Objects.requireNonNull(
              reader,
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "infra.common.error.argument_null", "reader"));
      Objects.requireNonNull(
          objectMapper,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.argument_null", "objectMapper"));
      this.sourceName = sourceName;
      this.iterator = new EntryIterator(reader, objectMapper, sourceName);
    }

    @Override
    public Iterator<TasksFileEntry> iterator() {
      return iterator;
    }

    @Override
    public void close() {
      try {
        reader.close();
      } catch (IOException ioException) {
        Logger.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message",
                "Failed to close " + sourceName + ": " + ioException.getMessage()));
      }
    }
  }

  private static final class EntryIterator implements Iterator<TasksFileEntry> {

    private final BufferedReader reader;

    private final ObjectMapper objectMapper;

    private final String sourceName;

    private TasksFileEntry nextEntry;

    private boolean done;

    private int lineNumber;

    private EntryIterator(
        final BufferedReader reader, final ObjectMapper objectMapper, final String sourceName) {
      this.reader = reader;
      this.objectMapper = objectMapper;
      this.sourceName = sourceName;
    }

    @Override
    public boolean hasNext() {
      if (nextEntry == null && !done) {
        fetchNext();
      }
      return nextEntry != null;
    }

    @Override
    public TasksFileEntry next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      final TasksFileEntry currentEntry = nextEntry;
      nextEntry = null;
      return currentEntry;
    }

    private void fetchNext() {
      try {
        String lineText;
        while ((lineText = reader.readLine()) != null) {
          lineNumber++;
          if (lineText.isBlank()) {
            continue;
          }
          final TasksFileEntry parsedEntry = parseLine(lineText);
          if (parsedEntry != null) {
            nextEntry = parsedEntry;
            return;
          }
        }
      } catch (IOException ioException) {
        Logger.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message",
                "Failed to read " + sourceName + ": " + ioException.getMessage()));
      }
      done = true;
    }

    private TasksFileEntry parseLine(final String line) {
      try {
        final JsonNode lineNode = objectMapper.readTree(line);
        if (lineNode == null || lineNode.isNull()) {
          return null;
        }
        final GenerationTaskResult parsedResult = tryParseResult(lineNode);
        final TaskRecord parsedTask = tryParseTask(lineNode);
        // `status` is the discriminator because result deserialization is permissive.
        if (parsedResult != null && parsedResult.getStatus() != null) {
          return parsedTask != null
              ? TasksFileEntry.forResultWithTask(parsedResult, parsedTask)
              : TasksFileEntry.forResult(parsedResult);
        }
        if (parsedTask != null) {
          return TasksFileEntry.forTask(parsedTask);
        }
        return null;
      } catch (JacksonException parseException) {
        Logger.warn(
            "Failed to parse "
                + sourceName
                + " line "
                + lineNumber
                + ": "
                + parseException.getMessage());
        return null;
      }
    }

    private GenerationTaskResult tryParseResult(final JsonNode node) {
      try {
        return objectMapper.treeToValue(node, GenerationTaskResult.class);
      } catch (JacksonException | IllegalArgumentException ignored) {
        return null;
      }
    }

    private TaskRecord tryParseTask(final JsonNode node) {
      // Avoid treating result-only rows as tasks when no task identity fields are present.
      if (!hasTaskFields(node)) {
        return null;
      }
      try {
        final TaskRecord parsedTask = objectMapper.treeToValue(node, TaskRecord.class);
        return hasTaskIdentity(parsedTask) ? parsedTask : null;
      } catch (JacksonException | IllegalArgumentException ignored) {
        return null;
      }
    }

    private boolean hasTaskFields(final JsonNode node) {
      return node.has("task_id")
          || node.has("taskId")
          || node.has("class_fqn")
          || node.has("classFqn")
          || node.has("method_name")
          || node.has("methodName");
    }

    private boolean hasTaskIdentity(final TaskRecord task) {
      if (task == null) {
        return false;
      }
      if (task.getTaskId() != null && !task.getTaskId().isBlank()) {
        return true;
      }
      if (task.getClassFqn() != null && !task.getClassFqn().isBlank()) {
        return true;
      }
      return task.getMethodName() != null && !task.getMethodName().isBlank();
    }
  }
}
