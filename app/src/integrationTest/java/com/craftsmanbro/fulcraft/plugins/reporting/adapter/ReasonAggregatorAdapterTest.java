package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.plugins.reporting.core.service.reason.ReasonAggregator;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonStats;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesReader;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReasonAggregatorAdapterTest {

  @TempDir Path tempDir;

  @Test
  void aggregate_readsReasonsFromFile() throws Exception {
    var mapper = JsonMapperFactory.create();
    Map<String, Object> task = buildTaskWithReasons();

    Path tasksFile = tempDir.resolve("tasks.jsonl");
    Files.writeString(tasksFile, mapper.writeValueAsString(task) + "\n");

    ReasonAggregatorAdapter adapter = new ReasonAggregatorAdapter(new ReasonAggregator());
    ReasonSummary summary = adapter.aggregate(tasksFile, "run-1", true);

    assertThat(summary.getRunId()).isEqualTo("run-1");
    assertThat(summary.getReasonStats()).containsKey("spi_low_conf");

    ReasonStats stats = summary.getReasonStats().get("spi_low_conf");
    assertThat(stats.getTaskCount()).isEqualTo(1);
    assertThat(stats.getWouldBeExcludedCount()).isEqualTo(1);
    assertThat(stats.getExcludedCount()).isEqualTo(0);
  }

  @Test
  void aggregate_readsReasonsFromBufferedReader() throws Exception {
    var mapper = JsonMapperFactory.create();
    Map<String, Object> task = buildTaskWithReasons();

    String line = mapper.writeValueAsString(task);
    try (BufferedReader reader = new BufferedReader(new StringReader(line + "\n"))) {
      ReasonAggregatorAdapter adapter = new ReasonAggregatorAdapter(new ReasonAggregator());
      ReasonSummary summary = adapter.aggregate(reader, "run-2", true);

      assertThat(summary.getRunId()).isEqualTo("run-2");
      assertThat(summary.getReasonStats()).containsKey("spi_low_conf");
    }
  }

  @Test
  void aggregate_throwsWhenTasksFileMissing() {
    ReasonAggregatorAdapter adapter = new ReasonAggregatorAdapter();
    Path missing = tempDir.resolve("missing.jsonl");

    assertThrows(IOException.class, () -> adapter.aggregate(missing, "run-3", false));
  }

  @Test
  void aggregate_usesProvidedEntriesSource() throws Exception {
    ReasonAggregator aggregator = mock(ReasonAggregator.class);
    TaskEntriesSource entriesSource = mock(TaskEntriesSource.class);
    TaskEntriesReader entriesReader = mock(TaskEntriesReader.class);

    ReasonSummary expected = new ReasonSummary();
    expected.setRunId("run-custom");

    when(entriesSource.readJsonl(any(BufferedReader.class))).thenReturn(entriesReader);
    when(aggregator.aggregate(entriesReader, "run-custom", false)).thenReturn(expected);

    ReasonAggregatorAdapter adapter = new ReasonAggregatorAdapter(aggregator, entriesSource);
    try (BufferedReader reader = new BufferedReader(new StringReader("{}\n"))) {
      ReasonSummary actual = adapter.aggregate(reader, "run-custom", false);
      assertThat(actual).isSameAs(expected);
    }

    verify(entriesSource).readJsonl(any(BufferedReader.class));
    verify(aggregator).aggregate(entriesReader, "run-custom", false);
    verify(entriesReader).close();
  }

  private Map<String, Object> buildTaskWithReasons() {
    return Map.of(
        "task_id",
        "task-1",
        "class_fqn",
        "com.example.Foo",
        "method_name",
        "doWork",
        "selected",
        false,
        "feasibility_breakdown",
        Map.of("dynamic_reasons", List.of("spi_low_conf"), "shadow_score", 0.0));
  }
}
