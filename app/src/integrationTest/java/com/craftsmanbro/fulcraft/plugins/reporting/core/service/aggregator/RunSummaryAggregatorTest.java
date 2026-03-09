package com.craftsmanbro.fulcraft.plugins.reporting.core.service.aggregator;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.plugins.reporting.model.DynamicSelectionReport;
import com.craftsmanbro.fulcraft.plugins.reporting.model.RunSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.DefaultTaskEntriesSource;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntry;
import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Tests for RunSummaryAggregator. Note: This aggregator processes task definitions (plan) only.
 * Execution results are aggregated separately from JUnit XML reports.
 */
class RunSummaryAggregatorTest {

  private RunSummaryAggregator aggregator;
  private ObjectMapper mapper;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    aggregator = new RunSummaryAggregator();
  }

  @Test
  void testEmptyFile_returnsZeroMetrics() throws Exception {
    BufferedReader reader = new BufferedReader(new StringReader(""));
    RunSummary summary = aggregateWithJsonl(reader, "run-1", false, null);

    assertEquals("run-1", summary.getRunId());
    assertEquals(0, summary.getTotalTasks());
    assertEquals(0, summary.getExecutedTasks());
    assertEquals(0.0, summary.getCompileRate());
    assertEquals(0.0, summary.getPassRate());
  }

  @Test
  void testSingleTask_selected() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":true}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    RunSummary summary = aggregateWithJsonl(reader, "run-2", false, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(1, summary.getSelectedTasks());
    // Task plan only - no execution results
    assertEquals(0, summary.getExecutedTasks());
  }

  @Test
  void testSingleTask_notSelected() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":false}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    RunSummary summary = aggregateWithJsonl(reader, "run-3", false, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(0, summary.getSelectedTasks());
  }

  @Test
  void testMultipleTasks_aggregatesCorrectly() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.A","method_name":"m1","selected":true}
        {"task_id":"t2","class_fqn":"com.example.B","method_name":"m2","selected":true}
        {"task_id":"t3","class_fqn":"com.example.C","method_name":"m3","selected":false}
        {"task_id":"t4","class_fqn":"com.example.D","method_name":"m4","selected":true}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    RunSummary summary = aggregateWithJsonl(reader, "run-8", false, null);

    assertEquals(4, summary.getTotalTasks());
    assertEquals(3, summary.getSelectedTasks()); // t1, t2, t4
    // No execution results
    assertEquals(0, summary.getExecutedTasks());
  }

  @Test
  void testDryRunMode_withDynamicReport() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":true}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));

    DynamicSelectionReport report = new DynamicSelectionReport();
    report.incrementWouldBeExcluded();
    report.incrementWouldBeExcluded();
    report.incrementWouldBeExcluded();

    RunSummary summary = aggregateWithJsonl(reader, "run-9", true, report);

    assertTrue(summary.isDryRun());
    assertEquals(3, summary.getWouldBeExcludedCount());
    assertEquals(0, summary.getExcludedCount());
    // Would-be-excluded rate based on total tasks
    assertEquals(3.0, summary.getWouldBeExcludedRate(), 0.01);
  }

  @Test
  void testDryRunMode_withoutDynamicReport_countsWouldBeExcluded() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":true,"feasibility_breakdown":{"shadow_score":0.0}}
        {"task_id":"t2","class_fqn":"com.example.Foo","method_name":"baz","selected":true,"feasibility_breakdown":{"shadow_score":0.8}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));

    RunSummary summary = aggregateWithJsonl(reader, "run-9b", true, null);

    assertTrue(summary.isDryRun());
    assertEquals(1, summary.getWouldBeExcludedCount());
    assertEquals(0, summary.getExcludedCount());
    assertEquals(0.5, summary.getWouldBeExcludedRate(), 0.01);
  }

  @Test
  void testDryRunMode_countsWouldBeExcludedForDynamicSignals() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":true,"feasibility_breakdown":{"penalties":{"DYNAMIC_REASONS":1}}}
        {"task_id":"t2","class_fqn":"com.example.Foo","method_name":"baz","selected":true,"feasibility_breakdown":{"dynamic_reasons":["SKIP:dynamic"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));

    RunSummary summary = aggregateWithJsonl(reader, "run-9c", true, null);

    assertTrue(summary.isDryRun());
    assertEquals(2, summary.getWouldBeExcludedCount());
    assertEquals(0, summary.getExcludedCount());
    assertEquals(1.0, summary.getWouldBeExcludedRate(), 0.01);
  }

  @Test
  void testDryRunMode_doesNotCountNonSkipDynamicReasons() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":true,"feasibility_breakdown":{"dynamic_reasons":["INFO:dynamic"],"shadow_score":1.0}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));

    RunSummary summary = aggregateWithJsonl(reader, "run-9d", true, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(0, summary.getWouldBeExcludedCount());
    assertEquals(0.0, summary.getWouldBeExcludedRate(), 0.01);
  }

  @Test
  void testEnforceMode_withDynamicReport() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":true}
        {"task_id":"t2","class_fqn":"com.example.Bar","method_name":"baz","selected":false}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));

    DynamicSelectionReport report = new DynamicSelectionReport();
    report.incrementExcluded();
    report.incrementExcluded();

    RunSummary summary = aggregateWithJsonl(reader, "run-10", false, report);

    assertFalse(summary.isDryRun());
    assertEquals(2, summary.getExcludedCount());
    assertEquals(0, summary.getWouldBeExcludedCount());
    assertEquals(1.0, summary.getExcludedRate(), 0.01); // 2/2
  }

  @Test
  void testEnforceMode_countsExcludedForDynamicPenalty() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":true,"feasibility_breakdown":{"penalties":{"DYNAMIC_REASONS":2}}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));

    RunSummary summary = aggregateWithJsonl(reader, "run-10c", false, null);

    assertFalse(summary.isDryRun());
    assertEquals(1, summary.getExcludedCount());
    assertEquals(0, summary.getWouldBeExcludedCount());
    assertEquals(1.0, summary.getExcludedRate(), 0.01);
  }

  @Test
  void testEnforceMode_withoutDynamicReport_countsExcluded() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":false,"exclusion_reason":"dynamic selection"}
        {"task_id":"t2","class_fqn":"com.example.Foo","method_name":"baz","selected":true,"feasibility_score":0.0}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));

    RunSummary summary = aggregateWithJsonl(reader, "run-10b", false, null);

    assertFalse(summary.isDryRun());
    assertEquals(2, summary.getExcludedCount());
    assertEquals(0, summary.getWouldBeExcludedCount());
    assertEquals(1.0, summary.getExcludedRate(), 0.01);
  }

  @Test
  void testEnforceMode_doesNotCountNonDynamicExclusionReason() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":false,"exclusion_reason":"manual review","feasibility_score":0.8}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));

    RunSummary summary = aggregateWithJsonl(reader, "run-10h", false, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(0, summary.getExcludedCount());
    assertEquals(0.0, summary.getExcludedRate(), 0.01);
  }

  @Test
  void testDuplicateTaskEntriesAreDeduplicated() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":true}
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":true}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));

    RunSummary summary = aggregateWithJsonl(reader, "run-10d", false, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(1, summary.getSelectedTasks());
  }

  @Test
  void testDuplicateEntriesWithoutTaskIdAreDeduplicatedCaseInsensitive() throws Exception {
    String jsonl =
        """
        {"class_fqn":"com.example.Foo","method_name":"Bar","selected":true}
        {"class_fqn":"COM.EXAMPLE.FOO","method_name":"bar","selected":false}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));

    RunSummary summary = aggregateWithJsonl(reader, "run-10e", false, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(1, summary.getSelectedTasks());
  }

  @Test
  void testDuplicateEntriesWithBlankTaskIdUseCompositeKey() throws Exception {
    String jsonl =
        """
        {"task_id":" ","class_fqn":"com.example.Foo","method_name":"Bar","selected":true}
        {"task_id":" ","class_fqn":"COM.EXAMPLE.FOO","method_name":"bar","selected":false}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));

    RunSummary summary = aggregateWithJsonl(reader, "run-10e-blank", false, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(1, summary.getSelectedTasks());
  }

  @Test
  void testDuplicateEntriesWithoutClassAndMethodAreDeduplicated() {
    TaskRecord task1 = new TaskRecord();
    task1.setSelected(true);
    TaskRecord task2 = new TaskRecord();
    task2.setSelected(false);

    RunSummary summary =
        aggregator.aggregate(
            List.of(new TaskEntry(task1), new TaskEntry(task2)), "run-10f", false, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(1, summary.getSelectedTasks());
  }

  @Test
  void testEntriesWithoutTaskPayloadAreIgnored() {
    TaskRecord task = new TaskRecord();
    task.setTaskId("t1");
    task.setClassFqn("com.example.Foo");
    task.setMethodName("bar");
    task.setSelected(true);

    RunSummary summary =
        aggregator.aggregate(
            List.of(new TaskEntry(null), new TaskEntry(task)), "run-10g", false, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(1, summary.getSelectedTasks());
  }

  @Test
  void testBlankLines_areSkipped() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":true}

        {"task_id":"t2","class_fqn":"com.example.Bar","method_name":"baz","selected":true}

        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    RunSummary summary = aggregateWithJsonl(reader, "run-11", false, null);

    assertEquals(2, summary.getTotalTasks());
    assertEquals(2, summary.getSelectedTasks());
  }

  @Test
  void testMalformedLine_isSkipped() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","selected":true}
        not-valid-json
        {"task_id":"t2","class_fqn":"com.example.Bar","method_name":"baz","selected":true}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    RunSummary summary = aggregateWithJsonl(reader, "run-12", false, null);

    // Malformed line is skipped, other 2 are processed
    assertEquals(2, summary.getTotalTasks());
  }

  @Test
  void testDryRunMode_ignoresUnsupportedDynamicShapes() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","selected":true,"feasibility_breakdown":{"shadow_score":"0","penalties":{"DYNAMIC_REASONS":"high"},"dynamic_reasons":[100,true]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    RunSummary summary = aggregateWithJsonl(reader, "run-unsupported-shapes", true, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(0, summary.getWouldBeExcludedCount());
  }

  @Test
  void testEnforceMode_countsConfidenceReasonWithoutDynamicKeyword() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","selected":false,"exclusion_reason":"confidence threshold","feasibility_score":1.0,"feasibility_breakdown":{"penalties":{"DYNAMIC_REASONS":0}}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    RunSummary summary = aggregateWithJsonl(reader, "run-confidence-only", false, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(1, summary.getExcludedCount());
  }

  @Test
  void testEnforceMode_ignoresExclusionWhenReasonMissingAndScoreMissing() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","selected":false,"feasibility_breakdown":{"penalties":"invalid"}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    RunSummary summary = aggregateWithJsonl(reader, "run-missing-reason", false, null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals(0, summary.getExcludedCount());
  }

  @Test
  void testJsonAndYamlFormats_matchJsonlResults() throws Exception {
    Map<String, Object> task1 = task("t1", "com.example.Foo", "bar", true);
    Map<String, Object> task2 = task("t2", "com.example.Foo", "baz", true);

    Path jsonl = tempDir.resolve("tasks.jsonl");
    Path json = tempDir.resolve("tasks.json");
    Path yaml = tempDir.resolve("tasks.yaml");

    Files.writeString(
        jsonl, mapper.writeValueAsString(task1) + "\n" + mapper.writeValueAsString(task2) + "\n");
    java.util.Map<String, Object> structured = new java.util.LinkedHashMap<>();
    structured.put("tasks", List.of(task1, task2));
    mapper.writeValue(json.toFile(), structured);
    new YAMLMapper().writeValue(yaml.toFile(), structured);

    RunSummary jsonlSummary;
    try (var jsonlReader = new DefaultTaskEntriesSource().read(jsonl)) {
      jsonlSummary = aggregator.aggregate(jsonlReader, "run-jsonl", false, null);
    }
    RunSummary jsonSummary;
    try (var jsonReader = new DefaultTaskEntriesSource().read(json)) {
      jsonSummary = aggregator.aggregate(jsonReader, "run-json", false, null);
    }
    RunSummary yamlSummary;
    try (var yamlReader = new DefaultTaskEntriesSource().read(yaml)) {
      yamlSummary = aggregator.aggregate(yamlReader, "run-yaml", false, null);
    }

    assertEquals(jsonlSummary.getTotalTasks(), jsonSummary.getTotalTasks());
    assertEquals(jsonlSummary.getSelectedTasks(), jsonSummary.getSelectedTasks());
    assertEquals(jsonlSummary.getTotalTasks(), yamlSummary.getTotalTasks());
    assertEquals(jsonlSummary.getSelectedTasks(), yamlSummary.getSelectedTasks());
  }

  private Map<String, Object> task(
      String id, String classFqn, String methodName, boolean selected) {
    return Map.of(
        "task_id", id, "class_fqn", classFqn, "method_name", methodName, "selected", selected);
  }

  private RunSummary aggregateWithJsonl(
      BufferedReader reader, String runId, boolean isDryRun, DynamicSelectionReport report)
      throws Exception {
    try (var iter = new DefaultTaskEntriesSource().readJsonl(reader)) {
      return aggregator.aggregate(iter, runId, isDryRun, report);
    }
  }
}
