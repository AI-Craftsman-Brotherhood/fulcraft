package com.craftsmanbro.fulcraft.plugins.reporting.stage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportMetadataKeys;
import com.craftsmanbro.fulcraft.plugins.reporting.flow.ReportFlow;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ReportStage}.
 *
 * <p>Tests verify that ReportStage correctly orchestrates report generation by delegating to {@link
 * ReportFlow} and handling metadata and error cases appropriately.
 */
@ExtendWith(MockitoExtension.class)
class ReportStageTest {

  private static final Path TEST_PROJECT_ROOT = Paths.get("/tmp/test-project");
  private static final Path TEST_OUTPUT_DIR = Paths.get("/tmp/test-project/build/reports");
  private static final String TEST_RUN_ID = "test-run-id";

  @Mock private ReportFlow reportFlow;
  @TempDir Path tempDir;

  private ReportStage stage;
  private RunContext context;

  @BeforeEach
  void setUp() {
    stage = new ReportStage(reportFlow);
    Config config = new Config();
    context = new RunContext(TEST_PROJECT_ROOT, config, TEST_RUN_ID);
  }

  @Test
  void testGetName_returnsReport() {
    assertEquals("Report", stage.getName());
  }

  @Test
  void testGetStep_returnsReportStep() {
    assertEquals(PipelineNodeIds.REPORT, stage.getNodeId());
  }

  @Test
  void testConstructor_rejectsNullService() {
    assertThrows(NullPointerException.class, () -> new ReportStage((ReportFlow) null));
  }

  @Test
  void testConstructor_rejectsNullConfig() {
    assertThrows(
        NullPointerException.class, () -> new ReportStage((Config) null, (ctx, cfg) -> reportFlow));
  }

  @Test
  void testConstructor_rejectsNullServiceFactory() {
    assertThrows(
        NullPointerException.class,
        () -> new ReportStage(new Config(), (BiFunction<RunContext, Config, ReportFlow>) null));
  }

  @Test
  void testGetReportFlow_returnsNullBeforeExecution_whenUsingConfigConstructor() {
    ReportStage lazyStage = new ReportStage(new Config());

    assertNull(lazyStage.getReportFlow());
  }

  @Test
  void testExecute_rejectsNullContext() {
    assertThrows(NullPointerException.class, () -> stage.execute(null));
  }

  @Test
  void testExecute_skipsWhenDryRun() throws StageException {
    // Arrange
    RunContext dryRunContext =
        new RunContext(TEST_PROJECT_ROOT, new Config(), "dry-run-id").withDryRun(true);

    // Act
    stage.execute(dryRunContext);

    // Assert
    verifyNoInteractions(reportFlow);
    assertFalse(
        dryRunContext
            .getMetadata(ReportStage.METADATA_REPORT_GENERATED, Boolean.class)
            .orElse(true),
        "Should mark reports as not generated in dry-run mode");
  }

  @Test
  void testExecute_delegatesToService() throws StageException, ReportWriteException {
    // Arrange
    when(reportFlow.getOutputDirectory()).thenReturn(TEST_OUTPUT_DIR);

    // Act
    stage.execute(context);

    // Assert
    verify(reportFlow).generateReports();
    verify(reportFlow).getOutputDirectory();
  }

  @Test
  void testExecute_storesMetadataOnSuccess() throws StageException {
    // Arrange
    when(reportFlow.getOutputDirectory()).thenReturn(TEST_OUTPUT_DIR);

    // Act
    stage.execute(context);

    // Assert
    assertTrue(
        context.getMetadata(ReportStage.METADATA_REPORT_GENERATED, Boolean.class).orElse(false),
        "Should mark reports as generated on success");
    assertEquals(
        TEST_OUTPUT_DIR.toString(),
        context.getMetadata(ReportStage.METADATA_REPORT_OUTPUT_DIR, String.class).orElse(null),
        "Should store output directory in metadata");
  }

