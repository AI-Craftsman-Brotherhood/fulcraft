package com.craftsmanbro.fulcraft.plugins.reporting.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesReader;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntry;
import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TasksFileLoaderTest {

  @TempDir Path tempDir;

  @Test
  void loadTasks_deduplicatesByTaskIdAndSignature() throws Exception {
    Path tasksFile = tempDir.resolve("tasks.jsonl");
    String content =
        """
        {"task_id":"task-1","class_fqn":"com.example.Foo","method_name":"doThing"}
        {"task_id":"task-1","class_fqn":"com.example.Foo","method_name":"doOther"}
        {"class_fqn":"com.example.Bar","method_name":"Run"}
        {"class_fqn":"com.example.Bar","method_name":"run"}
        """;
    Files.writeString(tasksFile, content);

    TasksFileLoader loader = new TasksFileLoader();

    List<TaskRecord> tasks = loader.loadTasks(tasksFile);

    assertEquals(2, tasks.size());
    assertEquals("task-1", tasks.get(0).getTaskId());
    assertEquals("com.example.Bar", tasks.get(1).getClassFqn());
    assertEquals("Run", tasks.get(1).getMethodName());
  }

  @Test
  void loadTasks_readerReturnsImmutableList() throws Exception {
    String content =
        """
        {"task_id":"task-1","class_fqn":"com.example.Foo","method_name":"doThing"}
        {"class_fqn":"com.example.Bar","method_name":"run"}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(content));
    TasksFileLoader loader = new TasksFileLoader();

    List<TaskRecord> tasks = loader.loadTasks(reader);

    assertEquals(2, tasks.size());
    assertThrows(UnsupportedOperationException.class, () -> tasks.add(new TaskRecord()));
  }

  @Test
  void loadTasks_keepsDuplicatesWhenNoIdentityFieldsExist() throws Exception {
    Path tasksFile = tempDir.resolve("tasks.jsonl");
    String content = """
        {"method_name":"run"}
        {"method_name":"run"}
        """;
    Files.writeString(tasksFile, content);

    TasksFileLoader loader = new TasksFileLoader();

    List<TaskRecord> tasks = loader.loadTasks(tasksFile);

    assertEquals(2, tasks.size());
    assertEquals("run", tasks.get(0).getMethodName());
    assertEquals("run", tasks.get(1).getMethodName());
  }

  @Test
  void loadTasks_deduplicatesByFilePathAndUnknownMethod() throws Exception {
    TaskRecord first = new TaskRecord();
    first.setFilePath("src/main/java/com/example/Foo.java");
    TaskRecord second = new TaskRecord();
    second.setFilePath("src/main/java/com/example/Foo.java");

    TaskEntriesSource source =
        new TaskEntriesSource() {
          @Override
          public TaskEntriesReader read(Path path) {
            return new TaskEntriesReader() {
              @Override
              public java.util.Iterator<TaskEntry> iterator() {
                return List.of(new TaskEntry(first), new TaskEntry(second)).iterator();
              }

              @Override
              public void close() {}
            };
          }

          @Override
          public TaskEntriesReader readJsonl(BufferedReader reader) {
            throw new UnsupportedOperationException();
          }

          @Override
          public Path resolveExistingTasksFile(Path directory) {
            return null;
          }
        };

    TasksFileLoader loader = new TasksFileLoader(source);

    List<TaskRecord> tasks = loader.loadTasks(tempDir.resolve("ignored.jsonl"));

    assertEquals(1, tasks.size());
    assertEquals("src/main/java/com/example/Foo.java", tasks.get(0).getFilePath());
    assertNull(tasks.get(0).getMethodName());
  }

  @Test
  void loadTasks_requiresNonNullInputs() {
    TasksFileLoader loader = new TasksFileLoader();

    assertThrows(NullPointerException.class, () -> loader.loadTasks((Path) null));
    assertThrows(NullPointerException.class, () -> loader.loadTasks((BufferedReader) null));
  }
}
