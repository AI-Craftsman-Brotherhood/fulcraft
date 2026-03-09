package com.craftsmanbro.fulcraft.plugins.analysis.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.index.ProjectSymbolIndex;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess.SourcePreprocessor;
import com.craftsmanbro.fulcraft.plugins.analysis.flow.AnalysisFlow;
import com.craftsmanbro.fulcraft.plugins.analysis.flow.AnalysisFlow.ValidationResult;
import com.craftsmanbro.fulcraft.plugins.analysis.flow.AnalysisReportFlow;
import com.craftsmanbro.fulcraft.plugins.analysis.flow.BrittlenessDetectionFlow;
import com.craftsmanbro.fulcraft.plugins.analysis.flow.SourcePreprocessingFlow;
import com.craftsmanbro.fulcraft.plugins.analysis.io.AnalysisResultWriter;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

/** Unit tests for AnalyzeStage orchestration. */
// Mockito verify() on methods returning values
class AnalyzeStageTest {

  @TempDir Path tempDir;

  private AnalysisPort analysisPort;
  private AnalyzeStage stage;
  private Config config;

  @BeforeEach
  void setUp() throws IOException {
    analysisPort = mock(AnalysisPort.class);
    stage = new AnalyzeStage(analysisPort);
    config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setId("test-project");

    Files.createDirectories(tempDir.resolve("src/main/java"));
  }