  @Test
  void testExecute_wrapsServiceException() throws ReportWriteException {
    // Arrange
    ReportWriteException serviceException = new ReportWriteException("Test error");
    doThrow(serviceException).when(reportFlow).generateReports();

    // Act & Assert
    StageException thrown = assertThrows(StageException.class, () -> stage.execute(context));

    assertEquals(PipelineNodeIds.REPORT, thrown.getNodeId(), "Should have correct step");
    assertTrue(thrown.getMessage().contains("Test error"), "Should include original error message");
    assertEquals(serviceException, thrown.getCause(), "Should wrap original exception");

    // Verify metadata indicates failure
    assertFalse(
        context.getMetadata(ReportStage.METADATA_REPORT_GENERATED, Boolean.class).orElse(true),
        "Should mark reports as not generated on failure");
  }

  @Test
  void testExecute_wrapsUnexpectedException() throws ReportWriteException {
    // Arrange
    IllegalStateException unexpected = new IllegalStateException("boom");
    doThrow(unexpected).when(reportFlow).generateReports();

    // Act
    StageException thrown = assertThrows(StageException.class, () -> stage.execute(context));

    // Assert
    assertEquals(PipelineNodeIds.REPORT, thrown.getNodeId(), "Should have correct step");
    assertTrue(
        thrown.getMessage().contains("IllegalStateException"),
        "Should include unexpected exception type");
    assertSame(unexpected, thrown.getCause(), "Should preserve original cause");
    assertFalse(
        context.getMetadata(ReportStage.METADATA_REPORT_GENERATED, Boolean.class).orElse(true),
        "Should mark reports as not generated on failure");
  }

  @Test
  void testGetReportFlow_returnsInjectedService() {
    assertSame(reportFlow, stage.getReportFlow());
  }

  @Test
  void testExecute_withValidContext_processesSuccessfully()
      throws StageException, ReportWriteException {
    // Arrange
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setId("test-project");

    RunContext validContext = new RunContext(TEST_PROJECT_ROOT, config, "valid-run");
    when(reportFlow.getOutputDirectory()).thenReturn(TEST_OUTPUT_DIR);

    // Act
    stage.execute(validContext);

    // Assert - no exception means success
    verify(reportFlow).generateReports();
    assertTrue(
        validContext
            .getMetadata(ReportStage.METADATA_REPORT_GENERATED, Boolean.class)
            .orElse(false));
  }

  @Test
  void testExecute_withSummaryInService_storesSummaryInContext()
      throws StageException, ReportWriteException {
    // Arrange
    GenerationSummary testSummary = new GenerationSummary();
    testSummary.setProjectId("test-project");
    testSummary.setTotalTasks(10);

    when(reportFlow.getOutputDirectory()).thenReturn(TEST_OUTPUT_DIR);
    doAnswer(
            invocation -> {
              context.putMetadata("generation.summary", testSummary);
              return null;
            })
        .when(reportFlow)
        .generateReports();

    // Act
    stage.execute(context);

    // Assert
    assertTrue(
        context.getMetadata("generation.summary", GenerationSummary.class).isPresent(),
        "Summary should be stored in context");
    assertEquals(
        "test-project",
        context.getMetadata("generation.summary", GenerationSummary.class).get().getProjectId());
    assertEquals(
        10,
        context.getMetadata("generation.summary", GenerationSummary.class).get().getTotalTasks());
  }

  @Test
  void testExecute_usesFactoryToCreateService() throws StageException, ReportWriteException {
    // Arrange
    Config config = new Config();
    ReportFlow mockFlow = mock(ReportFlow.class);
    when(mockFlow.getOutputDirectory()).thenReturn(TEST_OUTPUT_DIR);

    ReportStage stageWithFactory =
        new ReportStage(
            config,
            (ctx, cfg) -> {
              assertSame(context, ctx, "Should pass correct context to factory");
              assertSame(config, cfg, "Should pass correct config to factory");
              return mockFlow;
            });

    // Act
    stageWithFactory.execute(context);

    // Assert
    verify(mockFlow).generateReports();
  }

