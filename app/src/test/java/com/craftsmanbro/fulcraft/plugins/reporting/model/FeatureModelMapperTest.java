package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeatureModelMapperTest {

  @Test
  void toTaskRecordReturnsSameInstanceWhenAlreadyTyped() {
    TaskRecord taskRecord = new TaskRecord();
    taskRecord.setTaskId("task-1");

    TaskRecord converted = FeatureModelMapper.toTaskRecord(taskRecord);

    assertSame(taskRecord, converted);
  }

  @Test
  void toTaskRecordConvertsMapAndIgnoresUnknownFields() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("task_id", "task-1");
    raw.put("project_id", "project-a");
    raw.put("class_fqn", "com.example.Foo");
    raw.put("method_name", "run");
    raw.put("selected", true);
    raw.put("feasibility_breakdown", Map.of("risk", 0.25));
    raw.put("unknown", "ignored");

    TaskRecord converted = FeatureModelMapper.toTaskRecord(raw);

    assertNotNull(converted);
    assertEquals("task-1", converted.getTaskId());
    assertEquals("project-a", converted.getProjectId());
    assertEquals("com.example.Foo", converted.getClassFqn());
    assertEquals("run", converted.getMethodName());
    assertEquals(Boolean.TRUE, converted.getSelected());
    assertNotNull(converted.getFeasibilityBreakdown());
    assertEquals(0.25, ((Number) converted.getFeasibilityBreakdown().get("risk")).doubleValue());
  }

  @Test
  void toTaskRecordsHandlesNonListAndFiltersNulls() {
    assertTrue(FeatureModelMapper.toTaskRecords("not-a-list").isEmpty());

    List<Object> rawList = new ArrayList<>();
    rawList.add(Map.of("task_id", "task-1"));
    rawList.add(null);
    rawList.add(Map.of("task_id", "task-2"));

    List<TaskRecord> converted = FeatureModelMapper.toTaskRecords(rawList);

    assertEquals(2, converted.size());
    assertEquals("task-1", converted.get(0).getTaskId());
    assertEquals("task-2", converted.get(1).getTaskId());
    assertThrows(UnsupportedOperationException.class, () -> converted.add(new TaskRecord()));
  }

  @Test
  void toGenerationTaskResultReturnsNullWhenConversionFails() {
    Map<String, Object> raw = Map.of("generatedTestFile", "bad\0path");

    GenerationTaskResult converted = FeatureModelMapper.toGenerationTaskResult(raw);

    assertNull(converted);
  }

  @Test
  void toGenerationTaskResultsConvertsEntriesAndReturnsImmutableList() {
    List<Object> rawList = new ArrayList<>();
    rawList.add(
        Map.of(
            "taskId",
            "task-1",
            "status",
            " success ",
            "generatedTestFile",
            "build/tests/FooTest.java"));
    rawList.add(null);
    rawList.add(Map.of("taskId", "task-2", "status", "failure"));

    List<GenerationTaskResult> converted = FeatureModelMapper.toGenerationTaskResults(rawList);

    assertEquals(2, converted.size());
    assertEquals("task-1", converted.get(0).getTaskId());
    assertEquals(GenerationTaskResult.Status.SUCCESS, converted.get(0).getStatusEnum());
    assertEquals(Path.of("build/tests/FooTest.java"), converted.get(0).getGeneratedTestFilePath());
    assertEquals(GenerationTaskResult.Status.FAILURE, converted.get(1).getStatusEnum());
    assertThrows(
        UnsupportedOperationException.class, () -> converted.add(new GenerationTaskResult()));
  }

  @Test
  void toGenerationSummaryAndFixErrorHistoryConvertFromMaps() {
    Map<String, Object> summaryRaw = new HashMap<>();
    summaryRaw.put("runId", "run-1");
    summaryRaw.put("projectId", "project-a");
    summaryRaw.put("totalTasks", 3);
    summaryRaw.put("details", List.of(Map.of("taskId", "task-1", "status", "SUCCESS")));

    GenerationSummary summary = FeatureModelMapper.toGenerationSummary(summaryRaw);

    assertNotNull(summary);
    assertEquals("run-1", summary.getRunId());
    assertEquals("project-a", summary.getProjectId());
    assertEquals(3, summary.getTotalTasks());
    assertEquals(1, summary.getDetails().size());
    assertEquals("task-1", summary.getDetails().get(0).getTaskId());

    Map<String, Object> fixHistoryRaw = new HashMap<>();
    fixHistoryRaw.put("taskId", "task-1");
    fixHistoryRaw.put("converged", true);
    fixHistoryRaw.put(
        "attempts",
        List.of(
            Map.of(
                "attemptNumber",
                1,
                "errorCategory",
                "COMPILE",
                "errorMessage",
                "boom",
                "timestampMs",
                10L)));

    FixErrorHistory history = FeatureModelMapper.toFixErrorHistory(fixHistoryRaw);

    assertNotNull(history);
    assertEquals("task-1", history.getTaskId());
    assertTrue(history.isConverged());
    assertEquals(1, history.getAttempts().size());
    assertEquals(1, history.getAttempts().get(0).getAttemptNumber());
  }
}
