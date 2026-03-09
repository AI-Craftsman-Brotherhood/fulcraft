package com.craftsmanbro.fulcraft.infrastructure.io.impl;

import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileFormat;
import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileFormatType;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

public class TasksFileFormatFactory {

  private final ObjectMapper jsonMapper;

  public TasksFileFormatFactory() {
    this(JsonMapperFactory.createPrettyPrinter());
  }

  public TasksFileFormatFactory(final ObjectMapper jsonMapper) {
    this.jsonMapper =
        Objects.requireNonNull(
            jsonMapper,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "jsonMapper"));
  }

  public TasksFileFormat formatForPath(final Path path) {
    Objects.requireNonNull(
        path,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "path"));
    final Path pathFileName = path.getFileName();
    if (pathFileName == null) {
      return new JsonlTasksFileFormat(jsonMapper);
    }
    final String normalizedFileName = pathFileName.toString().toLowerCase(Locale.ROOT);
    if (normalizedFileName.endsWith(".jsonl")) {
      return new JsonlTasksFileFormat(jsonMapper);
    }
    if (normalizedFileName.endsWith(".json")) {
      return new JsonTasksFileFormat(jsonMapper);
    }
    if (normalizedFileName.endsWith(".yaml") || normalizedFileName.endsWith(".yml")) {
      return new YamlTasksFileFormat(jsonMapper);
    }
    // Default to JSONL when the path has no recognized extension.
    return new JsonlTasksFileFormat(jsonMapper);
  }

  public TasksFileFormat formatForType(final TasksFileFormatType type) {
    Objects.requireNonNull(
        type,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "type"));
    return switch (type) {
      case JSON -> new JsonTasksFileFormat(jsonMapper);
      case YAML -> new YamlTasksFileFormat(jsonMapper);
      case JSONL -> new JsonlTasksFileFormat(jsonMapper);
    };
  }

  public Path resolveTasksPath(final Path directory, final TasksFileFormatType type) {
    Objects.requireNonNull(
        directory,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "directory"));
    Objects.requireNonNull(
        type,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "type"));
    return directory.resolve(type.getDefaultFilename());
  }

  public Path resolveExistingTasksFile(final Path directory) {
    Objects.requireNonNull(
        directory,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "directory"));
    // Preserve lookup precedence when multiple task files exist in the same directory.
    final Path jsonTasksPath = directory.resolve(TasksFileFormatType.JSON.getDefaultFilename());
    if (Files.isRegularFile(jsonTasksPath)) {
      return jsonTasksPath;
    }
    final Path yamlTasksPath = directory.resolve(TasksFileFormatType.YAML.getDefaultFilename());
    if (Files.isRegularFile(yamlTasksPath)) {
      return yamlTasksPath;
    }
    final Path ymlTasksPath = directory.resolve("tasks.yml");
    if (Files.isRegularFile(ymlTasksPath)) {
      return ymlTasksPath;
    }
    final Path jsonlTasksPath =
        directory.resolve(TasksFileFormatType.JSONL.getDefaultFilename());
    if (Files.isRegularFile(jsonlTasksPath)) {
      return jsonlTasksPath;
    }
    return null;
  }
}
