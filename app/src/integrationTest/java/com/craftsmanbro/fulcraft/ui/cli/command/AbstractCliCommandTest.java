package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.Main;
import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.CommonOverrides;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineRunner;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.testsupport.KernelPortTestExtension;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

@ExtendWith(KernelPortTestExtension.class)
@Isolated
class AbstractCliCommandTest {

  @TempDir Path tempDir;
  private final PrintStream originalStdout = System.out;
  private final PrintStream originalStderr = System.err;

  @AfterEach
  void restoreLoggerOutput() {
    UiLogger.setOutput(originalStdout, originalStderr);
  }

  @Test
  void resolveProjectRoot_prefersCliOptionPositionalConfigAndDefault() {
    TestCommand command = new TestCommand();
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setRoot("config-root");
    config.setProject(projectConfig);

    command.projectRootOption = Path.of("option-root");
    command.projectRootPositional = Path.of("positional-root");
    assertThat(command.resolveProjectRoot(config)).isEqualTo(Path.of("option-root"));

    command.projectRootOption = null;
    assertThat(command.resolveProjectRoot(config)).isEqualTo(Path.of("positional-root"));

    command.projectRootPositional = null;
    assertThat(command.resolveProjectRoot(config)).isEqualTo(Path.of("config-root"));

    config.setProject(null);
    assertThat(command.resolveProjectRoot(config)).isEqualTo(Path.of("."));
  }

  @Test
  void buildCommonOverrides_usesJsonOutputAndColorMode() {
    TestCommand command = new TestCommand();
    command.jsonOutput = true;
    command.logFormat = "yaml";
    command.colorMode = "off";

    CommonOverrides overrides = command.buildCommonOverrides();

    assertThat(overrides.getEffectiveLogFormat()).isEqualTo("json");
    assertThat(overrides.getColorMode()).isEqualTo("off");
  }

  @Test
  void applyLoggerSettings_updatesLoggerFlags() {
    TestCommand command = new TestCommand();
    boolean initialJson = Logger.isJsonMode();
    boolean initialColor = Logger.isColorEnabled();

    try {
      CommonOverrides overrides = new CommonOverrides().withLogFormat("json").withColorMode("off");
      command.applyLoggerSettings(overrides);

      assertThat(Logger.isJsonMode()).isTrue();
      assertThat(Logger.isColorEnabled()).isFalse();
    } finally {
      Logger.setJsonMode(initialJson);
      Logger.setColorEnabled(initialColor);
    }
  }

  @Test
  void createContext_setsDryRunAndMetadata() {
    TestCommand command = new TestCommand();
    command.dryRun = true;
    Config config = new Config();
    Path projectRoot = tempDir.resolve("project");

    RunContext context = command.createContext(config, projectRoot);

    assertThat(context.isDryRun()).isTrue();
    assertThat(context.getProjectRoot()).isEqualTo(projectRoot.toAbsolutePath());
    assertThat(context.getRunId()).isNotBlank();
    assertThat(context.getMetadata()).containsKey("startTime");
  }

  @Test
  void doCall_runsPipelineAndPrintsDiagnostics() {
    ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
    UiLogger.setOutput(new PrintStream(outCapture, true, StandardCharsets.UTF_8), originalStderr);
    UiLogger.setColorEnabled(false);
    UiLogger.setJsonMode(false);

    TestCommand command = new TestCommand();
    Config config = Config.createDefault();
    RunContext context = new RunContext(tempDir.toAbsolutePath(), config, "run-success");
    context.addError("error-one");
    context.addWarning("warn-one");
    command.setContextToReturn(context);
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenReturn(7);

    int exitCode = command.doCall(config, tempDir);
    String output = outCapture.toString(StandardCharsets.UTF_8);

    assertThat(exitCode).isEqualTo(7);
    verify(command.runner()).runNodes(eq(context), eq(List.of("analyze")), eq(null), eq(null));
    assertThat(command.diagnosticsCallCount()).isEqualTo(1);
    assertThat(command.lastDiagnosticsContext()).isSameAs(context);
    assertThat(output).contains("=== Errors ===");
    assertThat(output).contains("  - error-one");
    assertThat(output).contains("=== Warnings ===");
    assertThat(output).contains("  - warn-one");
  }