  @Test
  void testExecute_withDefaultService_writesToRunReportDirectory() throws Exception {
    Config config = createReportConfig("markdown");
    RunContext localContext = new RunContext(tempDir, config, "default-service");
    localContext.setReportTaskResults(List.of(new ReportTaskResult()));
    ReportStage defaultStage = new ReportStage(config);

    defaultStage.execute(localContext);

    Path reportDir =
        RunPaths.from(
                localContext.getConfig(), localContext.getProjectRoot(), localContext.getRunId())
            .reportDir();
    assertTrue(Files.exists(reportDir.resolve("report.md")));
    assertEquals(
        reportDir.toString(),
        localContext
            .getMetadata(ReportStage.METADATA_REPORT_OUTPUT_DIR, String.class)
            .orElse(null));
    assertTrue(
        localContext
            .getMetadata(ReportStage.METADATA_REPORT_GENERATED, Boolean.class)
            .orElse(false));
    assertNotNull(defaultStage.getReportFlow());
  }

  @Test
  void testExecute_withOutputDirectoryOverride_writesReportIntoOverrideDirectory()
      throws Exception {
    Config config = createReportConfig("markdown");
    RunContext localContext = new RunContext(tempDir, config, "override-directory");
    localContext.setReportTaskResults(List.of(new ReportTaskResult()));
    Path overrideDir = tempDir.resolve("custom-report-dir");
    Files.createDirectories(overrideDir);
    localContext.putMetadata(ReportMetadataKeys.OUTPUT_PATH_OVERRIDE, overrideDir);
    ReportStage defaultStage = new ReportStage(config);

    defaultStage.execute(localContext);

    assertEquals(
        overrideDir.toString(),
        localContext
            .getMetadata(ReportStage.METADATA_REPORT_OUTPUT_DIR, String.class)
            .orElse(null));
    assertTrue(Files.exists(overrideDir.resolve("report.md")));
  }

  @Test
  void testExecute_withOutputFileOverride_usesProjectRootAndCustomFilename() throws Exception {
    Config config = createReportConfig("json");
    RunContext localContext = new RunContext(tempDir, config, "override-file");
    localContext.setReportTaskResults(List.of(new ReportTaskResult()));

    String customFilename = "report-stage-" + System.nanoTime() + ".json";
    localContext.putMetadata(ReportMetadataKeys.OUTPUT_PATH_OVERRIDE, Path.of(customFilename));
    ReportStage defaultStage = new ReportStage(config);

    defaultStage.execute(localContext);

    assertEquals(
        tempDir.toString(),
        localContext
            .getMetadata(ReportStage.METADATA_REPORT_OUTPUT_DIR, String.class)
            .orElse(null));
    assertTrue(Files.exists(tempDir.resolve(customFilename)));
  }

  @Test
  void testExecute_withHtmlReport_generatesSourceLevelReportsAndVisualReport() throws Exception {
    Config config = createReportConfig("html");
    RunContext localContext = new RunContext(tempDir, config, "html-source-reports");
    localContext.setReportTaskResults(List.of(new ReportTaskResult()));

    AnalysisResult analysisResult = new AnalysisResult("test-project");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.report.SampleService");
    classInfo.setFilePath("src/main/java/com/example/report/SampleService.java");
    analysisResult.setClasses(List.of(classInfo));
    AnalysisResultContext.set(localContext, analysisResult);

    ReportStage defaultStage = new ReportStage(config);
    defaultStage.execute(localContext);

    Path reportDir =
        RunPaths.from(
                localContext.getConfig(), localContext.getProjectRoot(), localContext.getRunId())
            .reportDir();
    assertTrue(Files.exists(reportDir.resolve("report.html")));
    assertTrue(Files.exists(reportDir.resolve("analysis_visual.html")));
    assertTrue(
        Files.exists(reportDir.resolve("src/main/java/com/example/report/SampleService.html")));
  }

