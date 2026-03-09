package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.Main;
import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.document.flow.DocumentFlow;
import com.craftsmanbro.fulcraft.ui.cli.wiring.ServiceFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ListResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

@Isolated
class DocumentCommandTest {

  static class TestDocumentCommand extends DocumentCommand {
    AnalysisResult mockResult;
    DocumentFlow mockFlow;

    @Override
    protected AnalysisResult analyzeProject(Config config, Path projectRoot) throws IOException {
      if (mockResult == null) {
        mockResult = mock(AnalysisResult.class);
      }
      return mockResult;
    }

    @Override
    protected DocumentFlow createDocumentFlow() {
      if (mockFlow == null) {
        mockFlow = mock(DocumentFlow.class);
      }
      return mockFlow;
    }

    Path exposedProjectRootOption() {
      return getProjectRootOption();
    }

    Path exposedProjectRootPositional() {
      return getProjectRootPositional();
    }

    boolean exposedVerboseEnabled() {
      return isVerboseEnabled();
    }

    boolean exposedShouldApplyProjectRootToConfig() {
      return shouldApplyProjectRootToConfig();
    }
  }

  @Test
  void doCall_executesDocumentFlow_andResolvesDefaultOutputPath(@TempDir Path tempDir)
      throws Exception {
    TestDocumentCommand command = new TestDocumentCommand();
    command.setResourceBundle(new TestBundle());

    AnalysisResult result = mock(AnalysisResult.class);
    when(result.getClasses()).thenReturn(java.util.List.of(mock(ClassInfo.class)));
    command.mockResult = result;

    DocumentFlow flow = mock(DocumentFlow.class);
    command.mockFlow = flow;

    DocumentFlow.Result flowResult = new DocumentFlow.Result(1, tempDir);
    when(flow.generate(any(), any(), any(), any(), any())).thenReturn(flowResult);

    Config config = Config.createDefault();

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    ArgumentCaptor<Path> outputPathCaptor = ArgumentCaptor.forClass(Path.class);
    ArgumentCaptor<DocumentFlow.ProgressListener> progressListenerCaptor =
        ArgumentCaptor.forClass(DocumentFlow.ProgressListener.class);
    verify(command.mockFlow)
        .generate(
            eq(result),
            any(Config.class),
            eq(tempDir),
            outputPathCaptor.capture(),
            progressListenerCaptor.capture());
    Path outputPath = outputPathCaptor.getValue();
    assertThat(outputPath.getFileName()).hasToString("docs");
    assertThat(outputPath.toString())
        .startsWith(tempDir.resolve(".ful").resolve("runs").toString());

    DocumentFlow.ProgressListener progressListener = progressListenerCaptor.getValue();
    progressListener.onMarkdownGenerating();
    progressListener.onMarkdownComplete(1);
    progressListener.onHtmlGenerating();
    progressListener.onPdfGenerating();
    progressListener.onDiagramGenerating();
    progressListener.onSingleFileComplete(tempDir.resolve("single.md"));
    progressListener.onLlmGenerating();
    progressListener.onLlmComplete(1, tempDir.resolve("llm"));
  }

  @Test
  void doCall_usesExplicitOutputPath_whenOutputOptionProvided(@TempDir Path tempDir)
      throws Exception {
    TestDocumentCommand command = new TestDocumentCommand();
    command.setResourceBundle(new TestBundle());
    Path explicitOutput = tempDir.resolve("custom-docs");
    new CommandLine(command).parseArgs("--output", explicitOutput.toString());

    AnalysisResult result = mock(AnalysisResult.class);
    when(result.getClasses()).thenReturn(java.util.List.of(mock(ClassInfo.class)));
    command.mockResult = result;

    DocumentFlow flow = mock(DocumentFlow.class);
    command.mockFlow = flow;
    when(flow.generate(any(), any(), any(), any(), any()))
        .thenReturn(new DocumentFlow.Result(2, explicitOutput));

    int exitCode = command.doCall(Config.createDefault(), tempDir);

    assertThat(exitCode).isZero();
    verify(flow)
        .generate(
            eq(result),
            any(Config.class),
            eq(tempDir),
            eq(explicitOutput),
            any(DocumentFlow.ProgressListener.class));
  }

  @Test
  void doCall_skipsDocumentFlow_whenNoClassesFound(@TempDir Path tempDir) throws Exception {
    TestDocumentCommand command = new TestDocumentCommand();
    command.setResourceBundle(new TestBundle());

    AnalysisResult result = mock(AnalysisResult.class);
    when(result.getClasses()).thenReturn(java.util.List.of());
    command.mockResult = result;
    command.mockFlow = mock(DocumentFlow.class);

    int exitCode = command.doCall(Config.createDefault(), tempDir);

    assertThat(exitCode).isZero();
    verifyNoInteractions(command.mockFlow);
  }

