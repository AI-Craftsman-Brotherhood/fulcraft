package com.craftsmanbro.fulcraft.plugins.reporting.flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.RunDirectories;
import com.craftsmanbro.fulcraft.plugins.reporting.adapter.ReasonAggregatorAdapter;
import com.craftsmanbro.fulcraft.plugins.reporting.adapter.RunSummaryAggregatorAdapter;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportMetadataKeys;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportingContext;
import com.craftsmanbro.fulcraft.plugins.reporting.core.model.TasksSnapshot;
import com.craftsmanbro.fulcraft.plugins.reporting.core.service.ReportingService;
import com.craftsmanbro.fulcraft.plugins.reporting.io.CoverageReportLoader;
import com.craftsmanbro.fulcraft.plugins.reporting.io.ReportHistoryStore;
import com.craftsmanbro.fulcraft.plugins.reporting.io.TasksSnapshotReader;
import com.craftsmanbro.fulcraft.plugins.reporting.io.contract.CoverageReader;
import com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.DynamicSelectionReport;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportHistory;
import com.craftsmanbro.fulcraft.plugins.reporting.model.RunSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ReportFlow}.
 *
 * <p>Tests verify that the service correctly:
 *
 * <ul>
 *   <li>Builds GenerationSummary from RunContext
 *   <li>Aggregates results from all pipeline phases
 *   <li>Constructs ReportData with correct data
 *   <li>Delegates to appropriate ReportWriterPort
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportFlowTest {

  private static final String TEST_PROJECT_ID = "test-project";
  private static final String TEST_RUN_ID = "test-run-123";

  @TempDir Path tempDir;

  @Mock private ReportWriterPort markdownWriter;
  @Mock private ReportWriterPort jsonWriter;
  @Mock private ReportingService reportingService;
  @Mock private TasksSnapshotReader tasksSnapshotReader;
  @Mock private CoverageReportLoader coverageReportLoader;
  @Mock private ReportHistoryStore reportHistoryStore;
  @Mock private RunSummaryAggregatorAdapter runSummaryAggregatorAdapter;
  @Mock private ReasonAggregatorAdapter reasonAggregatorAdapter;
  @Mock private CoverageReader coverageReader;

  private RunContext context;
  private Config config;
  private Map<ReportFormat, ReportWriterPort> writers;

  @BeforeEach
  void setUp() {
    config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setId(TEST_PROJECT_ID);

    context = new RunContext(tempDir, config, TEST_RUN_ID);

    when(markdownWriter.getFormat()).thenReturn(ReportFormat.MARKDOWN);
    when(jsonWriter.getFormat()).thenReturn(ReportFormat.JSON);

    writers = new EnumMap<>(ReportFormat.class);
    writers.put(ReportFormat.MARKDOWN, markdownWriter);
    writers.put(ReportFormat.JSON, jsonWriter);
  }

  @Test
  void testConstructor_rejectsNullWriters() {
    assertThrows(
        NullPointerException.class,
        () -> new ReportFlow((Map<ReportFormat, ReportWriterPort>) null, context, config));
  }

  @Test
  void testConstructor_rejectsNullContext() {
    assertThrows(NullPointerException.class, () -> new ReportFlow(writers, null, config));
  }

  @Test
  void testConstructor_rejectsNullConfig() {
    assertThrows(NullPointerException.class, () -> new ReportFlow(writers, context, null));
  }

  @Test
  void testSingleWriterConstructor_rejectsNullFormat() {
    ReportWriterPort writer = mock(ReportWriterPort.class);
    when(writer.getFormat()).thenReturn(null);

    assertThrows(NullPointerException.class, () -> new ReportFlow(writer, context, config));
  }

  @Test
  void testGenerateReports_skipsDryRun() throws ReportWriteException {
    // Arrange
    RunContext dryRunContext = new RunContext(tempDir, config, "dry-run").withDryRun(true);
    ReportFlow service = new ReportFlow(writers, dryRunContext, config);

    // Act
    service.generateReports();

    // Assert
    verifyNoInteractions(markdownWriter);
    verifyNoInteractions(jsonWriter);
  }

  @Test
  void testGenerateReports_delegatesToWriter() throws ReportWriteException {
    // Arrange
    context.putMetadata("tasks.selected", createSampleTasks());
    ReportFlow service = new ReportFlow(writers, context, config);

    // Act
    service.generateReports();

    // Assert
    verify(markdownWriter).writeReport(any(ReportData.class), eq(config));
  }

  @Test
  void testGenerateReports_passesCorrectReportData() throws ReportWriteException {
    // Arrange
    context.putMetadata("tasks.selected", createSampleTasks());
    ReportFlow service = new ReportFlow(writers, context, config);

    ArgumentCaptor<ReportData> dataCaptor = ArgumentCaptor.forClass(ReportData.class);

    // Act
    service.generateReports();

    // Assert
    verify(markdownWriter).writeReport(dataCaptor.capture(), eq(config));

    ReportData capturedData = dataCaptor.getValue();
    assertEquals(TEST_RUN_ID, capturedData.getRunId());
    assertEquals(TEST_PROJECT_ID, capturedData.getProjectId());
    assertNotNull(capturedData.getSummary());
  }

  @Test
  void testGenerateReports_storesSummaryInContext() throws ReportWriteException {
    // Arrange
    context.putMetadata("tasks.selected", createSampleTasks());
    ReportFlow service = new ReportFlow(writers, context, config);

    // Act
    service.generateReports();

    // Assert
    GenerationSummary storedSummary =
        context.getMetadata("generation.summary", GenerationSummary.class).orElse(null);
    assertNotNull(storedSummary);
    assertEquals(TEST_PROJECT_ID, storedSummary.getProjectId());
  }

  @Test
  void testGenerateReports_aggregatesSummariesAndPersistsHistory()
      throws ReportWriteException, java.io.IOException {
    // Arrange
    Path tasksOverride = tempDir.resolve("tasks.jsonl");
    context.putMetadata(ReportMetadataKeys.TASKS_FILE_OVERRIDE, tasksOverride);

    TasksSnapshot snapshot = TasksSnapshot.empty();
    GenerationSummary summary = new GenerationSummary();
    ReportHistory history = new ReportHistory();
    ReportHistory updatedHistory = new ReportHistory();
    CoverageSummary coverageSummary = new CoverageSummary();
    ReportData reportData = mock(ReportData.class);
    RunSummary runSummary = new RunSummary();
    ReasonSummary reasonSummary = new ReasonSummary();

    when(tasksSnapshotReader.load(context)).thenReturn(snapshot);
    when(reportingService.buildSummary(snapshot)).thenReturn(summary);
    Path historyRoot = RunDirectories.resolveRunsRoot(config, tempDir);
    when(reportHistoryStore.loadHistory(historyRoot)).thenReturn(history);
    when(reportingService.applyTrendAnalysis(summary, history)).thenReturn(updatedHistory);
    when(coverageReportLoader.loadCoverage(context, config, coverageReader))
        .thenReturn(coverageSummary);
    when(reportingService.buildReportData(summary)).thenReturn(reportData);
    when(runSummaryAggregatorAdapter.aggregate(
            eq(tasksOverride), eq(TEST_RUN_ID), eq(false), nullable(DynamicSelectionReport.class)))
        .thenReturn(runSummary);
    when(reasonAggregatorAdapter.aggregate(eq(tasksOverride), eq(TEST_RUN_ID), eq(false)))
        .thenReturn(reasonSummary);

    ReportFlow service = createFlowWithDependencies(context);

    // Act
    service.generateReports();

    // Assert
    verify(tasksSnapshotReader).load(context);
    verify(runSummaryAggregatorAdapter)
        .aggregate(
            eq(tasksOverride), eq(TEST_RUN_ID), eq(false), nullable(DynamicSelectionReport.class));
    verify(reasonAggregatorAdapter).aggregate(eq(tasksOverride), eq(TEST_RUN_ID), eq(false));
    verify(reportHistoryStore).loadHistory(historyRoot);
    verify(reportingService).applyTrendAnalysis(summary, history);
    verify(reportHistoryStore).saveHistory(historyRoot, updatedHistory);
    verify(coverageReportLoader).loadCoverage(context, config, coverageReader);
    verify(reportingService).applyCoverageIntegration(summary, coverageSummary);
    verify(reportingService).generateHumanReadableAnalysisSummary(summary);
    verify(reportingService).generateAllFormats(reportData, writers);

    assertSame(
        runSummary,
        context.getMetadata(ReportMetadataKeys.RUN_SUMMARY, RunSummary.class).orElse(null));
    assertSame(
        reasonSummary,
        context.getMetadata(ReportMetadataKeys.REASON_SUMMARY, ReasonSummary.class).orElse(null));
  }

  @Test
  void testGenerateReports_skipsAggregationWhenTasksFileMissing() throws ReportWriteException {
    // Arrange
    TasksSnapshot snapshot = TasksSnapshot.empty();
    GenerationSummary summary = new GenerationSummary();
    ReportData reportData = mock(ReportData.class);

    when(tasksSnapshotReader.load(context)).thenReturn(snapshot);
    when(reportingService.buildSummary(snapshot)).thenReturn(summary);
    when(reportingService.buildReportData(summary)).thenReturn(reportData);

    ReportFlow service = createFlowWithDependencies(context);

    // Act
    service.generateReports();

    // Assert
    verifyNoInteractions(runSummaryAggregatorAdapter);
    verifyNoInteractions(reasonAggregatorAdapter);
  }

  @Test
  void testGenerateReports_continuesWhenRunSummaryAggregationFails()
      throws ReportWriteException, IOException {
    // Arrange
    Path tasksOverride = tempDir.resolve("tasks.jsonl");
    context.putMetadata(ReportMetadataKeys.TASKS_FILE_OVERRIDE, tasksOverride);

    TasksSnapshot snapshot = TasksSnapshot.empty();
    GenerationSummary summary = new GenerationSummary();
    ReportHistory history = new ReportHistory();
    ReportHistory updatedHistory = new ReportHistory();
    CoverageSummary coverageSummary = new CoverageSummary();
    ReportData reportData = mock(ReportData.class);
    ReasonSummary reasonSummary = new ReasonSummary();

    when(tasksSnapshotReader.load(context)).thenReturn(snapshot);
    when(reportingService.buildSummary(snapshot)).thenReturn(summary);
    Path historyRoot = RunDirectories.resolveRunsRoot(config, tempDir);
    when(reportHistoryStore.loadHistory(historyRoot)).thenReturn(history);
    when(reportingService.applyTrendAnalysis(summary, history)).thenReturn(updatedHistory);
    when(coverageReportLoader.loadCoverage(context, config, coverageReader))
        .thenReturn(coverageSummary);
    when(reportingService.buildReportData(summary)).thenReturn(reportData);
    when(runSummaryAggregatorAdapter.aggregate(
            eq(tasksOverride), eq(TEST_RUN_ID), eq(false), nullable(DynamicSelectionReport.class)))
        .thenThrow(new IOException("run summary failed"));
    when(reasonAggregatorAdapter.aggregate(eq(tasksOverride), eq(TEST_RUN_ID), eq(false)))
        .thenReturn(reasonSummary);

    ReportFlow service = createFlowWithDependencies(context);

    // Act
    service.generateReports();

    // Assert
    assertTrue(context.getMetadata(ReportMetadataKeys.RUN_SUMMARY, RunSummary.class).isEmpty());
    assertSame(
        reasonSummary,
        context.getMetadata(ReportMetadataKeys.REASON_SUMMARY, ReasonSummary.class).orElse(null));
    verify(reasonAggregatorAdapter).aggregate(eq(tasksOverride), eq(TEST_RUN_ID), eq(false));
    verify(reportingService).generateAllFormats(reportData, writers);
  }

  @Test
  void testGenerateReports_continuesWhenReasonSummaryAggregationFails()
      throws ReportWriteException, IOException {
    // Arrange
    Path tasksOverride = tempDir.resolve("tasks.jsonl");
    context.putMetadata(ReportMetadataKeys.TASKS_FILE_OVERRIDE, tasksOverride);

    TasksSnapshot snapshot = TasksSnapshot.empty();
    GenerationSummary summary = new GenerationSummary();
    ReportHistory history = new ReportHistory();
    ReportHistory updatedHistory = new ReportHistory();
    CoverageSummary coverageSummary = new CoverageSummary();
    ReportData reportData = mock(ReportData.class);
    RunSummary runSummary = new RunSummary();

    when(tasksSnapshotReader.load(context)).thenReturn(snapshot);
    when(reportingService.buildSummary(snapshot)).thenReturn(summary);
    Path historyRoot = RunDirectories.resolveRunsRoot(config, tempDir);
    when(reportHistoryStore.loadHistory(historyRoot)).thenReturn(history);
    when(reportingService.applyTrendAnalysis(summary, history)).thenReturn(updatedHistory);
    when(coverageReportLoader.loadCoverage(context, config, coverageReader))
        .thenReturn(coverageSummary);
    when(reportingService.buildReportData(summary)).thenReturn(reportData);
    when(runSummaryAggregatorAdapter.aggregate(
            eq(tasksOverride), eq(TEST_RUN_ID), eq(false), nullable(DynamicSelectionReport.class)))
        .thenReturn(runSummary);
    when(reasonAggregatorAdapter.aggregate(eq(tasksOverride), eq(TEST_RUN_ID), eq(false)))
        .thenThrow(new IOException("reason summary failed"));

    ReportFlow service = createFlowWithDependencies(context);

    // Act
    service.generateReports();

    // Assert
    assertSame(
        runSummary,
        context.getMetadata(ReportMetadataKeys.RUN_SUMMARY, RunSummary.class).orElse(null));
    assertTrue(
        context.getMetadata(ReportMetadataKeys.REASON_SUMMARY, ReasonSummary.class).isEmpty());
    verify(runSummaryAggregatorAdapter)
        .aggregate(
            eq(tasksOverride), eq(TEST_RUN_ID), eq(false), nullable(DynamicSelectionReport.class));
    verify(reportingService).generateAllFormats(reportData, writers);
  }

  @Test
  void testGenerateReports_usesTasksFileFromPlanDirectoryWhenNoOverride()
      throws ReportWriteException, IOException {
    // Arrange
    Path planDir =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId()).planDir();
    Files.createDirectories(planDir);
    Path tasksFile = planDir.resolve("tasks.jsonl");
    Files.writeString(tasksFile, "");

    TasksSnapshot snapshot = TasksSnapshot.empty();
    GenerationSummary summary = new GenerationSummary();
    ReportHistory history = new ReportHistory();
    ReportHistory updatedHistory = new ReportHistory();
    CoverageSummary coverageSummary = new CoverageSummary();
    ReportData reportData = mock(ReportData.class);
    RunSummary runSummary = new RunSummary();
    ReasonSummary reasonSummary = new ReasonSummary();

    when(tasksSnapshotReader.load(context)).thenReturn(snapshot);
    when(reportingService.buildSummary(snapshot)).thenReturn(summary);
    Path historyRoot = RunDirectories.resolveRunsRoot(config, tempDir);
    when(reportHistoryStore.loadHistory(historyRoot)).thenReturn(history);
    when(reportingService.applyTrendAnalysis(summary, history)).thenReturn(updatedHistory);
    when(coverageReportLoader.loadCoverage(context, config, coverageReader))
        .thenReturn(coverageSummary);
    when(reportingService.buildReportData(summary)).thenReturn(reportData);
    when(runSummaryAggregatorAdapter.aggregate(
            eq(tasksFile), eq(TEST_RUN_ID), eq(false), nullable(DynamicSelectionReport.class)))
        .thenReturn(runSummary);
    when(reasonAggregatorAdapter.aggregate(eq(tasksFile), eq(TEST_RUN_ID), eq(false)))
        .thenReturn(reasonSummary);

    ReportFlow service = createFlowWithDependencies(context);

    // Act
    service.generateReports();

    // Assert
    verify(runSummaryAggregatorAdapter)
        .aggregate(
            eq(tasksFile), eq(TEST_RUN_ID), eq(false), nullable(DynamicSelectionReport.class));
    verify(reasonAggregatorAdapter).aggregate(eq(tasksFile), eq(TEST_RUN_ID), eq(false));
  }

  @Test
  void testGenerateReports_passesDynamicSelectionReportToRunSummaryAggregator()
      throws ReportWriteException, IOException {
    // Arrange
    Path tasksOverride = tempDir.resolve("tasks.jsonl");
    context.putMetadata(ReportMetadataKeys.TASKS_FILE_OVERRIDE, tasksOverride);
    context.putMetadata(
        ReportMetadataKeys.DYNAMIC_SELECTION_REPORT,
        Map.of("totalMethods", 7, "dynamicMethods", 4, "excludedCount", 2));

    TasksSnapshot snapshot = TasksSnapshot.empty();
    GenerationSummary summary = new GenerationSummary();
    ReportHistory history = new ReportHistory();
    ReportHistory updatedHistory = new ReportHistory();
    CoverageSummary coverageSummary = new CoverageSummary();
    ReportData reportData = mock(ReportData.class);
    RunSummary runSummary = new RunSummary();
    ReasonSummary reasonSummary = new ReasonSummary();

    when(tasksSnapshotReader.load(context)).thenReturn(snapshot);
    when(reportingService.buildSummary(snapshot)).thenReturn(summary);
    Path historyRoot = RunDirectories.resolveRunsRoot(config, tempDir);
    when(reportHistoryStore.loadHistory(historyRoot)).thenReturn(history);
    when(reportingService.applyTrendAnalysis(summary, history)).thenReturn(updatedHistory);
    when(coverageReportLoader.loadCoverage(context, config, coverageReader))
        .thenReturn(coverageSummary);
    when(reportingService.buildReportData(summary)).thenReturn(reportData);
    when(runSummaryAggregatorAdapter.aggregate(
            eq(tasksOverride), eq(TEST_RUN_ID), eq(false), nullable(DynamicSelectionReport.class)))
        .thenReturn(runSummary);
    when(reasonAggregatorAdapter.aggregate(eq(tasksOverride), eq(TEST_RUN_ID), eq(false)))
        .thenReturn(reasonSummary);

    ReportFlow service = createFlowWithDependencies(context);
    ArgumentCaptor<DynamicSelectionReport> dynamicCaptor =
        ArgumentCaptor.forClass(DynamicSelectionReport.class);

    // Act
    service.generateReports();

    // Assert
    verify(runSummaryAggregatorAdapter)
        .aggregate(eq(tasksOverride), eq(TEST_RUN_ID), eq(false), dynamicCaptor.capture());
    DynamicSelectionReport captured = dynamicCaptor.getValue();
    assertNotNull(captured);
    assertEquals(7, captured.getTotalMethods());
    assertEquals(4, captured.getDynamicMethods());
    assertEquals(2, captured.getExcludedCount());
  }

  @Test
  void testGenerateReports_printsSummaryWhenShowSummaryEnabled() throws ReportWriteException {
    // Arrange
    RunContext showSummaryContext =
        new RunContext(tempDir, config, TEST_RUN_ID).withShowSummary(true);
    TasksSnapshot snapshot = TasksSnapshot.empty();
    GenerationSummary summary = new GenerationSummary();
    ReportData reportData = mock(ReportData.class);

    when(tasksSnapshotReader.load(showSummaryContext)).thenReturn(snapshot);
    when(reportingService.buildSummary(snapshot)).thenReturn(summary);
    when(reportingService.buildReportData(summary)).thenReturn(reportData);

    ReportFlow service = createFlowWithDependencies(showSummaryContext);

    // Act
    service.generateReports();

    // Assert
    verify(reportingService).printSummary(summary);
  }

  @Test
  void testGetOutputDirectory_returnsDefaultPath() {
    // Arrange
    ReportFlow service = new ReportFlow(writers, context, config);

    // Act
    Path outputDir = service.getOutputDirectory();

    // Assert
    assertEquals(
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
            .reportDir(),
        outputDir);
  }

  @Test
  void testGetOutputDirectory_usesCustomDirectory() {
    // Arrange
    Path customDir = tempDir.resolve("custom-reports");
    ReportFlow service = new ReportFlow(writers, context, config, customDir);

    // Act
    Path outputDir = service.getOutputDirectory();

    // Assert
    assertEquals(customDir, outputDir);
  }

  @Test
  void testGenerateReport_withSpecificFormat() throws ReportWriteException {
    // Arrange
    context.putMetadata("tasks.selected", createSampleTasks());
    ReportFlow service = new ReportFlow(writers, context, config);

    // Act
    service.generateReport(ReportFormat.JSON);

    // Assert
    verify(jsonWriter).writeReport(any(ReportData.class), eq(config));
    verifyNoInteractions(markdownWriter);
  }

  @Test
  void testGenerateReport_throwsForUnsupportedFormat() {
    // Arrange
    ReportFlow service = new ReportFlow(writers, context, config);

    // Act & Assert
    assertThrows(
        ReportWriteException.class,
        () -> service.generateReport(ReportFormat.HTML)); // HTML not in writers map
  }

  @Test
  void testSingleWriterConstructor() throws ReportWriteException {
    // Arrange
    ReportFlow service = new ReportFlow(markdownWriter, context, config);
    context.putMetadata("tasks.selected", createSampleTasks());

    // Act
    service.generateReports();

    // Assert
    verify(markdownWriter).writeReport(any(ReportData.class), eq(config));
  }

  // === Helper Methods ===

  private List<TaskRecord> createSampleTasks() {
    return List.of(
        createTaskRecord("task1", true),
        createTaskRecord("task2", true),
        createTaskRecord("task3", true));
  }

  private TaskRecord createTaskRecord(String taskId, boolean selected) {
    TaskRecord task = new TaskRecord();
    task.setTaskId(taskId);
    task.setClassFqn("com.example.Test" + taskId);
    task.setMethodName("test" + taskId);
    task.setSelected(selected);
    return task;
  }

  private ReportFlow createFlowWithDependencies(RunContext runContext) {
    ReportingContext reportingContext = new ReportingContext(runContext, config, coverageReader);
    return new ReportFlow(
        writers,
        reportingContext,
        new ReportFlow.Dependencies(
            reportingService,
            tasksSnapshotReader,
            coverageReportLoader,
            reportHistoryStore,
            runSummaryAggregatorAdapter,
            reasonAggregatorAdapter));
  }
}
