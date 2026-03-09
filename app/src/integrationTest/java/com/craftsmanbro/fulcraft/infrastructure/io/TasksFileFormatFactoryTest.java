package com.craftsmanbro.fulcraft.infrastructure.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileFormat;
import com.craftsmanbro.fulcraft.infrastructure.io.impl.JsonTasksFileFormat;
import com.craftsmanbro.fulcraft.infrastructure.io.impl.JsonlTasksFileFormat;
import com.craftsmanbro.fulcraft.infrastructure.io.impl.TasksFileFormatFactory;
import com.craftsmanbro.fulcraft.infrastructure.io.impl.YamlTasksFileFormat;
import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileFormatType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests for {@link TasksFileFormatFactory}.
 *
 * <p>Verifies format selection by path and type, and existing file resolution.
 */
class TasksFileFormatFactoryTest {

  @TempDir Path tempDir;

  private TasksFileFormatFactory factory;

  @BeforeEach
  void setUp() {
    factory = new TasksFileFormatFactory();
  }

  // --- Constructor tests ---

  @Test
  void constructor_withNullMapper_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new TasksFileFormatFactory(null));
  }

  @Test
  void constructor_withMapper_createsInstance() {
    TasksFileFormatFactory f = new TasksFileFormatFactory(new ObjectMapper());
    assertNotNull(f);
  }

  // --- formatForPath tests ---

  @Test
  void formatForPath_withNullPath_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> factory.formatForPath(null));
  }

  @Test
  void formatForPath_withJsonExtension_returnsJsonFormat() {
    Path path = tempDir.resolve("tasks.json");

    TasksFileFormat format = factory.formatForPath(path);

    assertNotNull(format);
    assertTrue(format instanceof JsonTasksFileFormat);
  }

  @Test
  void formatForPath_withYamlExtension_returnsYamlFormat() {
    Path path = tempDir.resolve("tasks.yaml");

    TasksFileFormat format = factory.formatForPath(path);

    assertNotNull(format);
    assertTrue(format instanceof YamlTasksFileFormat);
  }

  @Test
  void formatForPath_withYmlExtension_returnsYamlFormat() {
    Path path = tempDir.resolve("tasks.yml");

    TasksFileFormat format = factory.formatForPath(path);

    assertNotNull(format);
    assertTrue(format instanceof YamlTasksFileFormat);
  }

  @Test
  void formatForPath_withJsonlExtension_returnsJsonlFormat() {
    Path path = tempDir.resolve("tasks.jsonl");

    TasksFileFormat format = factory.formatForPath(path);

    assertNotNull(format);
    assertTrue(format instanceof JsonlTasksFileFormat);
  }

  @Test
  void formatForPath_withUnknownExtension_returnsJsonlFormat() {
    Path path = tempDir.resolve("tasks.txt");

    TasksFileFormat format = factory.formatForPath(path);

    assertNotNull(format);
    assertTrue(format instanceof JsonlTasksFileFormat);
  }

  @Test
  void formatForPath_withNoExtension_returnsJsonlFormat() {
    Path path = tempDir.resolve("tasks");

    TasksFileFormat format = factory.formatForPath(path);

    assertNotNull(format);
    assertTrue(format instanceof JsonlTasksFileFormat);
  }

  // --- formatForType tests ---

  @Test
  void formatForType_withNullType_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> factory.formatForType(null));
  }

  @Test
  void formatForType_withJson_returnsJsonFormat() {
    TasksFileFormat format = factory.formatForType(TasksFileFormatType.JSON);

    assertNotNull(format);
    assertTrue(format instanceof JsonTasksFileFormat);
  }

  @Test
  void formatForType_withYaml_returnsYamlFormat() {
    TasksFileFormat format = factory.formatForType(TasksFileFormatType.YAML);

    assertNotNull(format);
    assertTrue(format instanceof YamlTasksFileFormat);
  }

  @Test
  void formatForType_withJsonl_returnsJsonlFormat() {
    TasksFileFormat format = factory.formatForType(TasksFileFormatType.JSONL);

    assertNotNull(format);
    assertTrue(format instanceof JsonlTasksFileFormat);
  }

  // --- resolveTasksPath tests ---

  @Test
  void resolveTasksPath_withNullDirectory_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class, () -> factory.resolveTasksPath(null, TasksFileFormatType.JSON));
  }

  @Test
  void resolveTasksPath_withNullType_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> factory.resolveTasksPath(tempDir, null));
  }

  @Test
  void resolveTasksPath_withJsonType_returnsCorrectPath() {
    Path path = factory.resolveTasksPath(tempDir, TasksFileFormatType.JSON);

    assertEquals(tempDir.resolve("tasks.json"), path);
  }

  @Test
  void resolveTasksPath_withYamlType_returnsCorrectPath() {
    Path path = factory.resolveTasksPath(tempDir, TasksFileFormatType.YAML);

    assertEquals(tempDir.resolve("tasks.yaml"), path);
  }

  @Test
  void resolveTasksPath_withJsonlType_returnsCorrectPath() {
    Path path = factory.resolveTasksPath(tempDir, TasksFileFormatType.JSONL);

    assertEquals(tempDir.resolve("tasks.jsonl"), path);
  }

  // --- resolveExistingTasksFile tests ---

  @Test
  void resolveExistingTasksFile_withNullDirectory_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> factory.resolveExistingTasksFile(null));
  }

  @Test
  void resolveExistingTasksFile_withNoFiles_returnsNull() {
    Path result = factory.resolveExistingTasksFile(tempDir);

    assertNull(result);
  }

  @Test
  void resolveExistingTasksFile_withJsonFile_returnsJsonPath() throws IOException {
    Path jsonPath = tempDir.resolve("tasks.json");
    Files.writeString(jsonPath, "{}");

    Path result = factory.resolveExistingTasksFile(tempDir);

    assertEquals(jsonPath, result);
  }

  @Test
  void resolveExistingTasksFile_withYamlFile_returnsYamlPath() throws IOException {
    Path yamlPath = tempDir.resolve("tasks.yaml");
    Files.writeString(yamlPath, "tasks: []");

    Path result = factory.resolveExistingTasksFile(tempDir);

    assertEquals(yamlPath, result);
  }

  @Test
  void resolveExistingTasksFile_withYmlFile_returnsYmlPath() throws IOException {
    Path ymlPath = tempDir.resolve("tasks.yml");
    Files.writeString(ymlPath, "tasks: []");

    Path result = factory.resolveExistingTasksFile(tempDir);

    assertEquals(ymlPath, result);
  }

  @Test
  void resolveExistingTasksFile_withJsonlFile_returnsJsonlPath() throws IOException {
    Path jsonlPath = tempDir.resolve("tasks.jsonl");
    Files.writeString(jsonlPath, "{}");

    Path result = factory.resolveExistingTasksFile(tempDir);

    assertEquals(jsonlPath, result);
  }

  @Test
  void resolveExistingTasksFile_prefersJsonOverYaml() throws IOException {
    Files.writeString(tempDir.resolve("tasks.json"), "{}");
    Files.writeString(tempDir.resolve("tasks.yaml"), "tasks: []");

    Path result = factory.resolveExistingTasksFile(tempDir);

    assertEquals(tempDir.resolve("tasks.json"), result);
  }

  @Test
  void resolveExistingTasksFile_prefersYamlOverJsonl() throws IOException {
    Files.writeString(tempDir.resolve("tasks.yaml"), "tasks: []");
    Files.writeString(tempDir.resolve("tasks.jsonl"), "{}");

    Path result = factory.resolveExistingTasksFile(tempDir);

    assertEquals(tempDir.resolve("tasks.yaml"), result);
  }
}