  @Test
  void doCall_rethrowsValidationExceptionAsParameterException(@TempDir Path tempDir)
      throws Exception {
    TestDocumentCommand command = new TestDocumentCommand();
    command.setResourceBundle(new TestBundle());
    command.spec = new CommandLine(command).getCommandSpec();

    AnalysisResult result = mock(AnalysisResult.class);
    when(result.getClasses()).thenReturn(java.util.List.of(mock(ClassInfo.class)));
    command.mockResult = result;

    DocumentFlow flow = mock(DocumentFlow.class);
    when(flow.generate(any(), any(), any(), any(), any()))
        .thenThrow(new DocumentFlow.ValidationException("invalid document options"));
    command.mockFlow = flow;

    assertThatThrownBy(() -> command.doCall(Config.createDefault(), tempDir))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("invalid document options");
  }

  @Test
  void doCall_rethrowsParameterExceptionFromDocumentFlow(@TempDir Path tempDir) throws Exception {
    TestDocumentCommand command = new TestDocumentCommand();
    command.setResourceBundle(new TestBundle());

    AnalysisResult result = mock(AnalysisResult.class);
    when(result.getClasses()).thenReturn(java.util.List.of(mock(ClassInfo.class)));
    command.mockResult = result;

    ParameterException expected =
        new ParameterException(new CommandLine(command), "invalid parameter");
    DocumentFlow flow = mock(DocumentFlow.class);
    when(flow.generate(any(), any(), any(), any(), any())).thenThrow(expected);
    command.mockFlow = flow;

    assertThatThrownBy(() -> command.doCall(Config.createDefault(), tempDir)).isSameAs(expected);
  }

  @Test
  void doCall_returnsNonZero_whenIOExceptionOccurs(@TempDir Path tempDir) throws Exception {
    TestDocumentCommand command = new TestDocumentCommand();
    command.setResourceBundle(new TestBundle());
    new CommandLine(command).parseArgs("--verbose");

    AnalysisResult result = mock(AnalysisResult.class);
    when(result.getClasses()).thenReturn(java.util.List.of(mock(ClassInfo.class)));
    command.mockResult = result;

    DocumentFlow flow = mock(DocumentFlow.class);
    when(flow.generate(any(), any(), any(), any(), any())).thenThrow(new IOException("disk full"));
    command.mockFlow = flow;

    int exitCode = command.doCall(Config.createDefault(), tempDir);

    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void doCall_returnsNonZero_whenIOExceptionOccursWithoutVerbose(@TempDir Path tempDir)
      throws Exception {
    TestDocumentCommand command = new TestDocumentCommand();
    command.setResourceBundle(new TestBundle());

    AnalysisResult result = mock(AnalysisResult.class);
    when(result.getClasses()).thenReturn(java.util.List.of(mock(ClassInfo.class)));
    command.mockResult = result;

    DocumentFlow flow = mock(DocumentFlow.class);
    when(flow.generate(any(), any(), any(), any(), any()))
        .thenThrow(new IOException("write failed"));
    command.mockFlow = flow;

    int exitCode = command.doCall(Config.createDefault(), tempDir);

    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void analyzeProject_usesCompositeAnalysisPort_andSkipsEnrichmentForEmptyClasses(
      @TempDir Path tempDir) throws Exception {
    DocumentCommand command = new DocumentCommand();
    Main main = mock(Main.class);
    ServiceFactory services = mock(ServiceFactory.class);
    AnalysisPort analysisPort = mock(AnalysisPort.class);
    command.main = main;
    when(main.getServices()).thenReturn(services);
    when(services.createAnalysisPort("composite")).thenReturn(analysisPort);

    Config config = Config.createDefault();
    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of());
    when(analysisPort.analyze(tempDir, config)).thenReturn(result);

    AnalysisResult actual = command.analyzeProject(config, tempDir);

    assertThat(actual).isSameAs(result);
    verify(services).createAnalysisPort("composite");
    verify(analysisPort).analyze(tempDir, config);
  }

  @Test
  void analyzeProject_swallowDynamicResolutionRuntimeErrors(@TempDir Path tempDir)
      throws Exception {
    DocumentCommand command = new DocumentCommand();
    Main main = mock(Main.class);
    ServiceFactory services = mock(ServiceFactory.class);
    AnalysisPort analysisPort = mock(AnalysisPort.class);
    command.main = main;
    when(main.getServices()).thenReturn(services);
    when(services.createAnalysisPort("composite")).thenReturn(analysisPort);

    Config config = Config.createDefault();
    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(new ClassInfo()));
    when(analysisPort.analyze(any(), same(config))).thenReturn(result);

    AnalysisResult actual = command.analyzeProject(config, null);

    assertThat(actual).isSameAs(result);
    verify(analysisPort).analyze(null, config);
  }

