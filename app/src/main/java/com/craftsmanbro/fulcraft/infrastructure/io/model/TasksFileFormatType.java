package com.craftsmanbro.fulcraft.infrastructure.io.model;

import java.util.Locale;

public enum TasksFileFormatType {
  JSON("json", "tasks.json"),
  YAML("yaml", "tasks.yaml"),
  JSONL("jsonl", "tasks.jsonl");

  private final String key;

  private final String defaultFileName;

  TasksFileFormatType(final String key, final String defaultFileName) {
    this.key = key;
    this.defaultFileName = defaultFileName;
  }

  public String getKey() {
    return key;
  }

  public String getDefaultFilename() {
    return defaultFileName;
  }

  public static TasksFileFormatType fromString(final String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    final String normalized = value.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "json" -> JSON;
      case "yaml", "yml" -> YAML;
      case "jsonl" -> JSONL;
      default -> null;
    };
  }
}