  @Test
  @DisplayName("Should throw NullPointerException when analysis port is null")
  void constructor_shouldThrowNullPointerException_whenAnalysisPortIsNull() {
    assertThatThrownBy(() -> new AnalyzeStage((AnalysisPort) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("AnalysisPort cannot be null");
  }

  @Nested
  @DisplayName("Basic Execution Tests")
  class BasicExecutionTests {

    @Test
    @DisplayName("Should complete successfully when analysis returns classes")
    void execute_shouldCompleteSuccessfully_whenAnalysisReturnsClasses() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-1");
      AnalysisResult analysisResult = createAnalysisResult();

      when(analysisPort.analyze(any(Path.class), any(Config.class))).thenReturn(analysisResult);

      stage.execute(context);

      assertThat(AnalysisResultContext.get(context)).isPresent();
      assertThat(AnalysisResultContext.get(context).get().getClasses()).isNotEmpty();
      assertThat(stage.getNodeId()).isEqualTo(PipelineNodeIds.ANALYZE);
      assertThat(stage.getName()).isEqualTo("Analyze");

      verify(analysisPort).analyze(any(Path.class), any(Config.class));
    }

    @Test
    @DisplayName("Should skip execution when dry run is enabled")
    void execute_shouldSkipExecution_whenDryRunEnabled() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-2").withDryRun(true);

      stage.execute(context);

      assertThat(AnalysisResultContext.get(context)).isEmpty();
      verify(analysisPort, never()).analyze(any(Path.class), any(Config.class));
    }

    @Test
    @DisplayName("Should throw StageException when analysis port throws IOException")
    void execute_shouldThrowStageException_whenAnalysisPortThrowsIOException() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-3");

      when(analysisPort.analyze(any(Path.class), any(Config.class)))
          .thenThrow(new IOException("Analysis engine failure"));

      assertThatThrownBy(() -> stage.execute(context))
          .isInstanceOf(StageException.class)
          .hasMessageContaining(MessageSource.getMessage("analysis.stage.error.failed", tempDir))
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should throw NullPointerException when run context is null")
    void execute_shouldThrowNullPointerException_whenRunContextIsNull() {
      assertThatThrownBy(() -> stage.execute(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("RunContext cannot be null");
    }
  }

  @Nested
  @DisplayName("Orchestration Order Tests")
  class OrchestrationOrderTests {

    private AnalysisFlow analysisFlow;
    private SourcePreprocessingFlow sourcePreprocessingFlow;
    private BrittlenessDetectionFlow brittlenessDetectionFlow;
    private AnalysisResultWriter resultSaver;
    private AnalysisReportFlow analysisReportFlow;
    private AnalyzeStage orchestratedStage;

    @BeforeEach
    void setUp() {
      analysisFlow = mock(AnalysisFlow.class);
      sourcePreprocessingFlow = mock(SourcePreprocessingFlow.class);
      brittlenessDetectionFlow = mock(BrittlenessDetectionFlow.class);
      resultSaver = mock(AnalysisResultWriter.class);
      analysisReportFlow = mock(AnalysisReportFlow.class);

      orchestratedStage =
          new AnalyzeStage(
              analysisFlow,
              sourcePreprocessingFlow,
              brittlenessDetectionFlow,
              resultSaver,
              analysisReportFlow);
    }

    @Test
    @DisplayName("Should call services in correct order")
    void execute_shouldCallServicesInCorrectOrder() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-order");
      Path analysisDir =
          RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
              .analysisDir();
      AnalysisResult analysisResult = createAnalysisResult();
      SourcePreprocessor.Result preprocessResult = createSkippedResult();

      // Setup mocks
      when(sourcePreprocessingFlow.preprocess(any(), any(), any())).thenReturn(preprocessResult);
      when(sourcePreprocessingFlow.isStrictModeFailure(any())).thenReturn(false);
      when(analysisFlow.analyze(any(), any())).thenReturn(analysisResult);
      when(analysisFlow.validate(any())).thenReturn(new ValidationResult(true, null));
      when(brittlenessDetectionFlow.detectBrittleness(any())).thenReturn(false);
      when(sourcePreprocessingFlow.buildProjectSymbolIndex(any(), any(), any()))
          .thenReturn(mock(ProjectSymbolIndex.class));
      when(sourcePreprocessingFlow.loadExternalConfigValues(any(), any())).thenReturn(Map.of());

      orchestratedStage.execute(context);

      // Verify order
      InOrder inOrder =
          inOrder(
              sourcePreprocessingFlow,
              analysisFlow,
              brittlenessDetectionFlow,
              resultSaver,
              analysisReportFlow);

      // Step 1: Preprocess
      inOrder.verify(sourcePreprocessingFlow).preprocess(tempDir, config, analysisDir);
      boolean strictModeFailure =
          inOrder.verify(sourcePreprocessingFlow).isStrictModeFailure(preprocessResult);
      assertThat(strictModeFailure).isFalse();

      // Step 2: Static analysis
      inOrder.verify(analysisFlow).analyze(tempDir, config);

      // Step 3: Validate
      inOrder.verify(analysisFlow).validate(analysisResult);

      // Step 4: Brittleness detection
      inOrder.verify(brittlenessDetectionFlow).detectBrittleness(analysisResult);

      // Step 5: Log stats
      inOrder.verify(analysisFlow).logStats(analysisResult);

      // Step 6: Save results
      inOrder
          .verify(resultSaver)
          .saveAnalysisResult(analysisResult, analysisDir, tempDir, "test-project");
      inOrder.verify(resultSaver).saveDynamicFeatures(analysisResult, analysisDir, tempDir);
      inOrder
          .verify(resultSaver)
          .saveDynamicResolutions(
              eq(analysisResult),
              eq(analysisDir),
              eq(tempDir),
              eq(config),
              any(ProjectSymbolIndex.class),
              eq(Map.of()));
      inOrder.verify(resultSaver).saveAnalysisShards(analysisResult, analysisDir, "test-project");
      inOrder
          .verify(resultSaver)
          .savePreprocessResult(preprocessResult, analysisDir, tempDir, config);

      // Step 7: Generate report
      inOrder.verify(analysisReportFlow).generateQualityReport(analysisDir);
    }

    @Test
    @DisplayName("Should throw StageException when preprocessing fails in strict mode")
    void execute_shouldThrowStageException_whenPreprocessingFailsInStrictMode() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-strict-fail");
      SourcePreprocessor.Result failedResult = createFailedResult("Lombok not found");

      when(sourcePreprocessingFlow.preprocess(any(), any(), any())).thenReturn(failedResult);
      when(sourcePreprocessingFlow.isStrictModeFailure(failedResult)).thenReturn(true);
      when(sourcePreprocessingFlow.getFailureReason(failedResult)).thenReturn("Lombok not found");

      assertThatThrownBy(() -> orchestratedStage.execute(context))
          .isInstanceOf(StageException.class)
          .hasMessageContaining(
              MessageSource.getMessage(
                  "analysis.stage.error.preprocess_strict", "Lombok not found"));

      // Verify analysis was not called
      verify(analysisFlow, never()).analyze(any(), any());
    }

    @Test
    @DisplayName("Should throw StageException when validation fails")
    void execute_shouldThrowStageException_whenValidationFails() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-validation-fail");
      SourcePreprocessor.Result preprocessResult = createSkippedResult();

      when(sourcePreprocessingFlow.preprocess(any(), any(), any())).thenReturn(preprocessResult);
      when(sourcePreprocessingFlow.isStrictModeFailure(any())).thenReturn(false);
      when(analysisFlow.analyze(any(), any())).thenReturn(null);
      when(analysisFlow.validate(null))
          .thenReturn(new ValidationResult(false, "Analysis returned null result"));

      assertThatThrownBy(() -> orchestratedStage.execute(context))
          .isInstanceOf(StageException.class)
          .hasMessageContaining("null result");
    }