  @Test
  void analyzeProject_enrichesWithDynamicResolutions_whenSourceFileExists(@TempDir Path tempDir)
      throws Exception {
    DocumentCommand command = new DocumentCommand();
    Main main = mock(Main.class);
    ServiceFactory services = mock(ServiceFactory.class);
    AnalysisPort analysisPort = mock(AnalysisPort.class);
    command.main = main;
    when(main.getServices()).thenReturn(services);
    when(services.createAnalysisPort("composite")).thenReturn(analysisPort);

    Path sourceFile = tempDir.resolve("src/main/java/com/example/Foo.java");
    java.nio.file.Files.createDirectories(sourceFile.getParent());
    java.nio.file.Files.writeString(sourceFile, "package com.example; class Foo { void x() {} }");

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Foo");
    classInfo.setFilePath("src/main/java/com/example/Foo.java");
    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(classInfo));
    when(analysisPort.analyze(tempDir, null)).thenReturn(result);

    AnalysisResult actual = command.analyzeProject(null, tempDir);

    assertThat(actual).isSameAs(result);
    verify(analysisPort).analyze(tempDir, null);
  }

  @Test
  void createDocumentFlow_returnsFlow_whenMainServicesConfigured() {
    DocumentCommand command = new DocumentCommand();
    Main main = mock(Main.class);
    ServiceFactory services = mock(ServiceFactory.class);
    command.main = main;
    when(main.getServices()).thenReturn(services);

    assertThat(command.createDocumentFlow()).isNotNull();
  }

  @Test
  void accessors_returnParsedValues() {
    TestDocumentCommand optionCommand = new TestDocumentCommand();
    Path optionPath = Path.of("opt-project");
    new CommandLine(optionCommand).parseArgs("--project-root", optionPath.toString(), "--verbose");
    assertThat(optionCommand.exposedProjectRootOption()).isEqualTo(optionPath);
    assertThat(optionCommand.exposedVerboseEnabled()).isTrue();
    assertThat(optionCommand.exposedShouldApplyProjectRootToConfig()).isFalse();

    TestDocumentCommand positionalCommand = new TestDocumentCommand();
    Path positionalPath = Path.of("pos-project");
    new CommandLine(positionalCommand).parseArgs(positionalPath.toString());
    assertThat(positionalCommand.exposedProjectRootPositional()).isEqualTo(positionalPath);
  }

  @Test
  void buildConfigOverrides_appliesDocumentOptions(@TempDir Path tempDir) {
    TestDocumentCommand command = new TestDocumentCommand();
    CommandLine cmd = new CommandLine(command);
    cmd.parseArgs(
        "--files",
        "A.java,B.java",
        "--dirs",
        "src/main/java,src/test/java",
        "--format",
        "html",
        "--diagram",
        "--include-tests",
        "--llm",
        "--single-file",
        "--diagram-format",
        "plantuml");

    Config config = new Config();
    command.buildConfigOverrides(tempDir).forEach(override -> override.apply(config));

    assertThat(config.getProject().getIncludePaths())
        .containsExactly("A.java", "B.java", "src/main/java", "src/test/java");
    assertThat(config.getDocs().getFormat()).isEqualTo("html");
    assertThat(config.getDocs().isDiagram()).isTrue();
    assertThat(config.getDocs().isIncludeTests()).isTrue();
    assertThat(config.getDocs().isUseLlm()).isTrue();
    assertThat(config.getDocs().isSingleFile()).isTrue();
    assertThat(config.getDocs().getDiagramFormat()).isEqualTo("plantuml");
  }

  static class TestBundle extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {
        {"document.start", "Start {0}"},
        {"document.no_classes", "No classes"},
        {"document.complete", "Complete {0} in {1}"},
        {"document.failed", "Failed {0}"},
        {"document.markdown.generating", "Generating Markdown"},
        {"document.markdown.complete", "Markdown Complete"},
        {"document.html.generating", "Generating HTML"},
        {"document.pdf.generating", "Generating PDF"},
        {"document.diagram.generating", "Generating Diagram"},
        {"document.single.complete", "Single file complete"},
        {"document.llm.generating", "Generating LLM docs"},
        {"document.llm.complete", "LLM docs complete"}
      };
    }
  }
}
