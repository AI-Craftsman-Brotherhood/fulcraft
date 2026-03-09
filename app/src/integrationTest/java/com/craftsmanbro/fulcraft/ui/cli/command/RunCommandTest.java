package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.Main;
import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.Pipeline;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineRunner;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunMetadataKeys;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.ui.cli.wiring.ServiceFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

class RunCommandTest {

  private static final class TestRunCommand extends RunCommand {
    private final PipelineRunner runner = mock(PipelineRunner.class);
    private final Pipeline pipeline = mock(Pipeline.class);
    private Config capturedConfig;

    private TestRunCommand() {
      when(runner.getPipeline()).thenReturn(pipeline);
      when(pipeline.getStageNodes()).thenReturn(defaultStageNodes());
    }

    @Override
    protected PipelineRunner createRunner(Config config) {
      this.capturedConfig = config;
      return runner;
    }

    PipelineRunner runner() {
      return runner;
    }

    Config capturedConfig() {
      return capturedConfig;
    }
  }

  private static Map<String, Stage> defaultStageNodes() {
    final LinkedHashMap<String, Stage> nodes = new LinkedHashMap<>();
    nodes.put("analyze", mock(Stage.class));
    nodes.put("select", mock(Stage.class));
    nodes.put("generate", mock(Stage.class));
    nodes.put("brittle_check", mock(Stage.class));
    nodes.put("report", mock(Stage.class));
    nodes.put("document", mock(Stage.class));
    nodes.put("explore", mock(Stage.class));
    return nodes;
  }

  @Test
  void doCall_runsPipelineWithExplicitStepsAndFlags(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,GENERATE", "--fail-fast", "--no-summary");

    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(Config.createDefault(), tempDir);

    assertThat(exitCode).isZero();
    verify(command.runner()).addListener(any());
    verify(command.runner())
        .runNodes(any(RunContext.class), eq(List.of("analyze", "generate")), eq(null), eq(null));
  }

