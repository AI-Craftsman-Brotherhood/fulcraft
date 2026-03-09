package com.craftsmanbro.fulcraft.plugins.reporting.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportMetadataKeys;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportingContext;
import com.craftsmanbro.fulcraft.plugins.reporting.core.model.TasksSnapshot;
import com.craftsmanbro.fulcraft.plugins.reporting.io.contract.CoverageReader;
import com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportHistory;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportingServiceTest {

  private static final String TEST_PROJECT_ID = "test-project";
  private static final String TEST_RUN_ID = "test-run-123";

  @TempDir Path tempDir;

  private ReportingService service;
  private ReportingContext reportingContext;
  private RunContext runContext;
  private Config config;

  @Mock private CoverageReader coverageReader;
  @Mock private LlmClientPort llmClient;

  @BeforeEach
  void setUp() {
    config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setId(TEST_PROJECT_ID);

    runContext = new RunContext(tempDir, config, TEST_RUN_ID);
    reportingContext = new ReportingContext(runContext, config, tempDir, coverageReader);
    service = new ReportingService(reportingContext);
  }

  @Test
  void testBuildSummary_populatesProjectMetadata() {
    TasksSnapshot snapshot = TasksSnapshot.empty();
    GenerationSummary summary = service.buildSummary(snapshot);

    assertEquals(TEST_PROJECT_ID, summary.getProjectId());
    assertEquals(TEST_RUN_ID, summary.getRunId());
    assertTrue(summary.getTimestamp() > 0);
  }

  @Test
  void testBuildSummary_countsTasksCorrectly() {
    List<TaskRecord> tasks = createSampleTasks();
    TasksSnapshot snapshot = new TasksSnapshot(tasks);
    GenerationSummary summary = service.buildSummary(snapshot);

    assertEquals(3, summary.getTotalTasks());
    assertNotNull(summary.getDetails());
    assertEquals(3, summary.getDetails().size());
  }

  @Test
  void testBuildSummary_usesUnknownProjectWhenProjectIdMissing() {
    Config configWithoutProject = new Config();
    RunContext localRunContext = new RunContext(tempDir, configWithoutProject, TEST_RUN_ID);
    ReportingContext localReportingContext =
        new ReportingContext(localRunContext, configWithoutProject, tempDir, coverageReader);
    ReportingService localService = new ReportingService(localReportingContext);

    GenerationSummary summary = localService.buildSummary(TasksSnapshot.empty());

    assertEquals(MessageSource.getMessage("report.value.unknown_project"), summary.getProjectId());
  }

  @Test
  void testBuildSummary_usesUnknownProjectWhenProjectIdIsNull() {
    Config configWithoutId = new Config();
    configWithoutId.setProject(new Config.ProjectConfig());
    RunContext localRunContext = new RunContext(tempDir, configWithoutId, TEST_RUN_ID);
    ReportingContext localReportingContext =
        new ReportingContext(localRunContext, configWithoutId, tempDir, coverageReader);
    ReportingService localService = new ReportingService(localReportingContext);

    GenerationSummary summary = localService.buildSummary(TasksSnapshot.empty());

    assertEquals(MessageSource.getMessage("report.value.unknown_project"), summary.getProjectId());
  }

  @Test
  void testBuildSummary_calculatesSkippedCount() {
    TaskRecord selected = createTaskRecord("task1", true);
    TaskRecord skipped = createTaskRecord("task2", false);
    skipped.setExclusionReason("Excluded by filter");

    TasksSnapshot snapshot = new TasksSnapshot(List.of(selected, skipped));

    GenerationSummary summary = service.buildSummary(snapshot);

    assertEquals(2, summary.getTotalTasks());
    assertEquals(2, summary.getSkipped());
  }

  @Test
  void testBuildSummary_marksNullTaskAsSkippedWithDefinitionNullMessage() {
    List<TaskRecord> tasks = new ArrayList<>();
    tasks.add(null);

    GenerationSummary summary = service.buildSummary(new TasksSnapshot(tasks));

    assertEquals(1, summary.getSkipped());
    GenerationTaskResult result = summary.getDetails().get(0);
    assertEquals("SKIPPED", result.getStatus());
    assertEquals(MessageSource.getMessage("report.task.definition_null"), result.getErrorMessage());
  }

  @Test
  void testBuildSummary_setsNotRecordedMessageForSelectedTask() {
    TaskRecord selected = createTaskRecord("task1", true);

    GenerationSummary summary = service.buildSummary(new TasksSnapshot(List.of(selected)));

    GenerationTaskResult result = summary.getDetails().get(0);
    assertEquals("SKIPPED", result.getStatus());
    assertEquals(
        MessageSource.getMessage("report.task.result_not_recorded"), result.getErrorMessage());
  }

  @Test
  void testBuildReportData_includesAllContextData() {
    List<TaskRecord> selectedTasks = createSampleTasks();
    runContext.putMetadata("tasks.selected", selectedTasks);

    TasksSnapshot snapshot = new TasksSnapshot(selectedTasks);
    GenerationSummary summary = service.buildSummary(snapshot);

    ReportData reportData = service.buildReportData(summary);

    assertEquals(TEST_RUN_ID, reportData.getRunId());
    assertEquals(TEST_PROJECT_ID, reportData.getProjectId());
    assertNotNull(reportData.getSummary());
  }

  @Test
  void testBuildReportData_includesErrorsAndWarnings() {
    runContext.addError("Test error 1");
    runContext.addError("Test error 2");
    runContext.addWarning("Test warning");

    GenerationSummary summary = service.buildSummary(TasksSnapshot.empty());

    ReportData reportData = service.buildReportData(summary);

    assertEquals(2, reportData.getErrors().size());
    assertEquals(1, reportData.getWarnings().size());
    assertTrue(reportData.hasErrors());
    assertTrue(reportData.hasWarnings());
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_skipsWhenLlmNotConfigured() {
    AnalysisResult analysisResult = new AnalysisResult(TEST_PROJECT_ID);
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Foo");
    analysisResult.setClasses(List.of(classInfo));
    AnalysisResultContext.set(runContext, analysisResult);

    service.generateHumanReadableAnalysisSummary(new GenerationSummary());

    assertFalse(
        runContext
            .getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class)
            .isPresent());
    verifyNoInteractions(llmClient);
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_storesGeneratedSummary() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    AnalysisResult analysisResult = createAnalysisResultWithComplexMethod();
    AnalysisResultContext.set(runContext, analysisResult);

    GenerationSummary generationSummary = new GenerationSummary();
    generationSummary.setTotalTasks(5);
    generationSummary.setSucceeded(4);
    generationSummary.setFailed(1);
    generationSummary.setSkipped(0);
    generationSummary.setSuccessRate(0.8);

    when(llmClient.generateTest(anyString(), eq(llmConfig))).thenReturn("```text\n要約です。\n```");

    llmEnabledService.generateHumanReadableAnalysisSummary(generationSummary);

    assertEquals(
        "要約です。",
        runContext
            .getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class)
            .orElse(null));
    verify(llmClient).generateTest(anyString(), eq(llmConfig));
    verify(llmClient).clearContext();
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_clearsExistingSummaryBeforeEarlyReturn() {
    runContext.putMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, "old summary");

    service.generateHumanReadableAnalysisSummary(new GenerationSummary());

    assertFalse(
        runContext
            .getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class)
            .isPresent());
    verifyNoInteractions(llmClient);
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_skipsWhenAnalysisClassesAreEmpty() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    AnalysisResult emptyAnalysis = new AnalysisResult(TEST_PROJECT_ID);
    emptyAnalysis.setClasses(List.of());
    AnalysisResultContext.set(runContext, emptyAnalysis);

    llmEnabledService.generateHumanReadableAnalysisSummary(new GenerationSummary());

    assertFalse(
        runContext
            .getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class)
            .isPresent());
    verifyNoInteractions(llmClient);
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_doesNotStoreBlankResponse() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    AnalysisResultContext.set(runContext, createAnalysisResultWithComplexMethod());
    when(llmClient.generateTest(anyString(), eq(llmConfig))).thenReturn("   \n");

    llmEnabledService.generateHumanReadableAnalysisSummary(new GenerationSummary());

    assertFalse(
        runContext
            .getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class)
            .isPresent());
    verify(llmClient).clearContext();
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_truncatesLongResponse() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    AnalysisResultContext.set(runContext, createAnalysisResultWithComplexMethod());
    when(llmClient.generateTest(anyString(), eq(llmConfig))).thenReturn("a".repeat(3200));

    llmEnabledService.generateHumanReadableAnalysisSummary(new GenerationSummary());

    String summary =
        runContext.getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class).orElse("");
    assertEquals(3000, summary.length());
    verify(llmClient).clearContext();
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_addsWarningWhenLlmFails() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    AnalysisResultContext.set(runContext, createAnalysisResultWithComplexMethod());
    when(llmClient.generateTest(anyString(), eq(llmConfig)))
        .thenThrow(new IllegalStateException("simulated failure"));

    llmEnabledService.generateHumanReadableAnalysisSummary(new GenerationSummary());

    assertFalse(
        runContext
            .getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class)
            .isPresent());
    assertTrue(
        runContext.getWarnings().stream()
            .anyMatch(warning -> warning.contains("simulated failure")));
    verify(llmClient).clearContext();
  }

  @Test
  void testBuildSummary_createsSkippedResultsForAllTasks() {
    // Since results are no longer merged from snapshot, all tasks become SKIPPED
    TaskRecord task1 = createTaskRecord("task1", true);
    TaskRecord task2 = createTaskRecord("task2", true);

    TasksSnapshot snapshot = new TasksSnapshot(List.of(task1, task2));
    GenerationSummary summary = service.buildSummary(snapshot);

    assertEquals(2, summary.getTotalTasks());
    assertEquals(2, summary.getDetails().size());

    // All tasks should be SKIPPED since there are no execution results
    assertEquals(2, summary.getSkipped());
  }

  @Test
  void testBuildSummary_prefersGeneratedTasksFromContextWhenSnapshotEmpty() {
    List<TaskRecord> selectedTasks = List.of(createTaskRecord("selected", true));
    List<TaskRecord> generatedTasks =
        List.of(createTaskRecord("generated1", true), createTaskRecord("generated2", true));
    runContext.putMetadata("tasks.selected", selectedTasks);
    runContext.putMetadata("tasks.generated", generatedTasks);

    GenerationSummary summary = service.buildSummary(TasksSnapshot.empty());

    assertEquals(2, summary.getTotalTasks());
    assertEquals(2, summary.getDetails().size());
    assertTrue(findResultByTaskId(summary.getDetails(), "generated1").isPresent());
    assertTrue(findResultByTaskId(summary.getDetails(), "generated2").isPresent());
  }

  @Test
  void testBuildSummary_usesSelectedTasksWhenGeneratedTasksMissing() {
    List<TaskRecord> selectedTasks =
        List.of(createTaskRecord("selected1", true), createTaskRecord("selected2", true));
    runContext.putMetadata("tasks.selected", selectedTasks);

    GenerationSummary summary = service.buildSummary(TasksSnapshot.empty());

    assertEquals(2, summary.getTotalTasks());
    assertEquals(2, summary.getDetails().size());
    Set<String> taskIds =
        summary.getDetails().stream()
            .map(GenerationTaskResult::getTaskId)
            .collect(Collectors.toSet());
    assertEquals(Set.of("selected1", "selected2"), taskIds);
  }

  @Test
  void testBuildSummary_handlesNullSnapshotAndFallsBackToMetadataTasks() {
    List<TaskRecord> generatedTasks = List.of(createTaskRecord("generated-null", true));
    runContext.putMetadata("tasks.generated", generatedTasks);

    GenerationSummary summary = service.buildSummary(null);

    assertEquals(1, summary.getTotalTasks());
    assertEquals("generated-null", summary.getDetails().get(0).getTaskId());
  }

  @Test
  void testBuildSummary_recordsDuration() {
    long startTime = Instant.now().minusMillis(1500).toEpochMilli();
    runContext.putMetadata("startTime", startTime);

    GenerationSummary summary = service.buildSummary(TasksSnapshot.empty());

    assertTrue(summary.getDurationMs() >= 0);
  }

  @Test
  void testApplyCoverageIntegration_setsCoverageValues() {
    GenerationSummary summary = service.buildSummary(TasksSnapshot.empty());
    CoverageSummary coverageSummary = new CoverageSummary();
    coverageSummary.setLineCovered(80);
    coverageSummary.setLineTotal(100);
    coverageSummary.setBranchCovered(25);
    coverageSummary.setBranchTotal(50);

    service.applyCoverageIntegration(summary, coverageSummary);

    assertEquals(0.8, summary.getLineCoverage(), 0.0001);
    assertEquals(0.5, summary.getBranchCoverage(), 0.0001);
  }

  @Test
  void testApplyTrendAnalysis_updatesDeltaAndHistory() {
    GenerationSummary summary = new GenerationSummary();
    summary.setProjectId(TEST_PROJECT_ID);
    summary.setRunId(TEST_RUN_ID);
    summary.setTimestamp(123L);
    summary.setTotalTasks(10);
    summary.setSucceeded(8);
    summary.setFailed(2);
    summary.setSkipped(0);
    summary.setSuccessRate(0.8);

    ReportHistory history =
        new ReportHistory(List.of(new ReportHistory.HistoricalRun("prev", 10L, 10, 5, 5, 0, 0.5)));

    ReportHistory updated = service.applyTrendAnalysis(summary, history);

    assertEquals(2, updated.size());
    assertEquals(0.3, summary.getSuccessRateDelta(), 0.0001);
  }

  @Test
  void testGenerateAllFormats_throwsWhenNoWriters() {
    ReportData reportData = ReportData.fromSummary(service.buildSummary(TasksSnapshot.empty()));

    ReportWriteException exception =
        assertThrows(
            ReportWriteException.class,
            () -> service.generateAllFormats(reportData, Collections.emptyMap()));

    assertEquals(MessageSource.getMessage("report.error.no_writers"), exception.getMessage());
  }

  @Test
  void testGenerateAllFormats_throwsWhenConfiguredFormatMissing() {
    config.getOutput().getFormat().setReport("html");
    ReportData reportData = ReportData.fromSummary(service.buildSummary(TasksSnapshot.empty()));

    Map<ReportFormat, ReportWriterPort> writers =
        Map.of(ReportFormat.JSON, mock(ReportWriterPort.class));

    ReportWriteException exception =
        assertThrows(
            ReportWriteException.class, () -> service.generateAllFormats(reportData, writers));

    assertTrue(
        exception
            .getMessage()
            .contains(MessageSource.getMessage("report.error.no_writer", ReportFormat.HTML)));
  }

  @Test
  void testGenerateAllFormats_invokesWriterForConfiguredFormat() throws ReportWriteException {
    config.getOutput().getFormat().setReport("json");
    ReportWriterPort writer = mock(ReportWriterPort.class);
    ReportData reportData = ReportData.fromSummary(service.buildSummary(TasksSnapshot.empty()));

    service.generateAllFormats(reportData, Map.of(ReportFormat.JSON, writer));

    verify(writer).writeReport(reportData, config);
  }

  @Test
  void testGenerateAllFormats_fallsBackToMarkdownForUnknownFormat() throws ReportWriteException {
    config.getOutput().getFormat().setReport("unknown-format");
    ReportWriterPort writer = mock(ReportWriterPort.class);
    ReportData reportData = ReportData.fromSummary(service.buildSummary(TasksSnapshot.empty()));

    service.generateAllFormats(reportData, Map.of(ReportFormat.MARKDOWN, writer));

    verify(writer).writeReport(reportData, config);
  }

  @Test
  void testGenerateAllFormats_usesMarkdownWhenOutputConfigIsNull() throws ReportWriteException {
    config.setOutput(null);
    ReportWriterPort writer = mock(ReportWriterPort.class);
    ReportData reportData = ReportData.fromSummary(service.buildSummary(TasksSnapshot.empty()));

    service.generateAllFormats(reportData, Map.of(ReportFormat.MARKDOWN, writer));

    verify(writer).writeReport(reportData, config);
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_skipsWhenAnalysisResultMissing() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    llmEnabledService.generateHumanReadableAnalysisSummary(new GenerationSummary());

    assertFalse(
        runContext
            .getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class)
            .isPresent());
    verifyNoInteractions(llmClient);
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_skipsWhenProviderBlankEvenWithClient() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("   ");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    AnalysisResultContext.set(runContext, createAnalysisResultWithComplexMethod());
    llmEnabledService.generateHumanReadableAnalysisSummary(new GenerationSummary());

    verifyNoInteractions(llmClient);
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_handlesNullLlmResponse() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    AnalysisResultContext.set(runContext, createAnalysisResultWithComplexMethod());
    when(llmClient.generateTest(anyString(), eq(llmConfig))).thenReturn(null);

    llmEnabledService.generateHumanReadableAnalysisSummary(new GenerationSummary());

    assertFalse(
        runContext
            .getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class)
            .isPresent());
    verify(llmClient).clearContext();
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_preservesUnclosedCodeFence() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    AnalysisResultContext.set(runContext, createAnalysisResultWithComplexMethod());
    when(llmClient.generateTest(anyString(), eq(llmConfig)))
        .thenReturn("```markdown\nsummary without closing fence");

    llmEnabledService.generateHumanReadableAnalysisSummary(new GenerationSummary());

    String summary =
        runContext.getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class).orElse("");
    assertTrue(summary.startsWith("```markdown"));
    verify(llmClient).clearContext();
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_handlesSparseAnalysisData() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    AnalysisResult analysisResult = new AnalysisResult(TEST_PROJECT_ID);
    MethodInfo low = new MethodInfo();
    low.setName(" ");
    low.setCyclomaticComplexity(2);
    low.setLoc(-1);
    MethodInfo high = new MethodInfo();
    high.setName("criticalPath");
    high.setCyclomaticComplexity(20);
    high.setLoc(80);
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn(" ");
    classInfo.setLoc(-10);
    List<MethodInfo> methods = new ArrayList<>();
    methods.add(low);
    methods.add(null);
    methods.add(high);
    classInfo.setMethods(methods);
    List<ClassInfo> classes = new ArrayList<>();
    classes.add(null);
    classes.add(classInfo);
    analysisResult.setClasses(classes);
    AnalysisResultContext.set(runContext, analysisResult);

    when(llmClient.generateTest(anyString(), eq(llmConfig))).thenReturn("summary");

    llmEnabledService.generateHumanReadableAnalysisSummary(null);

    ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
    verify(llmClient).generateTest(promptCaptor.capture(), eq(llmConfig));
    String prompt = promptCaptor.getValue();
    assertTrue(prompt.contains(MessageSource.getMessage("report.value.unknown_class")));
    assertTrue(prompt.contains(MessageSource.getMessage("report.value.unknown_method")));
    assertFalse(prompt.contains("Generation total tasks"));
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_handlesNullValuesAndFenceWithoutNewline() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    AnalysisResult analysisResult = new AnalysisResult(TEST_PROJECT_ID);

    ClassInfo emptyClass = new ClassInfo();
    emptyClass.setFqn(null);
    emptyClass.setLoc(-5);
    emptyClass.setMethods(new ArrayList<>());

    MethodInfo methodInfo = new MethodInfo();
    methodInfo.setName(null);
    methodInfo.setCyclomaticComplexity(-1);
    methodInfo.setLoc(-2);
    ClassInfo classWithNullMethodName = new ClassInfo();
    classWithNullMethodName.setFqn("com.example.Mixed");
    classWithNullMethodName.setLoc(30);
    classWithNullMethodName.setMethods(List.of(methodInfo));

    analysisResult.setClasses(List.of(emptyClass, classWithNullMethodName));
    AnalysisResultContext.set(runContext, analysisResult);

    GenerationSummary generationSummary = new GenerationSummary();
    generationSummary.setTotalTasks(2);
    generationSummary.setSucceeded(1);
    generationSummary.setFailed(1);
    generationSummary.setSkipped(0);
    when(llmClient.generateTest(anyString(), eq(llmConfig))).thenReturn("```");

    llmEnabledService.generateHumanReadableAnalysisSummary(generationSummary);

    ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
    verify(llmClient).generateTest(promptCaptor.capture(), eq(llmConfig));
    String prompt = promptCaptor.getValue();
    assertTrue(prompt.contains("Generation total tasks"));
    assertFalse(prompt.contains("Generation success rate"));
    assertTrue(prompt.contains(MessageSource.getMessage("report.value.unknown_class")));
    assertTrue(prompt.contains(MessageSource.getMessage("report.value.unknown_method")));
    assertEquals(
        "```",
        runContext.getMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, String.class).orElse(""));
  }

  @Test
  void testGenerateHumanReadableAnalysisSummary_excludesImplicitDefaultConstructorFromPrompt() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);
    ReportingService llmEnabledService = new ReportingService(reportingContext, llmClient);

    AnalysisResult analysisResult = new AnalysisResult(TEST_PROJECT_ID);
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CtorSample");
    classInfo.setLoc(20);

    MethodInfo implicitConstructor = new MethodInfo();
    implicitConstructor.setName("CtorSample");
    implicitConstructor.setSignature("CtorSample()");
    implicitConstructor.setParameterCount(0);
    implicitConstructor.setLoc(0);

    MethodInfo businessMethod = new MethodInfo();
    businessMethod.setName("run");
    businessMethod.setCyclomaticComplexity(8);
    businessMethod.setLoc(15);

    classInfo.setMethods(List.of(implicitConstructor, businessMethod));
    analysisResult.setClasses(List.of(classInfo));
    AnalysisResultContext.set(runContext, analysisResult);

    when(llmClient.generateTest(anyString(), eq(llmConfig))).thenReturn("summary");

    llmEnabledService.generateHumanReadableAnalysisSummary(new GenerationSummary());

    ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
    verify(llmClient).generateTest(promptCaptor.capture(), eq(llmConfig));
    String prompt = promptCaptor.getValue();
    assertTrue(prompt.contains("Analyzed methods: 1"));
    assertTrue(prompt.contains("methods=1"));
  }

  @Test
  void testPrintSummary_handlesAllStatusBranches() {
    GenerationTaskResult skipped = new GenerationTaskResult();
    skipped.setStatus("SKIPPED");
    GenerationTaskResult success = new GenerationTaskResult();
    success.setStatus("SUCCESS");
    GenerationTaskResult failure = new GenerationTaskResult();
    failure.setStatus("FAILURE");
    GenerationTaskResult processed = new GenerationTaskResult();
    processed.setStatus("PROCESSED");
    GenerationTaskResult other = new GenerationTaskResult();

    GenerationSummary summary = new GenerationSummary();
    summary.setProjectId(TEST_PROJECT_ID);
    summary.setRunId(TEST_RUN_ID);
    summary.setTotalTasks(5);
    List<GenerationTaskResult> details = new ArrayList<>();
    details.add(null);
    details.add(skipped);
    details.add(success);
    details.add(failure);
    details.add(processed);
    details.add(other);
    summary.setDetails(details);
    summary.setSucceeded(1);
    summary.setFailed(2);
    summary.setLineCoverage(0.75);
    summary.setBranchCoverage(0.5);
    summary.setDurationMs(2500L);

    assertDoesNotThrow(() -> service.printSummary(summary));
  }

  @Test
  void testPrintSummary_countsOnlyFailedForProcessedBlockCondition() {
    GenerationTaskResult skipped = new GenerationTaskResult();
    skipped.setStatus("SKIPPED");

    GenerationSummary summary = new GenerationSummary();
    summary.setProjectId(TEST_PROJECT_ID);
    summary.setRunId(TEST_RUN_ID);
    summary.setTotalTasks(1);
    summary.setDetails(List.of(skipped));
    summary.setSucceeded(0);
    summary.setFailed(1);

    assertDoesNotThrow(() -> service.printSummary(summary));
  }

  @Test
  void testPrintSummary_handlesSummaryWithoutOptionalSections() {
    GenerationSummary summary = new GenerationSummary();
    summary.setProjectId(TEST_PROJECT_ID);
    summary.setRunId(TEST_RUN_ID);
    summary.setTotalTasks(0);
    summary.setDetails(List.of());
    summary.setDurationMs(0);

    assertDoesNotThrow(() -> service.printSummary(summary));
  }

  @Test
  void testCalculateStatistics_privateMethodHandlesProcessedAndErrorCategories() throws Exception {
    GenerationSummary summary = new GenerationSummary();
    GenerationTaskResult success = new GenerationTaskResult();
    success.setStatus("SUCCESS");
    success.setErrorCategory("compile");
    GenerationTaskResult failure = new GenerationTaskResult();
    failure.setStatus("FAILURE");
    failure.setErrorCategory("runtime");
    GenerationTaskResult processed = new GenerationTaskResult();
    processed.setStatus("PROCESSED");
    processed.setErrorCategory("runtime");
    GenerationTaskResult skipped = new GenerationTaskResult();
    skipped.setStatus("SKIPPED");
    skipped.setErrorCategory("");

    invokeCalculateStatistics(summary, List.of(success, failure, processed, skipped));

    assertEquals(1, summary.getSucceeded());
    assertEquals(2, summary.getFailed());
    assertEquals(1.0 / 3.0, summary.getSuccessRate(), 1e-9);
    assertEquals(1, summary.getSkipped());
    assertEquals(Map.of("compile", 1, "runtime", 2), summary.getErrorCategoryCounts());
  }

  @Test
  void testConstructor_rejectsNullReportingContext() {
    assertThrows(NullPointerException.class, () -> new ReportingService((ReportingContext) null));
  }

  @Test
  void testConstructor_createsDefaultLlmClientWhenProviderConfigured() {
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("mock-model");
    config.setLlm(llmConfig);

    assertDoesNotThrow(() -> new ReportingService(reportingContext));
  }

  @Test
  void testHasLlmConfiguration_privateMethodHandlesNullConfig() throws Exception {
    var method = ReportingService.class.getDeclaredMethod("hasLlmConfiguration", Config.class);
    method.setAccessible(true);

    boolean resultForNull = (boolean) method.invoke(null, new Object[] {null});
    boolean resultForNoLlm = (boolean) method.invoke(null, new Config());

    assertFalse(resultForNull);
    assertFalse(resultForNoLlm);
  }

  private List<TaskRecord> createSampleTasks() {
    return List.of(
        createTaskRecord("task1", true),
        createTaskRecord("task2", true),
        createTaskRecord("task3", true));
  }

  private Optional<GenerationTaskResult> findResultByTaskId(
      List<GenerationTaskResult> results, String taskId) {
    return results.stream().filter(result -> taskId.equals(result.getTaskId())).findFirst();
  }

  private TaskRecord createTaskRecord(String taskId, boolean selected) {
    TaskRecord task = new TaskRecord();
    task.setTaskId(taskId);
    task.setClassFqn("com.example.Test" + taskId);
    task.setMethodName("test" + taskId);
    task.setSelected(selected);
    return task;
  }

  private AnalysisResult createAnalysisResultWithComplexMethod() {
    MethodInfo methodInfo = new MethodInfo();
    methodInfo.setName("execute");
    methodInfo.setCyclomaticComplexity(21);
    methodInfo.setLoc(55);

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.FooService");
    classInfo.setLoc(220);
    classInfo.setMethods(List.of(methodInfo));

    AnalysisResult analysisResult = new AnalysisResult(TEST_PROJECT_ID);
    analysisResult.setClasses(List.of(classInfo));
    return analysisResult;
  }

  private void invokeCalculateStatistics(
      GenerationSummary summary, List<GenerationTaskResult> details) throws Exception {
    var method =
        ReportingService.class.getDeclaredMethod(
            "calculateStatistics", GenerationSummary.class, List.class);
    method.setAccessible(true);
    method.invoke(service, summary, details);
  }
}
