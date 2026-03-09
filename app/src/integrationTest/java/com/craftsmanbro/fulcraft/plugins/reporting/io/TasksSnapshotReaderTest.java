package com.craftsmanbro.fulcraft.plugins.reporting.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportMetadataKeys;
import com.craftsmanbro.fulcraft.plugins.reporting.core.model.TasksSnapshot;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesReader;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntry;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TasksSnapshotReaderTest {

  @TempDir Path tempDir;

  @Test
  void load_readsTasksFromOverride() throws Exception {
    Path tasksFile = tempDir.resolve("tasks.jsonl");
    String content =
        """
        {"task_id":"task-1","class_fqn":"com.example.Foo","method_name":"doThing"}
        {"task_id":"task-2","class_fqn":"com.example.Bar","method_name":"doOther"}
        """;
    Files.writeString(tasksFile, content);

    RunContext context = new RunContext(tempDir, new Config(), "run-1");
    context.putMetadata(ReportMetadataKeys.TASKS_FILE_OVERRIDE, tasksFile);

    TasksSnapshotReader reader = new TasksSnapshotReader();
    TasksSnapshot snapshot = reader.load(context);

    assertFalse(snapshot.isEmpty());
    assertEquals(2, snapshot.tasks().size());
    TaskRecord task1 = snapshot.tasks().get(0);
    assertEquals("task-1", task1.getTaskId());
    TaskRecord task2 = snapshot.tasks().get(1);
    assertEquals("task-2", task2.getTaskId());
  }

  @Test
  void load_returnsEmptyWhenNoTasksFile() {
    RunContext context = new RunContext(tempDir, new Config(), "run-2");

    TasksSnapshotReader reader = new TasksSnapshotReader();
    TasksSnapshot snapshot = reader.load(context);

    assertTrue(snapshot.isEmpty());
  }

  @Test
  void load_readsTasksFromDefaultPlanDirectory() throws Exception {
    RunContext context = new RunContext(tempDir, new Config(), "run-default");
    Path planDir =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId()).planDir();
    Files.createDirectories(planDir);
    Files.writeString(
        planDir.resolve("tasks.jsonl"),
        """
        {"task_id":"task-default","class_fqn":"com.example.Foo","method_name":"run"}
        """);

    TasksSnapshotReader reader = new TasksSnapshotReader();
    TasksSnapshot snapshot = reader.load(context);

    assertFalse(snapshot.isEmpty());
    assertEquals(1, snapshot.tasks().size());
    assertEquals("task-default", snapshot.tasks().get(0).getTaskId());
  }

  @Test
  void load_returnsEmptyWhenOverrideFileIsMissing() {
    RunContext context = new RunContext(tempDir, new Config(), "run-missing");
    context.putMetadata(ReportMetadataKeys.TASKS_FILE_OVERRIDE, tempDir.resolve("missing.jsonl"));

    TasksSnapshotReader reader = new TasksSnapshotReader();
    TasksSnapshot snapshot = reader.load(context);

    assertTrue(snapshot.isEmpty());
  }

  @Test
  void load_ignoresEntriesWithoutTaskAndUsesPlanDirectoryResolution() {
    AtomicReference<Path> resolvedDirectory = new AtomicReference<>();
    TaskRecord expectedTask = new TaskRecord();
    expectedTask.setTaskId("task-from-source");

    TaskEntriesSource source =
        new TaskEntriesSource() {
          @Override
          public TaskEntriesReader read(Path path) {
            return new TaskEntriesReader() {
              @Override
              public java.util.Iterator<TaskEntry> iterator() {
                return List.of(new TaskEntry(null), new TaskEntry(expectedTask)).iterator();
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
            resolvedDirectory.set(directory);
            return directory.resolve("tasks.jsonl");
          }
        };

    RunContext context = new RunContext(tempDir, new Config(), "run-source");
    TasksSnapshotReader reader = new TasksSnapshotReader(source);
    TasksSnapshot snapshot = reader.load(context);

    assertEquals(
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId()).planDir(),
        resolvedDirectory.get());
    assertFalse(snapshot.isEmpty());
    assertEquals(1, snapshot.tasks().size());
    assertEquals("task-from-source", snapshot.tasks().get(0).getTaskId());
  }

  @Test
  void load_returnsEmptyWhenEntrySourceThrowsIOException() {
    TaskEntriesSource source =
        new TaskEntriesSource() {
          @Override
          public TaskEntriesReader read(Path path) throws IOException {
            throw new IOException("boom");
          }

          @Override
          public TaskEntriesReader readJsonl(BufferedReader reader) {
            throw new UnsupportedOperationException();
          }

          @Override
          public Path resolveExistingTasksFile(Path directory) {
            return directory.resolve("tasks.jsonl");
          }
        };

    RunContext context = new RunContext(tempDir, new Config(), "run-io");
    TasksSnapshotReader reader = new TasksSnapshotReader(source);
    TasksSnapshot snapshot = reader.load(context);

    assertTrue(snapshot.isEmpty());
  }
}
