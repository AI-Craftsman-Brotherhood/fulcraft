package com.craftsmanbro.fulcraft.ui.cli.command.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultMerger;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultVerifier;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.document.flow.DocumentFlow;
import com.craftsmanbro.fulcraft.plugins.document.stage.DocumentStage;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.contract.BuildToolPort;
import com.craftsmanbro.fulcraft.ui.cli.wiring.ServiceFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunAnalysisReportExecutorTest {

  @TempDir Path tempDir;

  @Test
  void constructor_rejectsNullServices() {
    assertThatThrownBy(() -> new RunAnalysisReportExecutor(null, "composite"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("services is required");
  }

  @Test
  void execute_inDryRun_doesNotInvokeAnalysisPort() throws Exception {
    TrackingServiceFactory services = new TrackingServiceFactory();
    RunAnalysisReportExecutor executor = new RunAnalysisReportExecutor(services, "composite");

    Config config = new Config();
    RunContext context = new RunContext(Path.of(".").toAbsolutePath(), config, "run-test");
    context.withDryRun(true);

    int exitCode = executor.execute(context);

    assertThat(exitCode).isZero();
    assertThat(services.createAnalysisPortCalled).isFalse();
  }

  @Test
  void execute_inDryRun_withDocument_notifiesAllStages() throws Exception {
    TrackingServiceFactory services = new TrackingServiceFactory();
    RunAnalysisReportExecutor executor = new RunAnalysisReportExecutor(services, "composite");

    Config config = new Config();
    RunContext context = new RunContext(Path.of(".").toAbsolutePath(), config, "run-test");
    context.withDryRun(true);

    List<String> events = new ArrayList<>();
    int exitCode =
        executor.execute(
            context,
            true,
            new RunAnalysisReportExecutor.StageListener() {
              @Override
              public void onStageStarted(String step) {
                events.add("start:" + step);
              }

              @Override
              public void onStageCompleted(String step) {
                events.add("done:" + step);
              }
            });

    assertThat(exitCode).isZero();
    assertThat(services.createAnalysisPortCalled).isFalse();
    assertThat(events)
        .containsExactly(
            "start:analyze",
            "done:analyze",
            "start:report",
            "done:report",
            "start:document",
            "done:document");
  }

  @Test
  void execute_nonDryRun_invokesAnalysisAndCreatesRunDirectories() throws Exception {
    TrackingServiceFactory services = new TrackingServiceFactory();
    RunAnalysisReportExecutor executor = new RunAnalysisReportExecutor(services, "test-engine");

    Config config = Config.createDefault();
    Path projectRoot = tempDir.resolve("project");
    Files.createDirectories(projectRoot);
    RunContext context = new RunContext(projectRoot, config, "run-test");
    context.withDryRun(false);

    int exitCode = executor.execute(context);

    RunPaths runDirectories =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId());
    assertThat(exitCode).isZero();
    assertThat(services.createAnalysisPortCalled).isTrue();
    assertThat(services.lastEngineType).isEqualTo("test-engine");
    assertThat(services.analysisInvoked).isTrue();
    assertThat(services.lastProjectRoot).isEqualTo(projectRoot);
    assertThat(services.lastConfig).isSameAs(config);
    assertThat(runDirectories.analysisDir()).exists();
    assertThat(runDirectories.reportDir()).exists();
  }

  @Test
  void execute_nonDryRun_withDocument_generatesDocsAndStoresMetadata() throws Exception {
    TrackingServiceFactory services = new TrackingServiceFactory(true);
    RunAnalysisReportExecutor executor = new RunAnalysisReportExecutor(services, "test-engine");

    Config config = Config.createDefault();
    Path projectRoot = tempDir.resolve("project-doc");
    Files.createDirectories(projectRoot);
    RunContext context = new RunContext(projectRoot, config, "run-doc");
    context.withDryRun(false);

    int exitCode = executor.execute(context, true);

    RunPaths runDirectories =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId());
    assertThat(exitCode).isZero();
    assertThat(runDirectories.runRoot().resolve("docs")).exists();
    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_GENERATED, Boolean.class))
        .hasValue(true);
    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_COUNT, Integer.class))
        .hasValueSatisfying(count -> assertThat(count).isGreaterThanOrEqualTo(1));
    assertThat(context.getMetadata(DocumentStage.METADATA_DOCUMENT_OUTPUT_DIR, String.class))
        .hasValue(runDirectories.runRoot().resolve("docs").toString());
  }

  @Test
  void execute_withDocument_notifiesStageListenerInOrder() throws Exception {
    TrackingServiceFactory services = new TrackingServiceFactory(true);
    RunAnalysisReportExecutor executor = new RunAnalysisReportExecutor(services, "test-engine");

    Config config = Config.createDefault();
    Path projectRoot = tempDir.resolve("project-stage-events");
    Files.createDirectories(projectRoot);
    RunContext context = new RunContext(projectRoot, config, "run-stage-events");
    context.withDryRun(false);

    List<String> events = new ArrayList<>();
    int exitCode =
        executor.execute(
            context,
            true,
            new RunAnalysisReportExecutor.StageListener() {
              @Override
              public void onStageStarted(String step) {
                events.add("start:" + step);
              }

              @Override
              public void onStageCompleted(String step) {
                events.add("done:" + step);
              }

              @Override
              public void onStageSkipped(String step) {
                events.add("skip:" + step);
              }
            });

    assertThat(exitCode).isZero();
    assertThat(events)
        .containsExactly(
            "start:analyze",
            "done:analyze",
            "start:report",
            "done:report",
            "start:document",
            "done:document");
  }

  @Test
  void execute_whenNoClasses_skipsReportAndDocument() throws Exception {
    TrackingServiceFactory services = new TrackingServiceFactory(false);
    RunAnalysisReportExecutor executor = new RunAnalysisReportExecutor(services, "test-engine");

    Config config = Config.createDefault();
    Path projectRoot = tempDir.resolve("project-skip-events");
    Files.createDirectories(projectRoot);
    RunContext context = new RunContext(projectRoot, config, "run-skip-events");
    context.withDryRun(false);

    List<String> events = new ArrayList<>();
    int exitCode =
        executor.execute(
            context,
            true,
            new RunAnalysisReportExecutor.StageListener() {
              @Override
              public void onStageStarted(String step) {
                events.add("start:" + step);
              }

              @Override
              public void onStageCompleted(String step) {
                events.add("done:" + step);
              }

              @Override
              public void onStageSkipped(String step) {
                events.add("skip:" + step);
              }
            });

    assertThat(exitCode).isZero();
    assertThat(events)
        .containsExactly("start:analyze", "done:analyze", "skip:report", "skip:document");
  }

  @Test
  void execute_withDocument_wrapsValidationFailureAsIOException() throws Exception {
    TrackingServiceFactory services = new TrackingServiceFactory(true);
    RunAnalysisReportExecutor executor = new RunAnalysisReportExecutor(services, "test-engine");

    Config config = Config.createDefault();
    Config.DocsConfig docsConfig = new Config.DocsConfig();
    docsConfig.setFormat("unsupported");
    config.setDocs(docsConfig);

    Path projectRoot = tempDir.resolve("project-invalid-docs");
    Files.createDirectories(projectRoot);
    RunContext context = new RunContext(projectRoot, config, "run-invalid-docs");
    context.withDryRun(false);

    List<String> events = new ArrayList<>();
    assertThatThrownBy(
            () ->
                executor.execute(
                    context,
                    true,
                    new RunAnalysisReportExecutor.StageListener() {
                      @Override
                      public void onStageStarted(String step) {
                        events.add("start:" + step);
                      }

                      @Override
                      public void onStageCompleted(String step) {
                        events.add("done:" + step);
                      }
                    }))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Document generation failed")
        .hasCauseInstanceOf(DocumentFlow.ValidationException.class);
    assertThat(events)
        .containsExactly(
            "start:analyze", "done:analyze", "start:report", "done:report", "start:document");
  }

  @Test
  void execute_rejectsNullContext() {
    TrackingServiceFactory services = new TrackingServiceFactory();
    RunAnalysisReportExecutor executor = new RunAnalysisReportExecutor(services, "composite");

    assertThatThrownBy(() -> executor.execute(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("RunContext is required");
  }

  @Test
  void execute_rejectsNullStageListener() {
    TrackingServiceFactory services = new TrackingServiceFactory();
    RunAnalysisReportExecutor executor = new RunAnalysisReportExecutor(services, "composite");

    Config config = new Config();
    RunContext context = new RunContext(Path.of(".").toAbsolutePath(), config, "run-test");

    assertThatThrownBy(() -> executor.execute(context, false, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("stageListener is required");
  }

  private static final class TrackingServiceFactory implements ServiceFactory {
    private final boolean withClasses;
    private boolean createAnalysisPortCalled;
    private boolean analysisInvoked;
    private String lastEngineType;
    private Path lastProjectRoot;
    private Config lastConfig;

    private TrackingServiceFactory() {
      this(false);
    }

    private TrackingServiceFactory(boolean withClasses) {
      this.withClasses = withClasses;
    }

    @Override
    public AnalysisPort createAnalysisPort(String engineType) {
      createAnalysisPortCalled = true;
      lastEngineType = engineType;
      return new AnalysisPort() {
        @Override
        public AnalysisResult analyze(Path projectRoot, Config config) throws IOException {
          analysisInvoked = true;
          lastProjectRoot = projectRoot;
          lastConfig = config;
          AnalysisResult result = new AnalysisResult(config.getProject().getId());
          if (withClasses) {
            ClassInfo classInfo = new ClassInfo();
            classInfo.setFqn("com.example.SampleService");
            classInfo.setFilePath("src/main/java/com/example/SampleService.java");
            result.setClasses(List.of(classInfo));
          }
          return result;
        }

        @Override
        public String getEngineName() {
          return "tracking";
        }

        @Override
        public boolean supports(Path projectRoot) {
          return true;
        }
      };
    }

    @Override
    public ResultMerger createResultMerger() {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public ResultVerifier createResultVerifier() {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public LlmClientPort createLlmClient(Config config) {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public LlmClientPort createDecoratedLlmClient(Config config, Path projectRoot) {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public BuildToolPort createBuildTool() {
      throw new UnsupportedOperationException("not used in this test");
    }
  }
}