  @Test
  void testExecute_withExistingFileOverride_usesExistingFileParentAndFilename() throws Exception {
    Config config = createReportConfig("markdown");
    RunContext localContext = new RunContext(tempDir, config, "override-existing-file");
    localContext.setReportTaskResults(List.of(new ReportTaskResult()));

    Path existingFile = tempDir.resolve("custom").resolve("existing-report.md");
    Files.createDirectories(existingFile.getParent());
    Files.writeString(existingFile, "previous");
    localContext.putMetadata(ReportMetadataKeys.OUTPUT_PATH_OVERRIDE, existingFile);
    ReportStage defaultStage = new ReportStage(config);

    defaultStage.execute(localContext);

    assertEquals(
        existingFile.getParent().toString(),
        localContext
            .getMetadata(ReportStage.METADATA_REPORT_OUTPUT_DIR, String.class)
            .orElse(null));
    assertTrue(Files.exists(existingFile));
  }

  @Test
  void testGetOrCreateService_usesContextConfigWhenInjectedConfigIsNull() throws Exception {
    RunContext localContext = new RunContext(tempDir, new Config(), "context-config-branch");

    setPrivateField(stage, "injectedService", null);
    setPrivateField(stage, "createdService", reportFlow);

    Method method = ReportStage.class.getDeclaredMethod("getOrCreateService", RunContext.class);
    method.setAccessible(true);
    Object resolved = method.invoke(stage, localContext);

    assertSame(reportFlow, resolved);
  }

  @Test
  void testExecute_withRootPathOverride_fallsBackToProjectRootWhenFileNameIsNull()
      throws Exception {
    Config config = createReportConfig("markdown");
    RunContext localContext = new RunContext(tempDir, config, "override-root-path");
    localContext.setReportTaskResults(List.of(new ReportTaskResult()));
    Path rootPath = Path.of("/");
    localContext.putMetadata(ReportMetadataKeys.OUTPUT_PATH_OVERRIDE, rootPath);
    ReportStage defaultStage = new ReportStage(config);

    try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
      files.when(() -> Files.exists(rootPath)).thenReturn(false);
      files.when(() -> Files.isDirectory(rootPath)).thenReturn(false);

      defaultStage.execute(localContext);
    }

