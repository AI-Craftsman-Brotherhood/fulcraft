package com.craftsmanbro.fulcraft.plugins.reporting.taskio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

class DefaultTaskEntriesSourceTest {

  @TempDir Path tempDir;

  private ObjectMapper mapper;
  private DefaultTaskEntriesSource source;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    source = new DefaultTaskEntriesSource();
  }

  @Test
  void read_throwsWhenPathIsNull() {
    assertThrows(NullPointerException.class, () -> source.read(null));
  }

  @Test
  void readJsonl_throwsWhenReaderIsNull() {
    assertThrows(NullPointerException.class, () -> source.readJsonl(null));
  }

  @Test
  void resolveExistingTasksFile_throwsWhenDirectoryIsNull() {
    assertThrows(NullPointerException.class, () -> source.resolveExistingTasksFile(null));
  }

  @Test
  void readJsonl_returnsOnlyTaskEntries() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"run","selected":true}
        {"status":"passed"}
        {"task_id":"t2","class_fqn":"com.example.Bar","method_name":"check","selected":false}
        """;

    List<TaskEntry> entries;
    try (TaskEntriesReader reader = source.readJsonl(new BufferedReader(new StringReader(jsonl)))) {
      entries = toList(reader);
    }

    assertEquals(2, entries.size());
    assertEquals("t1", entries.get(0).task().getTaskId());
    assertEquals(Boolean.TRUE, entries.get(0).task().getSelected());
    assertEquals("t2", entries.get(1).task().getTaskId());
    assertEquals(Boolean.FALSE, entries.get(1).task().getSelected());
  }

  @Test
  void read_supportsJsonlJsonAndYamlFiles() throws Exception {
    Map<String, Object> task1 = task("t1", "com.example.Foo", "run", true);
    Map<String, Object> task2 = task("t2", "com.example.Bar", "check", false);

    Path jsonl = tempDir.resolve("tasks.jsonl");
    Path json = tempDir.resolve("tasks.json");
    Path yaml = tempDir.resolve("tasks.yaml");

    Files.writeString(
        jsonl,
        mapper.writeValueAsString(task1)
            + System.lineSeparator()
            + mapper.writeValueAsString(task2)
            + System.lineSeparator());

    Map<String, Object> structured = new LinkedHashMap<>();
    structured.put("tasks", List.of(task1, task2));
    mapper.writeValue(json.toFile(), structured);
    new YAMLMapper().writeValue(yaml.toFile(), structured);

    assertTaskIds(jsonl, List.of("t1", "t2"));
    assertTaskIds(json, List.of("t1", "t2"));
    assertTaskIds(yaml, List.of("t1", "t2"));
  }

  @Test
  void read_ignoresResultEntriesInStructuredFile() throws Exception {
    Map<String, Object> task = task("t1", "com.example.Foo", "run", true);
    Map<String, Object> result = Map.of("task_id", "t1", "status", "PASSED");

    Path json = tempDir.resolve("tasks.json");
    Map<String, Object> structured = new LinkedHashMap<>();
    structured.put("tasks", List.of(task));
    structured.put("results", List.of(result));
    mapper.writeValue(json.toFile(), structured);

    List<TaskEntry> entries;
    try (TaskEntriesReader reader = source.read(json)) {
      entries = toList(reader);
    }

    assertEquals(1, entries.size());
    assertEquals("t1", entries.get(0).task().getTaskId());
  }

  @Test
  void resolveExistingTasksFile_prefersJsonThenYamlThenYmlThenJsonl() throws Exception {
    Path dir = tempDir.resolve("resolve-priority");
    Files.createDirectories(dir);

    Path jsonl = dir.resolve("tasks.jsonl");
    Files.writeString(jsonl, "{}");
    assertEquals(jsonl, source.resolveExistingTasksFile(dir));

    Path yml = dir.resolve("tasks.yml");
    Files.writeString(yml, "tasks: []\n");
    assertEquals(yml, source.resolveExistingTasksFile(dir));

    Path yaml = dir.resolve("tasks.yaml");
    Files.writeString(yaml, "tasks: []\n");
    assertEquals(yaml, source.resolveExistingTasksFile(dir));

    Path json = dir.resolve("tasks.json");
    Files.writeString(json, "{\"tasks\":[]}");
    assertEquals(json, source.resolveExistingTasksFile(dir));
  }

  private void assertTaskIds(Path path, List<String> expectedTaskIds) throws Exception {
    try (TaskEntriesReader reader = source.read(path)) {
      List<String> taskIds =
          toList(reader).stream().map(entry -> entry.task().getTaskId()).toList();
      assertEquals(expectedTaskIds, taskIds);
    }
  }

  private Map<String, Object> task(
      String taskId, String classFqn, String methodName, boolean selected) {
    return Map.of(
        "task_id", taskId, "class_fqn", classFqn, "method_name", methodName, "selected", selected);
  }

  private List<TaskEntry> toList(TaskEntriesReader reader) {
    List<TaskEntry> entries = new ArrayList<>();
    for (TaskEntry entry : reader) {
      entries.add(entry);
    }
    return entries;
  }
}
