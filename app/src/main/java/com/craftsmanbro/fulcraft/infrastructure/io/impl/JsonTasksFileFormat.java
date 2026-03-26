package com.craftsmanbro.fulcraft.infrastructure.io.impl;

import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileFormat;
import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileReader;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

public class JsonTasksFileFormat implements TasksFileFormat {

  private final ObjectMapper objectMapper;

  public JsonTasksFileFormat(final ObjectMapper objectMapper) {
    Objects.requireNonNull(
        objectMapper,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "objectMapper"));
    this.objectMapper =
        objectMapper
            .rebuild()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
  }

  @Override
  public TasksFileReader read(final Path path) throws IOException {
    Objects.requireNonNull(
        path,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "path"));
    final InputStream fileInputStream = Files.newInputStream(path);
    try {
      return new StructuredTasksFileReader(fileInputStream, objectMapper);
    } catch (IOException | RuntimeException failure) {
      try {
        fileInputStream.close();
      } catch (IOException ignored) {
        // Best effort cleanup on constructor failure.
      }
      throw failure;
    }
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
    final var rootNode = objectMapper.createObjectNode();
    final var tasksArrayNode = rootNode.putArray("tasks");
    if (tasks != null) {
      for (final TaskRecord task : tasks) {
        if (task == null) {
          continue;
        }
        tasksArrayNode.add((JsonNode) objectMapper.valueToTree(task));
      }
    }

    final var resultsArrayNode = rootNode.putArray("results");
    if (results != null) {
      for (final GenerationTaskResult result : results) {
        if (result == null) {
          continue;
        }
        resultsArrayNode.add((JsonNode) objectMapper.valueToTree(result));
      }
    }

    if (objectMapper.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), rootNode);
      return;
    }
    objectMapper.writeValue(path.toFile(), rootNode);
  }
}