    @Test
    @DisplayName("Should add warning to context when validation has warning")
    void execute_shouldAddWarning_whenValidationHasWarning() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-warning");
      AnalysisResult analysisResult = createAnalysisResult();
      SourcePreprocessor.Result preprocessResult = createSkippedResult();

      when(sourcePreprocessingFlow.preprocess(any(), any(), any())).thenReturn(preprocessResult);
      when(sourcePreprocessingFlow.isStrictModeFailure(any())).thenReturn(false);
      when(analysisFlow.analyze(any(), any())).thenReturn(analysisResult);
      when(analysisFlow.validate(any())).thenReturn(new ValidationResult(true, "No classes found"));
      when(brittlenessDetectionFlow.detectBrittleness(any())).thenReturn(false);
      when(sourcePreprocessingFlow.buildProjectSymbolIndex(any(), any(), any()))
          .thenReturn(mock(ProjectSymbolIndex.class));
      when(sourcePreprocessingFlow.loadExternalConfigValues(any(), any())).thenReturn(Map.of());

      orchestratedStage.execute(context);

      assertThat(context.getWarnings()).contains("No classes found");
    }

    @Test
    @DisplayName("Should set brittleness detected flag in context")
    void execute_shouldSetBrittlenessFlag_whenBrittlenessDetected() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-brittle");
      AnalysisResult analysisResult = createAnalysisResult();
      SourcePreprocessor.Result preprocessResult = createSkippedResult();

      when(sourcePreprocessingFlow.preprocess(any(), any(), any())).thenReturn(preprocessResult);
      when(sourcePreprocessingFlow.isStrictModeFailure(any())).thenReturn(false);
      when(analysisFlow.analyze(any(), any())).thenReturn(analysisResult);
      when(analysisFlow.validate(any())).thenReturn(new ValidationResult(true, null));
      when(brittlenessDetectionFlow.detectBrittleness(any())).thenReturn(true);
      when(sourcePreprocessingFlow.buildProjectSymbolIndex(any(), any(), any()))
          .thenReturn(mock(ProjectSymbolIndex.class));
      when(sourcePreprocessingFlow.loadExternalConfigValues(any(), any())).thenReturn(Map.of());

      orchestratedStage.execute(context);

      assertThat(context.isBrittlenessDetected()).isTrue();
    }

    @Test
    @DisplayName("Should log brittleness details when brittleness is detected")
    void execute_shouldLogBrittlenessDetails_whenBrittlenessDetected() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-brittle-log");
      AnalysisResult analysisResult = createAnalysisResult();
      SourcePreprocessor.Result preprocessResult = createSkippedResult();

      when(sourcePreprocessingFlow.preprocess(any(), any(), any())).thenReturn(preprocessResult);
      when(sourcePreprocessingFlow.isStrictModeFailure(any())).thenReturn(false);
      when(analysisFlow.analyze(any(), any())).thenReturn(analysisResult);
      when(analysisFlow.validate(any())).thenReturn(new ValidationResult(true, null));
      when(brittlenessDetectionFlow.detectBrittleness(any())).thenReturn(true);
      when(sourcePreprocessingFlow.buildProjectSymbolIndex(any(), any(), any()))
          .thenReturn(mock(ProjectSymbolIndex.class));
      when(sourcePreprocessingFlow.loadExternalConfigValues(any(), any())).thenReturn(Map.of());

      orchestratedStage.execute(context);

      verify(brittlenessDetectionFlow).logDetectionResults(analysisResult);
    }

    @Test
    @DisplayName("Should save analyzed file list when dump file list is enabled")
    void execute_shouldSaveAnalyzedFileList_whenDumpFileListEnabled() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-dump-file-list");
      Path analysisDir =
          RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId())
              .analysisDir();
      AnalysisResult analysisResult = createAnalysisResult();
      SourcePreprocessor.Result preprocessResult = createSkippedResult();
      Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
      analysisConfig.setDumpFileList(true);
      config.setAnalysis(analysisConfig);

      when(sourcePreprocessingFlow.preprocess(any(), any(), any())).thenReturn(preprocessResult);
      when(sourcePreprocessingFlow.isStrictModeFailure(any())).thenReturn(false);
      when(analysisFlow.analyze(any(), any())).thenReturn(analysisResult);
      when(analysisFlow.validate(any())).thenReturn(new ValidationResult(true, null));
      when(brittlenessDetectionFlow.detectBrittleness(any())).thenReturn(false);
      when(sourcePreprocessingFlow.buildProjectSymbolIndex(any(), any(), any()))
          .thenReturn(mock(ProjectSymbolIndex.class));
      when(sourcePreprocessingFlow.loadExternalConfigValues(any(), any())).thenReturn(Map.of());

      orchestratedStage.execute(context);

      verify(resultSaver).saveAnalyzedFileList(analysisResult, analysisDir, tempDir);
    }

    private SourcePreprocessor.Result createSkippedResult() {
      return new SourcePreprocessor.Result(
          SourcePreprocessor.Status.SKIPPED, List.of(tempDir), List.of(tempDir), null, null, 0);
    }

    private SourcePreprocessor.Result createFailedResult(String reason) {
      return new SourcePreprocessor.Result(
          SourcePreprocessor.Status.FAILED, List.of(tempDir), List.of(tempDir), "TEST", reason, 0);
    }

    private SourcePreprocessor.Result createSuccessResult(Path preprocessedDir) {
      return new SourcePreprocessor.Result(
          SourcePreprocessor.Status.SUCCESS,
          List.of(tempDir),
          List.of(preprocessedDir),
          "DELOMBOK",
          null,
          100);
    }

    @Test
    @DisplayName("Should use preprocessed source roots when preprocessing succeeds")
    void execute_shouldUsePreprocessedRoots_whenPreprocessingSucceeds() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-preprocess-success");
      AnalysisResult analysisResult = createAnalysisResult();
      Path preprocessedDir = tempDir.resolve("preprocessed");
      Files.createDirectories(preprocessedDir);
      SourcePreprocessor.Result successResult =
          new SourcePreprocessor.Result(
              SourcePreprocessor.Status.SUCCESS,
              List.of(tempDir),
              List.of(preprocessedDir),
              "DELOMBOK",
              null,
              100);

      // Setup config with analysis config
      Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
      analysisConfig.setSourceRootPaths(List.of(tempDir.toString()));
      config.setAnalysis(analysisConfig);

      when(sourcePreprocessingFlow.preprocess(any(), any(), any())).thenReturn(successResult);
      when(sourcePreprocessingFlow.isStrictModeFailure(any())).thenReturn(false);
      when(analysisFlow.analyze(any(), any()))
          .thenAnswer(
              invocation -> {
                Config capturedConfig = invocation.getArgument(1);
                // Verify config inside analyze has preprocessed roots
                assertThat(capturedConfig.getAnalysis().getSourceRootPaths())
                    .containsExactly(preprocessedDir.toString());
                // Verify source root mode is STRICT
                assertThat(capturedConfig.getAnalysis().getSourceRootMode()).isEqualTo("STRICT");
                return analysisResult;
              });
      when(analysisFlow.validate(any())).thenReturn(new ValidationResult(true, null));
      when(brittlenessDetectionFlow.detectBrittleness(any())).thenReturn(false);
      when(sourcePreprocessingFlow.buildProjectSymbolIndex(any(), any(), any()))
          .thenReturn(mock(ProjectSymbolIndex.class));
      when(sourcePreprocessingFlow.loadExternalConfigValues(any(), any())).thenReturn(Map.of());

      orchestratedStage.execute(context);

      // Verify analysis was called
      verify(analysisFlow).analyze(eq(tempDir), any(Config.class));

      // Verify config was restored
      assertThat(config.getAnalysis().getSourceRootPaths()).containsExactly(tempDir.toString());
      assertThat(config.getAnalysis().getSourceRootMode()).isNotEqualTo("STRICT");
    }

    @Test
    @DisplayName("Should keep strict source root mode when already strict")
    void execute_shouldKeepStrictSourceRootMode_whenAlreadyStrict() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-preprocess-strict-already");
      AnalysisResult analysisResult = createAnalysisResult();
      Path preprocessedDir = tempDir.resolve("preprocessed-strict");
      Files.createDirectories(preprocessedDir);
      SourcePreprocessor.Result successResult = createSuccessResult(preprocessedDir);

      Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
      analysisConfig.setSourceRootMode("strict");
      analysisConfig.setSourceRootPaths(List.of(tempDir.toString()));
      config.setAnalysis(analysisConfig);

      when(sourcePreprocessingFlow.preprocess(any(), any(), any())).thenReturn(successResult);
      when(sourcePreprocessingFlow.isStrictModeFailure(any())).thenReturn(false);
      when(analysisFlow.analyze(any(), any()))
          .thenAnswer(
              invocation -> {
                Config capturedConfig = invocation.getArgument(1);
                assertThat(capturedConfig.getAnalysis().getSourceRootPaths())
                    .containsExactly(preprocessedDir.toString());
                assertThat(capturedConfig.getAnalysis().getSourceRootMode()).isEqualTo("strict");
                return analysisResult;
              });
      when(analysisFlow.validate(any())).thenReturn(new ValidationResult(true, null));
      when(brittlenessDetectionFlow.detectBrittleness(any())).thenReturn(false);
      when(sourcePreprocessingFlow.buildProjectSymbolIndex(any(), any(), any()))
          .thenReturn(mock(ProjectSymbolIndex.class));
      when(sourcePreprocessingFlow.loadExternalConfigValues(any(), any())).thenReturn(Map.of());

      orchestratedStage.execute(context);

      assertThat(config.getAnalysis().getSourceRootMode()).isEqualTo("strict");
      assertThat(config.getAnalysis().getSourceRootPaths()).containsExactly(tempDir.toString());
    }

    @Test
    @DisplayName("Should analyze with original config when analysis config is missing")
    void execute_shouldAnalyzeWithOriginalConfig_whenAnalysisConfigMissing() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-preprocess-no-analysis-config");
      AnalysisResult analysisResult = createAnalysisResult();
      Path preprocessedDir = tempDir.resolve("preprocessed-no-analysis-config");
      Files.createDirectories(preprocessedDir);
      SourcePreprocessor.Result successResult = createSuccessResult(preprocessedDir);
      config.setAnalysis(null);

      when(sourcePreprocessingFlow.preprocess(any(), any(), any())).thenReturn(successResult);
      when(sourcePreprocessingFlow.isStrictModeFailure(any())).thenReturn(false);
      when(analysisFlow.analyze(any(), any()))
          .thenAnswer(
              invocation -> {
                Config capturedConfig = invocation.getArgument(1);
                assertThat(capturedConfig).isSameAs(config);
                return analysisResult;
              });
      when(analysisFlow.validate(any())).thenReturn(new ValidationResult(true, null));
      when(brittlenessDetectionFlow.detectBrittleness(any())).thenReturn(false);
      when(sourcePreprocessingFlow.buildProjectSymbolIndex(any(), any(), any()))
          .thenReturn(mock(ProjectSymbolIndex.class));
      when(sourcePreprocessingFlow.loadExternalConfigValues(any(), any())).thenReturn(Map.of());

      orchestratedStage.execute(context);

      verify(analysisFlow).analyze(tempDir, config);
    }

    @Test
    @DisplayName("Should analyze with original config when preprocess result is null")
    void execute_shouldAnalyzeWithOriginalConfig_whenPreprocessResultIsNull() throws Exception {
      RunContext context = new RunContext(tempDir, config, "run-preprocess-null-result");
      AnalysisResult analysisResult = createAnalysisResult();
      Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
      analysisConfig.setSourceRootMode("AUTO");
      analysisConfig.setSourceRootPaths(List.of(tempDir.toString()));
      config.setAnalysis(analysisConfig);

      when(sourcePreprocessingFlow.preprocess(any(), any(), any())).thenReturn(null);
      when(sourcePreprocessingFlow.isStrictModeFailure(null)).thenReturn(false);
      when(analysisFlow.analyze(any(), any()))
          .thenAnswer(
              invocation -> {
                Config capturedConfig = invocation.getArgument(1);
                assertThat(capturedConfig).isSameAs(config);
                return analysisResult;
              });
      when(analysisFlow.validate(any())).thenReturn(new ValidationResult(true, null));
      when(brittlenessDetectionFlow.detectBrittleness(any())).thenReturn(false);
      when(sourcePreprocessingFlow.buildProjectSymbolIndex(any(), any(), any()))
          .thenReturn(mock(ProjectSymbolIndex.class));
      when(sourcePreprocessingFlow.loadExternalConfigValues(any(), any())).thenReturn(Map.of());

      orchestratedStage.execute(context);

      verify(analysisFlow).analyze(tempDir, config);
    }
  }

  private AnalysisResult createAnalysisResult() {
    AnalysisResult result = new AnalysisResult("test-project");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.MyClass");
    classInfo.setFilePath("com/example/MyClass.java");
    result.setClasses(List.of(classInfo));
    return result;
  }
}
