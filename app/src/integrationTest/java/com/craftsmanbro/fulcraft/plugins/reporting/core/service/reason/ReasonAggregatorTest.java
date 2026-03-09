package com.craftsmanbro.fulcraft.plugins.reporting.core.service.reason;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonStats;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonSummary;
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
 * Tests for ReasonAggregator. Note: This aggregator processes task definitions (plan) only. It
 * tracks reason-based task counts and exclusion metrics. Execution results (compile/test pass
 * metrics) are not available from plan data.
 */
class ReasonAggregatorTest {

  private ReasonAggregator aggregator;
  private ObjectMapper mapper;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    aggregator = new ReasonAggregator();
  }

  @Test
  void testEmptyFile_returnsEmptyStats() throws Exception {
    BufferedReader reader = new BufferedReader(new StringReader(""));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-1", false);

    assertEquals("run-1", summary.getRunId());
    assertTrue(summary.getReasonStats().isEmpty());
  }

  @Test
  void testTaskWithNoReasons_notTracked() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar"}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-2", false);

    assertTrue(summary.getReasonStats().isEmpty());
  }

  @Test
  void testExtractReasons_returnsEmptyWhenBreakdownMissing() {
    TaskRecord task = new TaskRecord();

    List<String> reasons = aggregator.extractReasons(task);

    assertTrue(reasons.isEmpty());
  }

  @Test
  void testSingleReason_lowConf_tracked() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","feasibility_breakdown":{"dynamic_reasons":["low_confidence(0.75)<0.8"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-3", false);

    assertTrue(summary.getReasonStats().containsKey("low_conf"));
    ReasonStats stats = summary.getReasonStats().get("low_conf");
    assertEquals(1, stats.getTaskCount());
  }

  @Test
  void testMultipleReasons_sameTasks_bothTracked() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","feasibility_breakdown":{"dynamic_reasons":["low_confidence(0.60)<0.8","unresolved(2)*0.10"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-4", false);

    // Both reasons should be tracked
    assertTrue(summary.getReasonStats().containsKey("low_conf"));
    assertTrue(summary.getReasonStats().containsKey("unresolved"));

    // Each should count the same task
    ReasonStats lowConfStats = summary.getReasonStats().get("low_conf");
    assertEquals(1, lowConfStats.getTaskCount());

    ReasonStats unresolvedStats = summary.getReasonStats().get("unresolved");
    assertEquals(1, unresolvedStats.getTaskCount());
  }

  @Test
  void testMultipleTasks_aggregatesCorrectly() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.A","method_name":"m1","feasibility_breakdown":{"dynamic_reasons":["low_confidence(0.70)<0.8"]}}
        {"task_id":"t2","class_fqn":"com.example.B","method_name":"m2","feasibility_breakdown":{"dynamic_reasons":["low_confidence(0.65)<0.8"]}}
        {"task_id":"t3","class_fqn":"com.example.C","method_name":"m3","feasibility_breakdown":{"dynamic_reasons":["low_confidence(0.75)<0.8"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-8", false);

    ReasonStats stats = summary.getReasonStats().get("low_conf");
    assertEquals(3, stats.getTaskCount());
  }

  @Test
  void testDryRunMode_wouldBeExcluded_tracked() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","class_fqn":"com.example.Foo","method_name":"bar","feasibility_breakdown":{"dynamic_reasons":["SKIP:min_conf<0.5"],"shadow_score":0.0}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-9", true);

    assertTrue(summary.isDryRun());
    ReasonStats stats = summary.getReasonStats().get("skip_low_conf");
    assertEquals(1, stats.getTaskCount());
    assertEquals(1, stats.getWouldBeExcludedCount());
    assertEquals(1.0, stats.getWouldBeExcludedRate(), 0.01);
  }

  @Test
  void testNormalization_variousPatterns() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","feasibility_breakdown":{"dynamic_reasons":["low_confidence(0.45)<0.8"]}}
        {"task_id":"t2","feasibility_breakdown":{"dynamic_reasons":["unresolved(5)*0.10"]}}
        {"task_id":"t3","feasibility_breakdown":{"dynamic_reasons":["external(3)*0.20"]}}
        {"task_id":"t4","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"]}}
        {"task_id":"t5","feasibility_breakdown":{"dynamic_reasons":["SKIP:min_conf<0.5"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-10", false);

    // All patterns should normalize correctly
    assertTrue(summary.getReasonStats().containsKey("low_conf"));
    assertTrue(summary.getReasonStats().containsKey("unresolved"));
    assertTrue(summary.getReasonStats().containsKey("external"));
    assertTrue(summary.getReasonStats().containsKey("spi_low_conf"));
    assertTrue(summary.getReasonStats().containsKey("skip_low_conf"));
  }

  @Test
  void testBlankLines_skipped() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"]}}

        {"task_id":"t2","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-11", false);

    ReasonStats stats = summary.getReasonStats().get("spi_low_conf");
    assertEquals(2, stats.getTaskCount());
  }

  @Test
  void testMalformedLine_skipped() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"]}}
        not-valid-json
        {"task_id":"t2","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-12", false);

    ReasonStats stats = summary.getReasonStats().get("spi_low_conf");
    assertEquals(2, stats.getTaskCount());
  }

  @Test
  void testRateCalculations_edgeCases() throws Exception {
    ReasonStats stats = new ReasonStats();

    // Zero division protection
    assertEquals(0.0, stats.getCompileFailRate());
    assertEquals(0.0, stats.getRuntimeFailRate());
    assertEquals(0.0, stats.getFixAttemptRate());
    assertEquals(0.0, stats.getFixSuccessRate());
    assertEquals(0.0, stats.getExcludedRate());
  }

  @Test
  void testSignalsReasons_extractedAndTracked() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","feasibility_breakdown":{"signals":{"dynamicReasons":["low_confidence(0.75)<0.8"]}}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-signals", false);

    assertTrue(summary.getReasonStats().containsKey("low_conf"));
    assertEquals(1, summary.getReasonStats().get("low_conf").getTaskCount());
  }

  @Test
  void testMixedSources_deduplicated() throws Exception {
    // Both dynamic_reasons and signals contain the same reason -> count 1
    String jsonl =
        """
        {"task_id":"t1","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"],"signals":{"dynamicReasons":["spi_low_conf"]}}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-dedup", false);

    ReasonStats stats = summary.getReasonStats().get("spi_low_conf");
    assertEquals(1, stats.getTaskCount());
  }

  @Test
  void testSignalReasons_ignoresUnsupportedShapes() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"],"signals":{"dynamicReasons":"spi_low_conf"}}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-signal-shape", false);

    ReasonStats stats = summary.getReasonStats().get("spi_low_conf");
    assertEquals(1, stats.getTaskCount());
  }

  @Test
  void testExcluded_enforceMode_dynamicPenalty() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","feasibility_breakdown":{"dynamic_reasons":["low_confidence(0.5)<0.8"],"penalties":{"DYNAMIC_REASONS":1.0}}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-excluded-1", false);

    ReasonStats stats = summary.getReasonStats().get("low_conf");
    assertEquals(1, stats.getTaskCount());
    assertEquals(1, stats.getExcludedCount());
    assertEquals(0.0, stats.getWouldBeExcludedRate(), 0.01); // Not dry run
  }

  @Test
  void testExcluded_enforceMode_exclusionReason() throws Exception {
    // Logic: selected=false, exclusion_reason contains "dynamic" or "confidence"
    String jsonl =
        """
        {"task_id":"t1","selected":false,"exclusion_reason":"dynamic confidence too low","feasibility_breakdown":{"dynamic_reasons":["low_confidence(0.5)<0.8"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-excluded-2", false);

    ReasonStats stats = summary.getReasonStats().get("low_conf");
    assertEquals(1, stats.getTaskCount());
    assertEquals(1, stats.getExcludedCount());
  }

  @Test
  void testExcluded_enforceMode_lowScore() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","feasibility_score":0.0,"feasibility_breakdown":{"dynamic_reasons":["low_confidence(0.5)<0.8"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-excluded-3", false);

    ReasonStats stats = summary.getReasonStats().get("low_conf");
    assertEquals(1, stats.getTaskCount());
    assertEquals(1, stats.getExcludedCount());
  }

  @Test
  void testJsonAndYamlFormats_matchJsonlResults() throws Exception {
    Map<String, Object> task =
        Map.of(
            "task_id",
            "t1",
            "class_fqn",
            "com.example.Foo",
            "method_name",
            "bar",
            "feasibility_breakdown",
            Map.of("dynamic_reasons", List.of("low_confidence(0.60)<0.8")));

    Path jsonl = tempDir.resolve("tasks.jsonl");
    Path json = tempDir.resolve("tasks.json");
    Path yaml = tempDir.resolve("tasks.yaml");

    Files.writeString(jsonl, mapper.writeValueAsString(task) + "\n");
    java.util.Map<String, Object> structured = new java.util.LinkedHashMap<>();
    structured.put("tasks", List.of(task));
    mapper.writeValue(json.toFile(), structured);
    new YAMLMapper().writeValue(yaml.toFile(), structured);

    ReasonSummary jsonlSummary;
    try (var jsonlReader = new DefaultTaskEntriesSource().read(jsonl)) {
      jsonlSummary = aggregator.aggregate(jsonlReader, "run-jsonl", false);
    }
    ReasonSummary jsonSummary;
    try (var jsonReader = new DefaultTaskEntriesSource().read(json)) {
      jsonSummary = aggregator.aggregate(jsonReader, "run-json", false);
    }
    ReasonSummary yamlSummary;
    try (var yamlReader = new DefaultTaskEntriesSource().read(yaml)) {
      yamlSummary = aggregator.aggregate(yamlReader, "run-yaml", false);
    }

    assertEquals(
        jsonlSummary.getReasonStats().get("low_conf").getTaskCount(),
        jsonSummary.getReasonStats().get("low_conf").getTaskCount());
    assertEquals(
        jsonlSummary.getReasonStats().get("low_conf").getTaskCount(),
        yamlSummary.getReasonStats().get("low_conf").getTaskCount());
  }

  @Test
  void testDuplicateTasks_withoutTaskId_deduplicatedByCompositeKey() throws Exception {
    String jsonl =
        """
        {"class_fqn":"com.example.Foo","method_name":"Bar","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"]}}
        {"class_fqn":"com.example.foo","method_name":"bar","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-dedup-no-task-id", false);

    ReasonStats stats = summary.getReasonStats().get("spi_low_conf");
    assertEquals(1, stats.getTaskCount());
  }

  @Test
  void testDuplicateTasks_withBlankTaskId_deduplicatedByCompositeKey() throws Exception {
    String jsonl =
        """
        {"task_id":" ","class_fqn":"com.example.Foo","method_name":"Bar","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"]}}
        {"task_id":" ","class_fqn":"com.example.foo","method_name":"bar","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-dedup-blank-task-id", false);

    ReasonStats stats = summary.getReasonStats().get("spi_low_conf");
    assertEquals(1, stats.getTaskCount());
  }

  @Test
  void testDuplicateTasks_missingClassAndMethod_usesUnknownFallbackKey() {
    TaskRecord task1 = new TaskRecord();
    task1.setFeasibilityBreakdown(Map.of("dynamic_reasons", List.of("spi_low_conf")));
    TaskRecord task2 = new TaskRecord();
    task2.setFeasibilityBreakdown(Map.of("dynamic_reasons", List.of("spi_low_conf")));
    var entries = List.of(new TaskEntry(task1), new TaskEntry(task2));
    ReasonSummary summary = aggregator.aggregate(entries, "run-dedup-unknown-key", false);

    ReasonStats stats = summary.getReasonStats().get("spi_low_conf");
    assertEquals(1, stats.getTaskCount());
  }

  @Test
  void testNormalizeReason_boundaryInputs() {
    assertNull(aggregator.normalizeReason(null));
    assertNull(aggregator.normalizeReason("   "));
    assertEquals("custom_reason", aggregator.normalizeReason("  custom_reason  "));
  }

  @Test
  void testExtractReasons_ignoresNonStringEntriesAndUnsupportedShapes() {
    var task = new TaskRecord();
    task.setFeasibilityBreakdown(
        Map.of(
            "dynamic_reasons",
            List.of("spi_low_conf", 100, true),
            "signals",
            Map.of("dynamicReasons", List.of("spi_low_conf", "external(1)*0.20", 200))));

    List<String> reasons = aggregator.extractReasons(task);
    assertEquals(List.of("spi_low_conf", "external(1)*0.20"), reasons);
  }

  @Test
  void testDryRunMode_ignoresUnsupportedPenaltyAndSkipShapes() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","feasibility_breakdown":{"dynamic_reasons":["spi_low_conf",100],"shadow_score":"0","penalties":{"DYNAMIC_REASONS":0}}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-dry-unsupported-shapes", true);

    ReasonStats stats = summary.getReasonStats().get("spi_low_conf");
    assertEquals(1, stats.getTaskCount());
    assertEquals(0, stats.getWouldBeExcludedCount());
  }

  @Test
  void testDryRunMode_dynamicPenalty_marksWouldBeExcluded() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","feasibility_breakdown":{"dynamic_reasons":["low_confidence(0.60)<0.8"],"penalties":{"DYNAMIC_REASONS":0.5}}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-dry-penalty", true);

    ReasonStats stats = summary.getReasonStats().get("low_conf");
    assertEquals(1, stats.getTaskCount());
    assertEquals(1, stats.getWouldBeExcludedCount());
    assertEquals(0, stats.getExcludedCount());
  }

  @Test
  void testDryRunMode_skipPrefixWithoutShadowScore_marksWouldBeExcluded() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","feasibility_breakdown":{"dynamic_reasons":["SKIP:min_conf<0.6"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-dry-skip", true);

    ReasonStats stats = summary.getReasonStats().get("skip_low_conf");
    assertEquals(1, stats.getTaskCount());
    assertEquals(1, stats.getWouldBeExcludedCount());
  }

  @Test
  void testExcluded_enforceMode_confidenceOnlyReason_countsExcluded() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","selected":false,"exclusion_reason":"confidence threshold","feasibility_score":1.0,"feasibility_breakdown":{"dynamic_reasons":["spi_low_conf"],"penalties":{"DYNAMIC_REASONS":0}}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-confidence-only", false);

    ReasonStats stats = summary.getReasonStats().get("spi_low_conf");
    assertEquals(1, stats.getTaskCount());
    assertEquals(1, stats.getExcludedCount());
  }

  @Test
  void testExcluded_enforceMode_withoutDynamicSignals_notExcluded() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","selected":false,"exclusion_reason":"manual filter","feasibility_score":0.9,"feasibility_breakdown":{"dynamic_reasons":["low_confidence(0.5)<0.8"]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-not-excluded", false);

    ReasonStats stats = summary.getReasonStats().get("low_conf");
    assertEquals(1, stats.getTaskCount());
    assertEquals(0, stats.getExcludedCount());
  }

  @Test
  void testReasonNormalizationToNull_skipsReasonEntry() throws Exception {
    String jsonl =
        """
        {"task_id":"t1","feasibility_breakdown":{"dynamic_reasons":["   "]}}
        """;
    BufferedReader reader = new BufferedReader(new StringReader(jsonl));
    ReasonSummary summary = aggregateWithJsonl(reader, "run-blank-reason", false);

    assertTrue(summary.getReasonStats().isEmpty());
  }

  @Test
  void testAggregate_ignoresEntriesWithoutTaskPayload() {
    ReasonSummary summary =
        aggregator.aggregate(List.of(new TaskEntry(null)), "run-empty-entry", false);

    assertTrue(summary.getReasonStats().isEmpty());
  }

  @Test
  void testAggregate_nullEntries_throwsException() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> aggregator.aggregate(null, "run", false));
    assertEquals("entries", exception.getMessage());
  }

  private ReasonSummary aggregateWithJsonl(BufferedReader reader, String runId, boolean isDryRun)
      throws Exception {
    try (var iter = new DefaultTaskEntriesSource().readJsonl(reader)) {
      return aggregator.aggregate(iter, runId, isDryRun);
    }
  }
}
