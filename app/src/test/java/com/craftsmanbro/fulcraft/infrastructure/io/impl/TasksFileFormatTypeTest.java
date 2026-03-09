package com.craftsmanbro.fulcraft.infrastructure.io.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileFormatType;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TasksFileFormatType} enum.
 *
 * <p>Verifies enum values, keys, default filenames, and fromString parsing.
 */
class TasksFileFormatTypeTest {

  @Test
  void values_hasThreeTypes() {
    assertEquals(3, TasksFileFormatType.values().length);
  }

  @Test
  void json_hasCorrectKeyAndFilename() {
    assertEquals("json", TasksFileFormatType.JSON.getKey());
    assertEquals("tasks.json", TasksFileFormatType.JSON.getDefaultFilename());
  }

  @Test
  void yaml_hasCorrectKeyAndFilename() {
    assertEquals("yaml", TasksFileFormatType.YAML.getKey());
    assertEquals("tasks.yaml", TasksFileFormatType.YAML.getDefaultFilename());
  }

  @Test
  void jsonl_hasCorrectKeyAndFilename() {
    assertEquals("jsonl", TasksFileFormatType.JSONL.getKey());
    assertEquals("tasks.jsonl", TasksFileFormatType.JSONL.getDefaultFilename());
  }

  @Test
  void fromString_withNull_returnsNull() {
    assertNull(TasksFileFormatType.fromString(null));
  }

  @Test
  void fromString_withBlank_returnsNull() {
    assertNull(TasksFileFormatType.fromString("  "));
  }

  @Test
  void fromString_withJson_returnsJson() {
    assertEquals(TasksFileFormatType.JSON, TasksFileFormatType.fromString("json"));
  }

  @Test
  void fromString_withJsonUpperCase_returnsJson() {
    assertEquals(TasksFileFormatType.JSON, TasksFileFormatType.fromString("JSON"));
  }

  @Test
  void fromString_withYaml_returnsYaml() {
    assertEquals(TasksFileFormatType.YAML, TasksFileFormatType.fromString("yaml"));
  }

  @Test
  void fromString_withYml_returnsYaml() {
    assertEquals(TasksFileFormatType.YAML, TasksFileFormatType.fromString("yml"));
  }

  @Test
  void fromString_withJsonl_returnsJsonl() {
    assertEquals(TasksFileFormatType.JSONL, TasksFileFormatType.fromString("jsonl"));
  }

  @Test
  void fromString_withUnknown_returnsNull() {
    assertNull(TasksFileFormatType.fromString("xml"));
  }

  @Test
  void fromString_withWhitespace_trimsAndParses() {
    assertEquals(TasksFileFormatType.JSON, TasksFileFormatType.fromString("  json  "));
  }
}