  @Test
  void doCall_enablesExploreStageWhenExplicitStepsIncludeExplore(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,EXPLORE");

    Config config = Config.createDefault();
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getDocs()).isNotNull();
    assertThat(command.capturedConfig().getDocs().getFormat()).isEqualTo("html");
    verify(command.runner())
        .runNodes(
            any(RunContext.class),
            eq(List.of("analyze", "document", "explore")),
            eq(null),
            eq(null));
  }

  @Test
  void doCall_appliesDocumentFormatOverride(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,DOCUMENT", "--format", "all");

    Config config = Config.createDefault();
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getDocs()).isNotNull();
    assertThat(command.capturedConfig().getDocs().getFormat()).isEqualTo("all");
    verify(command.runner())
        .runNodes(any(RunContext.class), eq(List.of("analyze", "document")), eq(null), eq(null));
  }

  @Test
  void doCall_setsReportFormatToHtmlWhenExploreAndFormatHtml(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,EXPLORE", "--format", "html");

    Config config = Config.createDefault();
    config.getOutput().getFormat().setReport("markdown");
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getOutput().getReportFormat()).isEqualTo("html");
  }

  @Test
  void doCall_keepsExplicitNonMarkdownReportFormatWhenExploreAndFormatHtml(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,EXPLORE", "--format", "html");

    Config config = Config.createDefault();
    config.getOutput().getFormat().setReport("json");
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getOutput().getReportFormat()).isEqualTo("json");
  }

  @Test
  void doCall_enablesLlmAndPropagatesToRunContextMetadata(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,DOCUMENT,REPORT,EXPLORE", "--llm");

    Config config = Config.createDefault();
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getDocs().isUseLlm()).isTrue();

    ArgumentCaptor<RunContext> contextCaptor = ArgumentCaptor.forClass(RunContext.class);
    verify(command.runner())
        .runNodes(
            contextCaptor.capture(),
            eq(List.of("analyze", "document", "report", "explore")),
            eq(null),
            eq(null));
    RunContext capturedContext = contextCaptor.getValue();
    assertThat(capturedContext.getMetadata(RunMetadataKeys.LLM_ENABLED, Boolean.class))
        .contains(true);
    assertThat(capturedContext.getMetadata(RunMetadataKeys.llmStageKey("analyze"), Boolean.class))
        .contains(true);
    assertThat(capturedContext.getMetadata(RunMetadataKeys.llmStageKey("document"), Boolean.class))
        .contains(true);
    assertThat(capturedContext.getMetadata(RunMetadataKeys.llmStageKey("report"), Boolean.class))
        .contains(true);
    assertThat(capturedContext.getMetadata(RunMetadataKeys.llmStageKey("explore"), Boolean.class))
        .contains(true);
    assertThat(capturedContext.getMetadata(RunMetadataKeys.llmStageKey("generate"), Boolean.class))
        .contains(false);
    assertThat(capturedContext.getMetadata(RunMetadataKeys.LLM_STAGE_FLAGS, Object.class))
        .hasValueSatisfying(
            stageFlags -> {
              Map<?, ?> flags = asStageFlags(stageFlags);
              assertThat(flags.get("analyze")).isEqualTo(true);
              assertThat(flags.get("generate")).isEqualTo(false);
              assertThat(flags.get("report")).isEqualTo(true);
              assertThat(flags.get("document")).isEqualTo(true);
              assertThat(flags.get("explore")).isEqualTo(true);
            });
  }

  @Test
  void doCall_propagatesDisabledLlmWhenRunLlmIsNotEnabled(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,GENERATE");

    Config config = Config.createDefault();
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    ArgumentCaptor<RunContext> contextCaptor = ArgumentCaptor.forClass(RunContext.class);
    verify(command.runner())
        .runNodes(contextCaptor.capture(), eq(List.of("analyze", "generate")), eq(null), eq(null));

    RunContext capturedContext = contextCaptor.getValue();
    assertThat(capturedContext.getMetadata(RunMetadataKeys.LLM_ENABLED, Boolean.class))
        .contains(false);
    assertThat(capturedContext.getMetadata(RunMetadataKeys.llmStageKey("analyze"), Boolean.class))
        .contains(false);
    assertThat(capturedContext.getMetadata(RunMetadataKeys.llmStageKey("generate"), Boolean.class))
        .contains(false);
    assertThat(capturedContext.getMetadata(RunMetadataKeys.LLM_STAGE_FLAGS, Object.class))
        .hasValueSatisfying(
            stageFlags -> {
              Map<?, ?> flags = asStageFlags(stageFlags);
              assertThat(flags.get("analyze")).isEqualTo(false);
              assertThat(flags.get("generate")).isEqualTo(false);
            });
  }

  @Test
  void doCall_rejectsUnsupportedDocumentFormat(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,EXPLORE", "--format", "txt");

    assertThatThrownBy(() -> command.doCall(Config.createDefault(), tempDir))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("Unsupported --format value");
    verify(command.runner(), never()).runNodes(any(RunContext.class), any(), any(), any());
  }

  @Test
  void doCall_runsPipelineWithFromToRange(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--from", "GENERATE", "--to", "REPORT");

    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(Config.createDefault(), tempDir);

    assertThat(exitCode).isZero();
    verify(command.runner())
        .runNodes(any(RunContext.class), eq(null), eq("generate"), eq("report"));
  }

  @Test
  void doCall_rejectsWhenFromIsAfterTo(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--from", "REPORT", "--to", "ANALYZE");

    assertThatThrownBy(() -> command.doCall(Config.createDefault(), tempDir))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("--from must not be after --to");
  }

  @Test
  void doCall_runsAnalysisReportOnlyShortcutWhenDryRun(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    attachParentMain(command);
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--dry-run", "--steps", "ANALYZE,REPORT");

    int exitCode = command.doCall(Config.createDefault(), tempDir);

    assertThat(exitCode).isZero();
    verify(command.runner(), never()).runNodes(any(RunContext.class), any(), any(), any());
  }

  @Test
  void doCall_runsAnalysisReportWithDocumentShortcutWhenDryRun(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    attachParentMain(command);
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--dry-run", "--steps", "ANALYZE,REPORT,DOCUMENT");

    int exitCode = command.doCall(Config.createDefault(), tempDir);

    assertThat(exitCode).isZero();
    verify(command.runner(), never()).runNodes(any(RunContext.class), any(), any(), any());
  }

  @Test
  void doCall_runsPipelineWithDefaultStepsIncludingExploreWhenDryRun(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    attachParentMain(command);
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--dry-run");
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(Config.createDefault(), tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getDocs()).isNotNull();
    assertThat(command.capturedConfig().getDocs().getFormat()).isEqualTo("html");
    verify(command.runner()).runNodes(any(RunContext.class), eq(null), eq(null), eq(null));
  }

  @Test
  void doCall_runsAllConfiguredNodesWhenWorkflowFileConfigured(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    attachParentMain(command);
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--dry-run");

    Config config = Config.createDefault();
    config.getPipeline().setWorkflowFile("workflow.json");
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    verify(command.runner()).runNodes(any(RunContext.class), eq(null), eq(null), eq(null));
  }

  @Test
  void doCall_usesRequestedNodeWhenSpecificStepsIncludeGenerate(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "GENERATE");

    Config config = Config.createDefault();
    config.getPipeline().setStages(List.of("generate"));
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    verify(command.runner())
        .runNodes(any(RunContext.class), eq(List.of("generate")), eq(null), eq(null));
  }

  @Test
  void doCall_returnsOneAndMasksErrorWhenRunnerThrows(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    StringWriter errBuffer = new StringWriter();
    commandLine.setErr(new PrintWriter(errBuffer, true));
    commandLine.parseArgs("--steps", "ANALYZE,GENERATE");

    when(command.runner().runNodes(any(RunContext.class), any(), any(), any()))
        .thenThrow(new RuntimeException("token=superSecret123"));

    int exitCode = command.doCall(Config.createDefault(), tempDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(errBuffer.toString())
        .contains("ERROR:")
        .contains("token=****")
        .doesNotContain("superSecret123");
  }

  @Test
  void doCall_printsMaskedStackTraceWhenVerboseAndRunnerThrows(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    StringWriter errBuffer = new StringWriter();
    commandLine.setErr(new PrintWriter(errBuffer, true));
    commandLine.parseArgs("--steps", "ANALYZE,GENERATE", "--verbose");

    when(command.runner().runNodes(any(RunContext.class), any(), any(), any()))
        .thenThrow(new RuntimeException("token=superSecret123"));

    int exitCode = command.doCall(Config.createDefault(), tempDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(errBuffer.toString())
        .contains("ERROR:")
        .contains("RuntimeException")
        .contains("token=****")
        .doesNotContain("superSecret123");
  }

  @Test
  void doCall_createsDocsConfigWhenLlmOptionEnabledAndDocsIsNull(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,GENERATE", "--llm");

    Config config = Config.createDefault();
    config.setDocs(null);
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getDocs()).isNotNull();
    assertThat(command.capturedConfig().getDocs().isUseLlm()).isTrue();
  }

  @Test
  void doCall_marksGenerateStageEnabledWhenOnlyToIsProvided(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--to", "REPORT", "--llm");

    Config config = Config.createDefault();
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    ArgumentCaptor<RunContext> contextCaptor = ArgumentCaptor.forClass(RunContext.class);
    verify(command.runner()).runNodes(contextCaptor.capture(), eq(null), eq(null), eq("report"));
    RunContext capturedContext = contextCaptor.getValue();
    assertThat(capturedContext.getMetadata(RunMetadataKeys.llmStageKey("generate"), Boolean.class))
        .contains(true);
  }

  @Test
  void doCall_marksGenerateStageDisabledWhenRangeStartsAtReport(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--from", "REPORT", "--llm");

    Config config = Config.createDefault();
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    ArgumentCaptor<RunContext> contextCaptor = ArgumentCaptor.forClass(RunContext.class);
    verify(command.runner()).runNodes(contextCaptor.capture(), eq(null), eq("report"), eq(null));
    RunContext capturedContext = contextCaptor.getValue();
    assertThat(capturedContext.getMetadata(RunMetadataKeys.llmStageKey("generate"), Boolean.class))
        .contains(false);
  }

  @Test
  void doCall_doesNotForceHtmlWhenDocsFormatAlreadyNonMarkdown(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,EXPLORE");

    Config config = Config.createDefault();
    Config.DocsConfig docsConfig = new Config.DocsConfig();
    docsConfig.setFormat("pdf");
    config.setDocs(docsConfig);
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getDocs().getFormat()).isEqualTo("pdf");
  }

  @Test
  void doCall_keepsReportFormatWhenFormatHtmlButExploreIsNotRequested(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,DOCUMENT", "--format", "html");

    Config config = Config.createDefault();
    config.getOutput().getFormat().setReport("markdown");
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getOutput().getReportFormat()).isEqualTo("markdown");
  }

  @Test
  void doCall_supportsMdDocumentFormatAlias(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,DOCUMENT", "--format", "md");

    Config config = Config.createDefault();
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getDocs().getFormat()).isEqualTo("markdown");
  }

  @Test
  void doCall_setsReportFormatToHtmlWhenReportUsesMdAlias(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,EXPLORE", "--format", "html");

    Config config = Config.createDefault();
    config.getOutput().getFormat().setReport("md");
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getOutput().getReportFormat()).isEqualTo("html");
  }

  @Test
  void doCall_treatsBlankDocumentFormatAsUnset(@TempDir Path tempDir) {
    TestRunCommand command = new TestRunCommand();
    CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs("--steps", "ANALYZE,EXPLORE", "--format", "   ");

    Config config = Config.createDefault();
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getDocs().getFormat()).isEqualTo("html");
  }

  @Test
  void doCall_throwsWhenConfigIsNull(@TempDir Path tempDir) {
    RunCommand command = new RunCommand();

    assertThatThrownBy(() -> command.doCall(null, tempDir))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Config is required");
  }

  @Test
  void doCall_throwsWhenProjectRootIsNull() {
    RunCommand command = new RunCommand();

    assertThatThrownBy(() -> command.doCall(Config.createDefault(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("projectRoot is required");
  }

  @Test
  void getNodeIds_throwsUnsupportedOperationException() {
    RunCommand command = new RunCommand();

    assertThatThrownBy(command::getNodeIds)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Subclasses must override getNodeIds()");
  }

  @Test
  void getCommandDescription_returnsExpectedMessage() {
    RunCommand command = new RunCommand();

    assertThat(command.getCommandDescription()).isEqualTo("Starting pipeline execution");
  }

  private static void attachParentMain(RunCommand command) {
    Main main = mock(Main.class);
    ServiceFactory services = mock(ServiceFactory.class);
    when(main.getServices()).thenReturn(services);
    command.main = main;
  }

  private static Map<?, ?> asStageFlags(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<?, ?>) value;
  }
}
