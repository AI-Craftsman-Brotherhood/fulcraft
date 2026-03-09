package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.Config.ProjectConfig;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.yaml.YAMLMapper;

class YamlReportWriterTest {

  @TempDir Path tempDir;

  private final YAMLMapper mapper = new YAMLMapper();

  @Test
  void generateYaml_includesSummaryCoverageAndDetails() throws Exception {
    ReportData data = buildReportData();
    YamlReportWriter writer = new YamlReportWriter();

    String yaml = writer.generateYaml(data);

    Map<String, Object> payload =
        mapper.readValue(yaml, new TypeReference<LinkedHashMap<String, Object>>() {});
    assertThat(payload.get("schemaVersion")).isEqualTo("1.0");
    assertThat(payload.get("runId")).isEqualTo("run-1");
    assertThat(payload.get("analysisHumanSummary")).isEqualTo("Human-readable analysis summary.");

    Map<String, Object> summary = getMap(payload, "summary");
    assertThat(summary.get("totalTasks")).isEqualTo(3);

    Map<String, Object> coverage = getMap(payload, "coverage");
    assertThat(((Number) coverage.get("line")).doubleValue()).isEqualTo(0.5);
    assertThat(((Number) coverage.get("branch")).doubleValue()).isEqualTo(0.25);

    Map<String, Object> details = getMap(payload, "details");
    assertThat(details.get("taskCount")).isEqualTo(1);
    Map<String, Object> extensions = getMap(payload, "extensions");
    assertThat(extensions).containsKey("junit-bundled");
  }

  @Test
  void writeReport_omitsDetailsWhenDisabled() throws Exception {
    ReportData data = buildReportData();
    YamlReportWriter writer = new YamlReportWriter(null, null, false);
    Config config = new Config();
    ProjectConfig project = new ProjectConfig();
    project.setRoot(tempDir.toString());
    project.setId("proj-1");
    config.setProject(project);

    writer.writeReport(data, config);

    Path outputPath = tempDir.resolve("build/reports/test-generation/report.yaml");
    assertThat(outputPath).exists();
    Map<String, Object> payload =
        mapper.readValue(
            Files.readString(outputPath), new TypeReference<LinkedHashMap<String, Object>>() {});
    assertThat(payload).doesNotContainKey("details");
  }

  @Test
  void generateYaml_withoutSummary_usesAnalysisFallbackAndOmitsOptionalSections() throws Exception {
    TaskRecord selectedTask = new TaskRecord();
    selectedTask.setTaskId("task-selected");
    selectedTask.setSelected(true);

    ReportData data =
        ReportData.builder()
            .runId("run-fallback")
            .projectId("proj-fallback")
            .timestamp(Instant.parse("2025-01-02T03:04:05Z"))
            .totalClassesAnalyzed(4)
            .totalMethodsAnalyzed(9)
            .selectedTasks(List.of(selectedTask))
            .excludedTaskCount(2)
            .build();
    YamlReportWriter writer = new YamlReportWriter();

    String yaml = writer.generateYaml(data);

    Map<String, Object> payload =
        mapper.readValue(yaml, new TypeReference<LinkedHashMap<String, Object>>() {});
    Map<String, Object> summary = getMap(payload, "summary");
    assertThat(summary).containsEntry("classesAnalyzed", 4);
    assertThat(summary).containsEntry("methodsAnalyzed", 9);
    assertThat(summary).containsEntry("selectedTasks", 1);
    assertThat(summary).containsEntry("excludedTasks", 2);
    assertThat(payload).doesNotContainKeys("coverage", "details", "analysisHumanSummary");
  }

  @Test
  void generateYaml_handlesPartialCoverageAndOmitsNullTaskFields() throws Exception {
    GenerationTaskResult taskResult = new GenerationTaskResult();
    taskResult.setTaskId("task-minimal");
    taskResult.setClassFqn("com.example.Minimal");
    taskResult.setMethodName("run");
    taskResult.setStatus(GenerationTaskResult.Status.SUCCESS);

    ReportData data =
        ReportData.builder()
            .runId("run-null-time")
            .projectId("proj-null-time")
            .timestamp(null)
            .lineCoverage(0.75)
            .taskResults(List.of(taskResult))
            .build();

    YamlReportWriter writer = new YamlReportWriter();
    String yaml = writer.generateYaml(data);

    Map<String, Object> payload =
        mapper.readValue(yaml, new TypeReference<LinkedHashMap<String, Object>>() {});
    Map<String, Object> coverage = getMap(payload, "coverage");
    assertThat(coverage).containsOnlyKeys("line");
    assertThat(((Number) coverage.get("line")).doubleValue()).isEqualTo(0.75);

    Map<String, Object> details = getMap(payload, "details");
    List<Map<String, Object>> tasks = (List<Map<String, Object>>) details.get("tasks");
    assertThat(tasks).hasSize(1);
    Map<String, Object> task = tasks.getFirst();
    assertThat(task).containsKeys("taskId", "classFqn", "methodName", "status");
    assertThat(task)
        .doesNotContainKeys(
            "errorMessage", "errorCategory", "complexityStrategy", "generationResult");
  }

  @Test
  void writeReport_throwsReportWriteException_whenOutputPathIsNotDirectory() throws Exception {
    Path blockedPath = tempDir.resolve("blocked");
    Files.writeString(blockedPath, "not-a-directory");

    YamlReportWriter writer = new YamlReportWriter(blockedPath, "report.yaml", true);

    assertThatThrownBy(() -> writer.writeReport(buildReportData(), new Config()))
        .isInstanceOf(ReportWriteException.class);
  }

  @Test
  void getFormat_returnsYaml() {
    YamlReportWriter writer = new YamlReportWriter();
    assertThat(writer.getFormat()).isEqualTo(ReportFormat.YAML);
  }

  private ReportData buildReportData() {
    GenerationSummary summary = new GenerationSummary();
    summary.setProjectId("proj-1");
    summary.setTotalTasks(3);
    summary.setSucceeded(2);
    summary.setFailed(1);
    summary.setSkipped(0);
    summary.setSuccessRate(0.66);
    summary.setSuccessRateDelta(0.1);
    summary.setErrorCategoryCounts(Map.of("LLM_ERROR", 1));

    GenerationTaskResult taskResult = new GenerationTaskResult();
    taskResult.setTaskId("task-1");
    taskResult.setClassFqn("com.example.Foo");
    taskResult.setMethodName("doWork");
    taskResult.setStatus(GenerationTaskResult.Status.FAILURE);
    taskResult.setErrorMessage("boom");
    taskResult.setErrorCategory("LLM_ERROR");
    taskResult.setComplexityStrategy("HIGH");
    taskResult.setGenerationResult(
        GenerationResult.failure()
            .errorMessage("boom")
            .llmModelUsed("gpt-4")
            .tokenUsage(123)
            .build());

    return ReportData.builder()
        .runId("run-1")
        .projectId("proj-1")
        .timestamp(Instant.parse("2024-01-02T03:04:05Z"))
        .summary(summary)
        .analysisHumanSummary("Human-readable analysis summary.")
        .lineCoverage(0.5)
        .branchCoverage(0.25)
        .extensions(Map.of("junit-bundled", Map.of("brittle", Map.of("totalFindings", 1))))
        .taskResults(List.of(taskResult))
        .durationMs(1500)
        .errors(List.of("error one"))
        .warnings(List.of("warning one"))
        .build();
  }

  private Map<String, Object> getMap(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return new LinkedHashMap<>();
  }
}
