package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportMetadataKeys;
import com.craftsmanbro.fulcraft.testsupport.KernelPortTestExtension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KernelPortTestExtension.class)
class ReportDataTest {

  @Test
  void builderCopiesCollectionsAndDefaultsErrorsWarnings() {
    TaskRecord task = new TaskRecord();
    List<TaskRecord> selected = new ArrayList<>();
    selected.add(task);

    List<String> errors = new ArrayList<>();
    errors.add("error");

    ReportData data =
        ReportData.builder()
            .runId("run-1")
            .projectId("project")
            .selectedTasks(selected)
            .errors(errors)
            .warnings(List.of("warn"))
            .build();

    selected.clear();
    errors.add("extra");

    assertEquals(1, data.getSelectedTasks().size());
    assertEquals(1, data.getErrors().size());
    assertTrue(data.hasErrors());
    assertTrue(data.hasWarnings());
    assertThrows(UnsupportedOperationException.class, () -> data.getSelectedTasks().add(task));

    ReportData emptyLists = ReportData.builder().runId("run-2").projectId("project").build();
    assertFalse(emptyLists.hasErrors());
    assertFalse(emptyLists.hasWarnings());
    assertNotNull(emptyLists.getErrors());
    assertNotNull(emptyLists.getWarnings());
  }

  @Test
  void fromSummaryMapsFields() {
    GenerationSummary summary = new GenerationSummary();
    summary.setRunId("run-1");
    summary.setProjectId("project");
    summary.setDurationMs(123L);
    summary.setLineCoverage(0.8);
    summary.setBranchCoverage(0.6);
    summary.setDetails(List.of(new GenerationTaskResult()));

    ReportData data = ReportData.fromSummary(summary);

    assertEquals("run-1", data.getRunId());
    assertEquals("project", data.getProjectId());
    assertEquals(0.8, data.getLineCoverage(), 0.0001);
    assertEquals(0.6, data.getBranchCoverage(), 0.0001);
    assertEquals(123L, data.getDurationMs());
    assertEquals(1, data.getTaskResults().size());
  }

  @Test
  void fromContextAggregatesMetadataAndCounts() {
    Config config = new Config();
    Config.ProjectConfig project = new Config.ProjectConfig();
    project.setId("project");
    config.setProject(project);

    RunContext context = new RunContext(Path.of("/tmp/project"), config, "run-1");

    TaskRecord selected = new TaskRecord();
    selected.setSelected(true);
    TaskRecord excluded = new TaskRecord();
    excluded.setSelected(false);
    TaskRecord unspecified = new TaskRecord();

    List<TaskRecord> selectedTasks = new ArrayList<>(List.of(selected, excluded, unspecified));
    context.putMetadata("tasks.selected", selectedTasks);
    context.putMetadata("tasks.generated", List.of(selected));

    AnalysisResult analysisResult = new AnalysisResult("project");
    ClassInfo classOne = new ClassInfo();
    classOne.setMethods(List.of(new MethodInfo(), new MethodInfo()));
    ClassInfo classTwo = new ClassInfo();
    classTwo.setMethods(List.of(new MethodInfo()));
    analysisResult.setClasses(List.of(classOne, classTwo));
    AnalysisResultContext.set(context, analysisResult);

    GenerationSummary summary = new GenerationSummary();
    summary.setRunId("run-1");
    summary.setProjectId("project");
    summary.setDurationMs(999L);
    summary.setLineCoverage(0.55);
    summary.setBranchCoverage(0.45);
    summary.setDetails(List.of(new GenerationTaskResult()));
    context.putMetadata("generation.summary", summary);
    context.putMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, "解析サマリーです。");
    context.putMetadata(
        ReportMetadataKeys.REPORT_EXTENSIONS,
        Map.of("junit-bundled", Map.of("brittle", Map.of("totalFindings", 3))));

    ReportTaskResult resultDef = new ReportTaskResult();
    resultDef.setStatus("success");
    context.setReportTaskResults(List.of(resultDef));
    context.setBrittlenessDetected(true);
    context.addError("error");
    context.addWarning("warning");

    ReportData data = ReportData.fromContext(context);

    assertEquals("run-1", data.getRunId());
    assertEquals("project", data.getProjectId());
    assertEquals(2, data.getTotalClassesAnalyzed());
    assertEquals(3, data.getTotalMethodsAnalyzed());
    assertEquals("解析サマリーです。", data.getAnalysisHumanSummary());
    assertEquals(3, data.getSelectedTasks().size());
    assertEquals(2, data.getExcludedTaskCount());
    assertEquals(1, data.getGeneratedTests().size());
    assertEquals(summary, data.getSummary());
    assertEquals(1, data.getTaskResults().size());
    assertEquals(1, data.getReportTaskResults().size());
    assertTrue(data.isBrittlenessDetected());
    assertEquals(
        Map.of("junit-bundled", Map.of("brittle", Map.of("totalFindings", 3))),
        data.getExtensions());
    assertEquals(0.55, data.getLineCoverage(), 0.0001);
    assertEquals(0.45, data.getBranchCoverage(), 0.0001);
    assertEquals(999L, data.getDurationMs());
    assertTrue(data.hasErrors());
    assertTrue(data.hasWarnings());
  }

  @Test
  void fromContext_excludesImplicitDefaultConstructorFromMethodCount() {
    Config config = new Config();
    Config.ProjectConfig project = new Config.ProjectConfig();
    project.setId("project");
    config.setProject(project);

    RunContext context = new RunContext(Path.of("/tmp/project"), config, "run-ctor-filter");

    AnalysisResult analysisResult = new AnalysisResult("project");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");

    MethodInfo implicitConstructor = new MethodInfo();
    implicitConstructor.setName("Sample");
    implicitConstructor.setSignature("Sample()");
    implicitConstructor.setParameterCount(0);
    implicitConstructor.setLoc(0);

    MethodInfo method = new MethodInfo();
    method.setName("execute");
    method.setLoc(4);

    classInfo.setMethods(List.of(implicitConstructor, method));
    analysisResult.setClasses(List.of(classInfo));
    AnalysisResultContext.set(context, analysisResult);

    ReportData data = ReportData.fromContext(context);

    assertEquals(1, data.getTotalMethodsAnalyzed());
  }
}
