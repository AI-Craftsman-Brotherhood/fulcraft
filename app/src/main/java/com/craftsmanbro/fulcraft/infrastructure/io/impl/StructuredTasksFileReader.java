package com.craftsmanbro.fulcraft.infrastructure.io.impl;

import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileReader;
import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileEntry;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class StructuredTasksFileReader implements TasksFileReader {

  private final InputStream inputStream;

  private final JsonParser parser;

  private final ObjectMapper objectMapper;

  private final Iterator<TasksFileEntry> iterator;

  StructuredTasksFileReader(final InputStream inputStream, final ObjectMapper objectMapper)
      throws IOException {
    this.inputStream =
        Objects.requireNonNull(
            inputStream,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "inputStream"));
    this.objectMapper =
        Objects.requireNonNull(
            objectMapper,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "objectMapper"));
    this.parser = this.objectMapper.createParser(this.inputStream);
    this.iterator = new EntryIterator(parser, objectMapper);
  }

  @Override
  public Iterator<TasksFileEntry> iterator() {
    return iterator;
  }

  @Override
  public void close() {
    try {
      parser.close();
    } catch (RuntimeException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Failed to close tasks parser: " + e.getMessage()));
    }
    try {
      inputStream.close();
    } catch (IOException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Failed to close tasks input: " + e.getMessage()));
    }
  }

  private static final class EntryIterator implements Iterator<TasksFileEntry> {

    private final JsonParser parser;

    private final ObjectMapper objectMapper;

    private Section section = Section.NONE;

    private TasksFileEntry nextEntry;

    private boolean initialized;

    private boolean done;

    private int taskIndex;

    private int resultIndex;

    private enum Section {
      NONE,
      TASKS,
      RESULTS,
      DONE
    }

    private EntryIterator(final JsonParser parser, final ObjectMapper objectMapper) {
      this.parser = parser;
      this.objectMapper = objectMapper;
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
      final TasksFileEntry entry = nextEntry;
      nextEntry = null;
      return entry;
    }

    private void fetchNext() {
      while (!done) {
        try {
          if (!initialized) {
            initializeParser();
          }
          if (section == Section.NONE && !advanceToNextSection()) {
            done = true;
            return;
          }
          final TasksFileEntry entry = readEntryInSection();
          if (entry != null) {
            nextEntry = entry;
            return;
          }
        } catch (IOException e) {
          done = true;
          throw new IllegalStateException(
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "infra.common.error.message", "Failed to read tasks file entry"),
              e);
        }
      }
    }

    private void initializeParser() throws IOException {
      final JsonToken token = parser.nextToken();
      if (token != JsonToken.START_OBJECT) {
        throw new IOException(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.message", "Tasks file must start with an object"));
      }
      initialized = true;
    }

    private boolean advanceToNextSection() throws IOException {
      while (true) {
        final JsonToken token = parser.nextToken();
        if (token == null || token == JsonToken.END_OBJECT) {
          section = Section.DONE;
          return false;
        }
        if (token != JsonToken.PROPERTY_NAME) {
          continue;
        }
        final String fieldName = parser.currentName();
        final JsonToken valueToken = parser.nextToken();
        if (valueToken == JsonToken.START_ARRAY) {
          if ("tasks".equals(fieldName)) {
            section = Section.TASKS;
            return true;
          }
          if ("results".equals(fieldName)) {
            section = Section.RESULTS;
            return true;
          }
        }
        parser.skipChildren();
      }
    }

    private TasksFileEntry readEntryInSection() throws IOException {
      final JsonToken token = parser.nextToken();
      if (token == JsonToken.END_ARRAY) {
        section = Section.NONE;
        return null;
      }
      if (token != JsonToken.START_OBJECT) {
        parser.skipChildren();
        return null;
      }
      final JsonNode node = parser.readValueAsTree();
      if (section == Section.TASKS) {
        taskIndex++;
        final TaskRecord task = convert(node, TaskRecord.class, "task", taskIndex);
        return task != null ? TasksFileEntry.forTask(task) : null;
      }
      if (section == Section.RESULTS) {
        resultIndex++;
        final GenerationTaskResult result =
            convert(node, GenerationTaskResult.class, "result", resultIndex);
        return result != null ? TasksFileEntry.forResult(result) : null;
      }
      return null;
    }

    private <T> T convert(
        final JsonNode node, final Class<T> type, final String label, final int index) {
      try {
        return objectMapper.treeToValue(node, type);
      } catch (Exception e) {
        Logger.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message",
                "Failed to parse " + label + " entry #" + index + ": " + e.getMessage()));
        return null;
      }
    }
  }
}
