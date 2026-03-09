package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineRunner;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

class ExploreCommandTest {

  private static final class TestExploreCommand extends ExploreCommand {
    private final PipelineRunner runner = mock(PipelineRunner.class);
    private Config capturedConfig;

    @Override
    protected PipelineRunner createRunner(Config config) {
      capturedConfig = config;
      return runner;
    }

    private PipelineRunner runner() {
      return runner;
    }

    private Config capturedConfig() {
      return capturedConfig;
    }
  }

  @Test
  void getSteps_returnsAnalyzeDocumentAndExplore() {
    ExploreCommand command = new ExploreCommand();
    assertThat(command.getNodeIds())
        .containsExactly(
            PipelineNodeIds.ANALYZE,
            PipelineNodeIds.DOCUMENT,
            PipelineNodeIds.REPORT,
            PipelineNodeIds.EXPLORE);
  }

  @Test
  void getCommandDescription_returnsDescription() {
    ExploreCommand command = new ExploreCommand();
    assertThat(command.getCommandDescription()).isNotBlank();
  }

  @Test
  void doCall_enablesRequiredStagesAndRunsExploreFlow(@TempDir Path tempDir) {
    TestExploreCommand command = new TestExploreCommand();
    Config config = Config.createDefault();
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getPipeline().enabledStagesSet())
        .contains("analyze", "document", "report", "explore");
    assertThat(command.capturedConfig().getDocs()).isNotNull();
    assertThat(command.capturedConfig().getDocs().getFormat()).isEqualTo("html");
    assertThat(command.capturedConfig().getOutput().getReportFormat()).isEqualTo("html");
    verify(command.runner())
        .runNodes(
            any(RunContext.class),
            eq(List.of("analyze", "document", "report", "explore")),
            eq(null),
            eq(null));
  }

  @Test
  void doCall_keepsExplicitNonMarkdownDocumentFormat(@TempDir Path tempDir) {
    TestExploreCommand command = new TestExploreCommand();
    Config config = Config.createDefault();
    Config.DocsConfig docsConfig = new Config.DocsConfig();
    docsConfig.setFormat("pdf");
    config.setDocs(docsConfig);
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getDocs().getFormat()).isEqualTo("pdf");
    assertThat(command.capturedConfig().getOutput().getReportFormat()).isEqualTo("json");
  }

  @Test
  void doCall_appliesFormatOptionAndInheritsToReport(@TempDir Path tempDir) {
    TestExploreCommand command = new TestExploreCommand();
    new CommandLine(command).parseArgs("--format", "html");

    Config config = Config.createDefault();
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getDocs().getFormat()).isEqualTo("html");
    assertThat(command.capturedConfig().getOutput().getReportFormat()).isEqualTo("html");
  }

  @Test
  void doCall_enablesLlmWhenLlmOptionIsSpecified(@TempDir Path tempDir) {
    TestExploreCommand command = new TestExploreCommand();
    new CommandLine(command).parseArgs("--llm");

    Config config = Config.createDefault();
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(0);

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(command.capturedConfig().getDocs()).isNotNull();
    assertThat(command.capturedConfig().getDocs().isUseLlm()).isTrue();
  }

  @Test
  void doCall_rejectsUnsupportedFormatOption(@TempDir Path tempDir) {
    TestExploreCommand command = new TestExploreCommand();
    new CommandLine(command).parseArgs("--format", "txt");

    assertThatThrownBy(() -> command.doCall(Config.createDefault(), tempDir))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("Unsupported --format value");
  }
}
