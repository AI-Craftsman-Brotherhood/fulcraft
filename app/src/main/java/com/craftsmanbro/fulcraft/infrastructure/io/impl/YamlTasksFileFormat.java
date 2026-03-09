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
import tools.jackson.dataformat.yaml.YAMLMapper;

public class YamlTasksFileFormat implements TasksFileFormat {

  private final ObjectMapper objectMapper;

  public YamlTasksFileFormat(final ObjectMapper baseMapper) {
    Objects.requireNonNull(
        baseMapper,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "baseMapper"));
    this.objectMapper =
        YAMLMapper.builder()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
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
    } catch (IOException | RuntimeException constructionFailure) {
      try {
        fileInputStream.close();
      } catch (IOException ignored) {
        // Best effort cleanup on constructor failure.
      }
      throw constructionFailure;
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
    final var documentNode = objectMapper.createObjectNode();
    final var tasksArrayNode = documentNode.putArray("tasks");
    if (tasks != null) {
      for (final TaskRecord task : tasks) {
        if (task != null) {
          tasksArrayNode.add((JsonNode) objectMapper.valueToTree(task));
        }
      }
    }
    final var resultsArrayNode = documentNode.putArray("results");
    if (results != null) {
      for (final GenerationTaskResult result : results) {
        if (result != null) {
          resultsArrayNode.add((JsonNode) objectMapper.valueToTree(result));
        }
      }
    }
    if (objectMapper.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), documentNode);
      return;
    }
    objectMapper.writeValue(path.toFile(), documentNode);
  }
}
