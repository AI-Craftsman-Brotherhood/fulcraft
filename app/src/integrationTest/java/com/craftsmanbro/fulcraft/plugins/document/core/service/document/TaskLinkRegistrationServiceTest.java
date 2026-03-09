package com.craftsmanbro.fulcraft.plugins.document.core.service.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesReader;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class TaskLinkRegistrationServiceTest {

  @TempDir Path tempDir;

  @Test
  void registerTaskLinksFromTasksFile_registersOnlySelectedTasks() throws Exception {
    TaskLinkRegistrationService service = new TaskLinkRegistrationService();
    TestLinkResolver resolver = Mockito.mock(TestLinkResolver.class);

    ObjectMapper mapper = new ObjectMapper();
    Path tasksFile = tempDir.resolve("tasks.jsonl");

    Map<String, Object> selectedTask =
        Map.of(
            "task_id", "task-1",
            "class_fqn", "com.example.Service",
            "method_name", "process",
            "test_class_name", "ServiceTest",
            "selected", true);

    Map<String, Object> unselectedTask =
        Map.of(
            "task_id", "task-2",
            "class_fqn", "com.example.Service",
            "method_name", "skip",
            "test_class_name", "ServiceSkipTest",
            "selected", false);

    Map<String, Object> defaultTask =
        Map.of(
            "task_id", "task-3",
            "class_fqn", "com.example.Service",
            "method_name", "doSomething",
            "test_class_name", "ServiceDoTest");
    // selected is absent (default), should be registered

    Files.writeString(
        tasksFile,
        mapper.writeValueAsString(selectedTask)
            + "\n"
            + mapper.writeValueAsString(unselectedTask)
            + "\n"
            + mapper.writeValueAsString(defaultTask)
            + "\n");

    service.registerTaskLinksFromTasksFile(resolver, tasksFile);

    // Selected task and default task should be registered
    verify(resolver)
        .registerTaskLink(
            argThat(task -> task != null && "task-1".equals(task.getTaskId())), isNull());
    verify(resolver)
        .registerTaskLink(
            argThat(task -> task != null && "task-3".equals(task.getTaskId())), isNull());
    // Unselected task should NOT be registered
    verify(resolver, never())
        .registerTaskLink(
            argThat(task -> task != null && "task-2".equals(task.getTaskId())), any());
  }

  @Test
  void registerTaskLinksFromTasksFile_shouldIgnoreEntriesWithoutTask() throws Exception {
    TaskEntriesSource source = Mockito.mock(TaskEntriesSource.class);
    TaskEntriesReader reader = Mockito.mock(TaskEntriesReader.class);
    TaskLinkRegistrationService service = new TaskLinkRegistrationService(source);
    TestLinkResolver resolver = Mockito.mock(TestLinkResolver.class);

    Path tasksFile = tempDir.resolve("tasks.jsonl");
    TaskRecord selected = task("task-1", true);
    TaskRecord unselected = task("task-2", false);
    when(source.read(tasksFile)).thenReturn(reader);
    when(reader.iterator())
        .thenReturn(
            List.of(new TaskEntry(null), new TaskEntry(selected), new TaskEntry(unselected))
                .iterator());

    service.registerTaskLinksFromTasksFile(resolver, tasksFile);

    verify(reader).close();
    verify(resolver)
        .registerTaskLink(
            argThat(task -> task != null && "task-1".equals(task.getTaskId())), isNull());
    verify(resolver, never())
        .registerTaskLink(
            argThat(task -> task != null && "task-2".equals(task.getTaskId())), any());
  }

  @Test
  void registerTaskLinksFromTasksFile_shouldDoNothingWhenNoEntriesExist() throws Exception {
    TaskEntriesSource source = Mockito.mock(TaskEntriesSource.class);
    TaskEntriesReader reader = Mockito.mock(TaskEntriesReader.class);
    TaskLinkRegistrationService service = new TaskLinkRegistrationService(source);
    TestLinkResolver resolver = Mockito.mock(TestLinkResolver.class);

    Path tasksFile = tempDir.resolve("tasks.jsonl");
    when(source.read(tasksFile)).thenReturn(reader);
    when(reader.iterator()).thenReturn(List.<TaskEntry>of().iterator());

    service.registerTaskLinksFromTasksFile(resolver, tasksFile);

    verify(reader).close();
    verifyNoInteractions(resolver);
  }

  @Test
  void registerTaskLinksFromTasksFile_shouldDoNothingWhenReadFails() throws Exception {
    TaskEntriesSource source = Mockito.mock(TaskEntriesSource.class);
    TaskLinkRegistrationService service = new TaskLinkRegistrationService(source);
    TestLinkResolver resolver = Mockito.mock(TestLinkResolver.class);

    Path tasksFile = tempDir.resolve("tasks.jsonl");
    when(source.read(tasksFile)).thenThrow(new IOException("simulated failure"));

    service.registerTaskLinksFromTasksFile(resolver, tasksFile);

    verifyNoInteractions(resolver);
  }

  @Test
  void resolveExistingTasksFile_shouldDelegateToTaskEntriesSource() {
    TaskEntriesSource source = Mockito.mock(TaskEntriesSource.class);
    TaskLinkRegistrationService service = new TaskLinkRegistrationService(source);
    Path expected = tempDir.resolve("tasks.jsonl");
    when(source.resolveExistingTasksFile(tempDir)).thenReturn(expected);

    Path actual = service.resolveExistingTasksFile(tempDir);

    assertThat(actual).isEqualTo(expected);
    verify(source).resolveExistingTasksFile(tempDir);
  }

  @Test
  void registerTaskLinksFromTasksFile_shouldThrowWhenResolverIsNull() {
    TaskLinkRegistrationService service = new TaskLinkRegistrationService();
    Path tasksFile = tempDir.resolve("tasks.jsonl");

    assertThatThrownBy(() -> service.registerTaskLinksFromTasksFile(null, tasksFile))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("resolver");
  }

  @Test
  void registerTaskLinksFromTasksFile_shouldThrowWhenTasksFileIsNull() {
    TaskLinkRegistrationService service = new TaskLinkRegistrationService();
    TestLinkResolver resolver = Mockito.mock(TestLinkResolver.class);

    assertThatThrownBy(() -> service.registerTaskLinksFromTasksFile(resolver, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("tasksFile");
  }

  @Test
  void resolveExistingTasksFile_shouldThrowWhenDirectoryIsNull() {
    TaskLinkRegistrationService service = new TaskLinkRegistrationService();

    assertThatThrownBy(() -> service.resolveExistingTasksFile(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("directory");
  }

  @Test
  void constructor_shouldThrowWhenTaskEntriesSourceIsNull() {
    assertThatThrownBy(() -> new TaskLinkRegistrationService((TaskEntriesSource) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("taskEntriesSource");
  }

  private static TaskRecord task(String taskId, Boolean selected) {
    TaskRecord task = new TaskRecord();
    task.setTaskId(taskId);
    task.setClassFqn("com.example.Service");
    task.setMethodName("process");
    task.setTestClassName("ServiceTest");
    task.setSelected(selected);
    return task;
  }
}