  @Test
  void doCall_rethrowsParameterException() {
    TestCommand command = new TestCommand();
    Config config = Config.createDefault();
    RunContext context = new RunContext(tempDir.toAbsolutePath(), config, "run-parameter");
    command.setContextToReturn(context);
    ParameterException expected = new ParameterException(new CommandLine(command), "invalid args");
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any())).thenThrow(expected);

    assertThatThrownBy(() -> command.doCall(config, tempDir)).isSameAs(expected);
    assertThat(command.diagnosticsCallCount()).isZero();
  }

  @Test
  void doCall_masksErrorMessageAndReturnsOne_whenUnexpectedException() {
    StringWriter errCapture = new StringWriter();
    TestCommand command = new TestCommand();
    command.setErrWriter(new PrintWriter(errCapture, true));
    Config config = Config.createDefault();
    RunContext context = new RunContext(tempDir.toAbsolutePath(), config, "run-error");
    command.setContextToReturn(context);
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any()))
        .thenThrow(new IllegalStateException("password=super-secret"));

    int exitCode = command.doCall(config, tempDir);
    String errOutput = errCapture.toString();

    assertThat(exitCode).isOne();
    assertThat(errOutput).contains("ERROR:");
    assertThat(errOutput).contains("password=****");
    assertThat(errOutput).doesNotContain("super-secret");
    assertThat(errOutput).doesNotContain("java.lang.IllegalStateException");
    assertThat(command.diagnosticsCallCount()).isEqualTo(1);
    assertThat(command.lastDiagnosticsContext()).isSameAs(context);
  }

  @Test
  void doCall_printsMaskedStackTrace_whenVerboseEnabled() {
    StringWriter errCapture = new StringWriter();
    TestCommand command = new TestCommand();
    command.verbose = true;
    command.setErrWriter(new PrintWriter(errCapture, true));
    Config config = Config.createDefault();
    RunContext context = new RunContext(tempDir.toAbsolutePath(), config, "run-verbose");
    command.setContextToReturn(context);
    String secret = "0123456789abcdefghijklmnopqrstuv";
    when(command.runner().runNodes(any(RunContext.class), any(), any(), any()))
        .thenThrow(new RuntimeException("token=" + secret));

    int exitCode = command.doCall(config, tempDir);
    String errOutput = errCapture.toString();

    assertThat(exitCode).isOne();
    assertThat(errOutput).contains("ERROR:");
    assertThat(errOutput).contains("java.lang.RuntimeException");
    assertThat(errOutput).contains("token=****");
    assertThat(errOutput).doesNotContain(secret);
  }

  @Test
  void doCall_requiresNonNullConfigAndProjectRoot() {
    TestCommand command = new TestCommand();

    assertThatThrownBy(() -> command.doCall(null, tempDir))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Config is required");
    assertThatThrownBy(() -> command.doCall(new Config(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("projectRoot is required");
  }

  @Test
  void createRunner_requiresParentCommandAndConfig() {
    CreateRunnerCommand command = new CreateRunnerCommand();

    assertThatThrownBy(() -> command.createRunner(new Config()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Parent command is required");

    command.main = new Main();
    assertThatThrownBy(() -> command.createRunner(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Config is required");
  }

  @Test
  void printDiagnostics_returnsImmediately_whenContextIsNull() {
    ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
    UiLogger.setOutput(new PrintStream(outCapture, true, StandardCharsets.UTF_8), originalStderr);
    UiLogger.setColorEnabled(false);
    UiLogger.setJsonMode(false);

    CreateRunnerCommand command = new CreateRunnerCommand();
    command.printDiagnostics(null);

    assertThat(outCapture.toString(StandardCharsets.UTF_8)).isEmpty();
  }

  private static final class TestCommand extends AbstractCliCommand {
    private final PipelineRunner runner = mock(PipelineRunner.class);
    private RunContext contextToReturn;
    private PrintWriter errWriter;
    private int diagnosticsCallCount;
    private RunContext diagnosticsContext;

    @Override
    protected List<String> getNodeIds() {
      return List.of(PipelineNodeIds.ANALYZE);
    }

    @Override
    protected String getCommandDescription() {
      return "test";
    }

    @Override
    protected RunContext createContext(Config config, Path projectRoot) {
      if (contextToReturn != null) {
        return contextToReturn;
      }
      return super.createContext(config, projectRoot);
    }

    @Override
    protected PipelineRunner createRunner(Config config) {
      return runner;
    }

    @Override
    protected void printDiagnostics(RunContext context) {
      diagnosticsCallCount++;
      diagnosticsContext = context;
      super.printDiagnostics(context);
    }

    @Override
    protected PrintWriter getErrWriter() {
      if (errWriter != null) {
        return errWriter;
      }
      return super.getErrWriter();
    }

    PipelineRunner runner() {
      return runner;
    }

    void setContextToReturn(RunContext contextToReturn) {
      this.contextToReturn = contextToReturn;
    }

    void setErrWriter(PrintWriter errWriter) {
      this.errWriter = errWriter;
    }

    int diagnosticsCallCount() {
      return diagnosticsCallCount;
    }

    RunContext lastDiagnosticsContext() {
      return diagnosticsContext;
    }
  }

  private static final class CreateRunnerCommand extends AbstractCliCommand {

    @Override
    protected List<String> getNodeIds() {
      return List.of(PipelineNodeIds.ANALYZE);
    }

    @Override
    protected String getCommandDescription() {
      return "create-runner";
    }
  }
}