    assertEquals(
        tempDir.toString(),
        localContext
            .getMetadata(ReportStage.METADATA_REPORT_OUTPUT_DIR, String.class)
            .orElse(null));
    assertTrue(Files.exists(tempDir.resolve("report.md")));
  }

  @Test
  void testExecute_loadsMissingResultsFromTasksFileOverride() throws Exception {
    // Arrange
    String runId = "load-from-override";
    RunContext localContext = new RunContext(tempDir, new Config(), runId);
    Path tasksFile = writeTasksFile(tempDir.resolve("overrides").resolve("tasks.jsonl"));
    localContext.putMetadata(ReportMetadataKeys.TASKS_FILE_OVERRIDE, tasksFile);
    when(reportFlow.getOutputDirectory()).thenReturn(tempDir.resolve("reports"));

    // Act
    stage.execute(localContext);

    // Assert
    verify(reportFlow).generateReports();
    assertEquals(
        1, localContext.getReportTaskResults().size(), "Should load one result from tasks file");
    ReportTaskResult loadedResult = localContext.getReportTaskResults().get(0);
    assertEquals("task-1", loadedResult.getTaskId(), "Should preserve task id");
    assertEquals(runId, loadedResult.getRunId(), "Should use current run id");
    assertEquals(
        "not_selected",
        loadedResult.getStatus(),
        "Selected=false tasks should map as not selected");
  }

  @Test
  void testExecute_loadsMissingResultsFromPlanDirectoryWhenOverrideMissing() throws Exception {
    // Arrange
    String runId = "load-from-plan";
    RunContext localContext = new RunContext(tempDir, new Config(), runId);
    Path planDir =
        RunPaths.from(
                localContext.getConfig(), localContext.getProjectRoot(), localContext.getRunId())
            .planDir();
    writeTasksFile(planDir.resolve("tasks.jsonl"));
    when(reportFlow.getOutputDirectory()).thenReturn(tempDir.resolve("reports"));

    // Act
    stage.execute(localContext);

    // Assert
    assertEquals(
        1,
        localContext.getReportTaskResults().size(),
        "Should discover and load tasks.jsonl under plan directory");
    assertEquals("task-1", localContext.getReportTaskResults().get(0).getTaskId());
  }

  @Test
  void testExecute_addsWarningWhenTasksFileCannotBeLoaded() throws Exception {
    // Arrange
    RunContext localContext = new RunContext(tempDir, new Config(), "load-failure");
    localContext.putMetadata(
        ReportMetadataKeys.TASKS_FILE_OVERRIDE, tempDir.resolve("missing").resolve("tasks.jsonl"));
    when(reportFlow.getOutputDirectory()).thenReturn(tempDir.resolve("reports"));

    // Act
    stage.execute(localContext);

    // Assert
    verify(reportFlow).generateReports();
    assertTrue(
        localContext.getReportTaskResults().isEmpty(), "Should keep results empty on load failure");
    assertTrue(localContext.hasWarnings(), "Should record warning when loading tasks fails");
  }

  @Test
  void testExecute_skipsTaskLoadingWhenResultsAlreadyPresent() throws Exception {
    // Arrange
    RunContext localContext = new RunContext(tempDir, new Config(), "existing-results");
    ReportTaskResult existing = new ReportTaskResult();
    existing.setTaskId("already-present");
    localContext.setReportTaskResults(List.of(existing));
    localContext.putMetadata(
        ReportMetadataKeys.TASKS_FILE_OVERRIDE, tempDir.resolve("missing").resolve("tasks.jsonl"));
    when(reportFlow.getOutputDirectory()).thenReturn(tempDir.resolve("reports"));

    // Act
    stage.execute(localContext);

    // Assert
    assertFalse(localContext.hasWarnings(), "Should not attempt loading tasks when results exist");
    assertEquals(1, localContext.getReportTaskResults().size());
    assertEquals("already-present", localContext.getReportTaskResults().get(0).getTaskId());
  }

  @Test
  void testExecute_reusesFactoryCreatedServiceAcrossExecutions() throws Exception {
    // Arrange
    Config config = new Config();
    AtomicInteger factoryCalls = new AtomicInteger();
    ReportFlow flowFromFactory = mock(ReportFlow.class);
    when(flowFromFactory.getOutputDirectory()).thenReturn(tempDir.resolve("reports"));
    ReportStage stageWithFactory =
        new ReportStage(
            config,
            (ctx, cfg) -> {
              factoryCalls.incrementAndGet();
              return flowFromFactory;
            });
    RunContext localContext = new RunContext(tempDir, config, "reuse-service");
    localContext.setReportTaskResults(List.of(new ReportTaskResult()));

    // Act
    stageWithFactory.execute(localContext);
    stageWithFactory.execute(localContext);

    // Assert
    assertEquals(1, factoryCalls.get(), "Factory should be called only once");
    verify(flowFromFactory, times(2)).generateReports();
  }

  private Path writeTasksFile(Path tasksFile) throws IOException {
    Files.createDirectories(tasksFile.getParent());
    String jsonl =
        "{\"task_id\":\"task-1\",\"project_id\":\"project-1\","
            + "\"class_fqn\":\"com.example.SampleService\",\"method_name\":\"run\","
            + "\"selected\":false}\n";
    Files.writeString(tasksFile, jsonl);
    return tasksFile;
  }

  private Config createReportConfig(String reportFormat) {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setId("test-project");
    config.setProject(projectConfig);

    Config.OutputConfig output = new Config.OutputConfig();
    output.getFormat().setReport(reportFormat);
    config.setOutput(output);
    return config;
  }

  private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
    var field = ReportStage.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
