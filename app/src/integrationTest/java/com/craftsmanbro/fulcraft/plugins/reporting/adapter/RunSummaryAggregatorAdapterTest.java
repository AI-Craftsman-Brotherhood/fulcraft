package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.plugins.reporting.core.service.aggregator.RunSummaryAggregator;
import com.craftsmanbro.fulcraft.plugins.reporting.model.DynamicSelectionReport;
import com.craftsmanbro.fulcraft.plugins.reporting.model.RunSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesReader;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for RunSummaryAggregatorAdapter. Note: This adapter processes task definitions (plan) only.
 * Execution results are aggregated separately from JUnit XML reports.
 */
class RunSummaryAggregatorAdapterTest {

  @TempDir Path tempDir;

  @Test
  void aggregate_readsTasksFromFile() throws Exception {
    var mapper = JsonMapperFactory.create();
    Map<String, Object> task1 = buildTask("task-1", "com.example.Foo", "doWork", true);
    Map<String, Object> task2 = buildTask("task-2", "com.example.Bar", "run", false);

    Path tasksFile = tempDir.resolve("tasks.jsonl");
    Files.writeString(
        tasksFile,
        mapper.writeValueAsString(task1) + "\n" + mapper.writeValueAsString(task2) + "\n");

    RunSummaryAggregatorAdapter adapter =
        new RunSummaryAggregatorAdapter(new RunSummaryAggregator());
    RunSummary summary = adapter.aggregate(tasksFile, "run-1", false, null);

    assertThat(summary.getRunId()).isEqualTo("run-1");
    assertThat(summary.getTotalTasks()).isEqualTo(2);
    assertThat(summary.getSelectedTasks()).isEqualTo(1);
    // No execution results from plan-only aggregation
    assertThat(summary.getExecutedTasks()).isEqualTo(0);
  }

  @Test
  void aggregate_readsFromBufferedReader() throws Exception {
    var mapper = JsonMapperFactory.create();
    Map<String, Object> task1 = buildTask("task-2", "com.example.Bar", "run", true);
    Map<String, Object> task2 = buildTask("task-3", "com.example.Baz", "execute", true);

    String line1 = mapper.writeValueAsString(task1);
    String line2 = mapper.writeValueAsString(task2);

    try (BufferedReader reader =
        new BufferedReader(new StringReader(line1 + "\n" + line2 + "\n"))) {
      RunSummaryAggregatorAdapter adapter =
          new RunSummaryAggregatorAdapter(new RunSummaryAggregator());
      RunSummary summary = adapter.aggregate(reader, "run-2", true, null);

      assertThat(summary.getRunId()).isEqualTo("run-2");
      assertThat(summary.getTotalTasks()).isEqualTo(2);
      assertThat(summary.getSelectedTasks()).isEqualTo(2);
      // No execution results from plan-only aggregation
      assertThat(summary.getExecutedTasks()).isEqualTo(0);
    }
  }

  @Test
  void aggregate_throwsWhenTasksFileMissing() {
    RunSummaryAggregatorAdapter adapter = new RunSummaryAggregatorAdapter();
    Path missing = tempDir.resolve("missing.jsonl");

    assertThrows(IOException.class, () -> adapter.aggregate(missing, "run-3", false, null));
  }

  @Test
  void aggregate_usesProvidedEntriesSource() throws Exception {
    RunSummaryAggregator aggregator = mock(RunSummaryAggregator.class);
    TaskEntriesSource entriesSource = mock(TaskEntriesSource.class);
    TaskEntriesReader entriesReader = mock(TaskEntriesReader.class);
    DynamicSelectionReport dynamicReport = new DynamicSelectionReport();

    RunSummary expected = new RunSummary();
    expected.setRunId("run-custom");

    when(entriesSource.readJsonl(any(BufferedReader.class))).thenReturn(entriesReader);
    when(aggregator.aggregate(entriesReader, "run-custom", true, dynamicReport))
        .thenReturn(expected);

    RunSummaryAggregatorAdapter adapter =
        new RunSummaryAggregatorAdapter(aggregator, entriesSource);
    try (BufferedReader reader = new BufferedReader(new StringReader("{}\n"))) {
      RunSummary actual = adapter.aggregate(reader, "run-custom", true, dynamicReport);
      assertThat(actual).isSameAs(expected);
    }

    verify(entriesSource).readJsonl(any(BufferedReader.class));
    verify(aggregator).aggregate(entriesReader, "run-custom", true, dynamicReport);
    verify(entriesReader).close();
  }

  private Map<String, Object> buildTask(
      String taskId, String classFqn, String methodName, boolean selected) {
    return Map.of(
        "task_id", taskId, "class_fqn", classFqn, "method_name", methodName, "selected", selected);
  }
}
