package com.craftsmanbro.fulcraft.plugins.document.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentFlowTest {

  @TempDir Path tempDir;

  @Test
  void generate_addsTestLinkSectionWhenIncludeTestsEnabled() throws Exception {
    Config config = baseConfig();
    config.getDocs().setFormat("markdown");
    config.getDocs().setIncludeTests(true);

    Path output = tempDir.resolve("docs");
    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    DocumentFlow.Result result = flow.generate(sampleAnalysisResult(), config, tempDir, output);

    assertThat(result.totalCount()).isEqualTo(1);
    Path markdownFile = output.resolve("src/main/java/com/example/Service.md");
    assertThat(Files.exists(markdownFile)).isTrue();
    String content = Files.readString(markdownFile);
    assertThat(content)
        .contains("<!-- ful:test-links:start -->")
        .contains("<!-- ful:test-links:end -->");
  }

  @Test
  void generate_withAllFormatCreatesMarkdownHtmlAndPdfOutputs() throws Exception {
    Config config = baseConfig();
    config.getDocs().setFormat("all");

    Path output = tempDir.resolve("docs");
    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    DocumentFlow.Result result = flow.generate(sampleAnalysisResult(), config, tempDir, output);

    assertThat(result.totalCount()).isEqualTo(3);
    assertThat(Files.exists(output.resolve("markdown/src/main/java/com/example/Service.md")))
        .isTrue();
    assertThat(Files.exists(output.resolve("html/src/main/java/com/example/Service.html")))
        .isTrue();
    assertThat(Files.exists(output.resolve("pdf/src/main/java/com/example/Service_print.html")))
        .isTrue();
  }

  @Test
  void generate_withAllFormatInvokesProgressCallbacks() throws Exception {
    Config config = baseConfig();
    config.getDocs().setFormat("all");

    Path output = tempDir.resolve("docs");
    RecordingProgressListener progress = new RecordingProgressListener();
    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    DocumentFlow.Result result =
        flow.generate(sampleAnalysisResult(), config, tempDir, output, progress);

    assertThat(result.totalCount()).isEqualTo(3);
    assertThat(progress.markdownGenerating).isTrue();
    assertThat(progress.markdownCompleteCount).isEqualTo(1);
    assertThat(progress.lastMarkdownCount).isEqualTo(1);
    assertThat(progress.htmlGenerating).isTrue();
    assertThat(progress.pdfGenerating).isTrue();
    assertThat(progress.singleFileOutput).isNull();
    assertThat(progress.llmGenerating).isFalse();
    assertThat(progress.diagramGenerating).isFalse();
  }

  @Test
  void generate_withLlmEnabledCreatesDetailDocument() throws Exception {
    Config config = baseConfig();
    config.getDocs().setUseLlm(true);
    config.setLlm(new Config.LlmConfig());

    Path output = tempDir.resolve("docs");
    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("# LLM"));

    DocumentFlow.Result result = flow.generate(sampleAnalysisResult(), config, tempDir, output);

    assertThat(result.totalCount()).isEqualTo(1);
    assertThat(Files.exists(output.resolve("src/main/java/com/example/Service_detail.md")))
        .isTrue();
  }

  @Test
  void generate_throwsValidationExceptionWhenLlmEnabledWithoutLlmConfig() {
    Config config = baseConfig();
    config.getDocs().setUseLlm(true);

    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    assertThatThrownBy(
            () -> flow.generate(sampleAnalysisResult(), config, tempDir, tempDir.resolve("docs")))
        .isInstanceOf(DocumentFlow.ValidationException.class);
  }

  @Test
  void generate_withSingleFileEnabledWritesCombinedDocumentAndReportsProgress() throws Exception {
    Config config = baseConfig();
    config.getDocs().setSingleFile(true);
    config.getDocs().setIncludeTests(true);

    Path output = tempDir.resolve("docs");
    RecordingProgressListener progress = new RecordingProgressListener();
    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    DocumentFlow.Result result =
        flow.generate(sampleAnalysisResult(), config, tempDir, output, progress);

    Path combinedFile = output.resolve("analysis_report.md");
    assertThat(result.totalCount()).isEqualTo(1);
    assertThat(Files.exists(combinedFile)).isTrue();
    assertThat(progress.singleFileOutput).isEqualTo(combinedFile);
    assertThat(progress.markdownGenerating).isFalse();
    assertThat(progress.llmGenerating).isFalse();
  }

  @Test
  void generate_usesProjectDocsOutputWhenOutputOverrideIsNull() throws Exception {
    Config config = baseConfig();
    config.getProject().setDocsOutput("custom/docs");

    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    DocumentFlow.Result result = flow.generate(sampleAnalysisResult(), config, tempDir, null);

    Path expectedOutput = tempDir.resolve("custom/docs");
    assertThat(result.outputPath()).isEqualTo(expectedOutput);
    assertThat(Files.exists(expectedOutput.resolve("src/main/java/com/example/Service.md")))
        .isTrue();
  }

  @Test
  void generate_usesDefaultOutputDirWhenOutputOverrideAndProjectDocsOutputAreMissing()
      throws Exception {
    Config config = baseConfig();

    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    DocumentFlow.Result result = flow.generate(sampleAnalysisResult(), config, tempDir, null);

    Path expectedOutput = tempDir.resolve(".ful/docs");
    assertThat(result.outputPath()).isEqualTo(expectedOutput);
    assertThat(Files.exists(expectedOutput.resolve("src/main/java/com/example/Service.md")))
        .isTrue();
  }

  @Test
  void generate_outputOverrideTakesPrecedenceOverProjectDocsOutput() throws Exception {
    Config config = baseConfig();
    config.getProject().setDocsOutput("project/docs");
    Path outputOverride = Path.of("override/docs");

    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    DocumentFlow.Result result =
        flow.generate(sampleAnalysisResult(), config, tempDir, outputOverride);

    Path expectedOutput = tempDir.resolve("override/docs");
    assertThat(result.outputPath()).isEqualTo(expectedOutput);
    assertThat(Files.exists(expectedOutput.resolve("src/main/java/com/example/Service.md")))
        .isTrue();
    assertThat(Files.exists(tempDir.resolve("project/docs/src/main/java/com/example/Service.md")))
        .isFalse();
  }

  @Test
  void generate_includeTestsUsesTasksFromLatestRunDirectory() throws Exception {
    Config config = baseConfig();
    config.getDocs().setIncludeTests(true);

    Path runsRoot = tempDir.resolve(".ful/runs");
    Path olderRun = runsRoot.resolve("run-old");
    Path latestRun = runsRoot.resolve("run-latest");
    writeTasksJsonl(olderRun.resolve("plan"), "OldServiceTest", true);
    writeTasksJsonl(latestRun.resolve("plan"), "LatestServiceTest", true);
    Files.setLastModifiedTime(olderRun, FileTime.fromMillis(1_000L));
    Files.setLastModifiedTime(latestRun, FileTime.fromMillis(2_000L));

    Path output = tempDir.resolve("docs");
    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    flow.generate(sampleAnalysisResult(), config, tempDir, output);

    String content = Files.readString(output.resolve("src/main/java/com/example/Service.md"));
    assertThat(content).contains("LatestServiceTest");
    assertThat(content).doesNotContain("OldServiceTest");
  }

  @Test
  void generate_includeTestsPrefersPlanTasksFileOverRunRootTasksFile() throws Exception {
    Config config = baseConfig();
    config.getDocs().setIncludeTests(true);

    Path runDir = tempDir.resolve(".ful/runs/run-001");
    writeTasksJsonl(runDir, "RunRootServiceTest", true);
    writeTasksJsonl(runDir.resolve("plan"), "PlanServiceTest", true);

    Path output = tempDir.resolve("docs");
    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    flow.generate(sampleAnalysisResult(), config, tempDir, output);

    String content = Files.readString(output.resolve("src/main/java/com/example/Service.md"));
    assertThat(content).contains("PlanServiceTest");
    assertThat(content).doesNotContain("RunRootServiceTest");
  }

  @Test
  void generate_includeTestsSkipsUnselectedTasksAndFallsBackToInferredLinks() throws Exception {
    Config config = baseConfig();
    config.getDocs().setIncludeTests(true);

    Path runDir = tempDir.resolve(".ful/runs/run-001");
    writeTasksJsonl(runDir.resolve("plan"), "IgnoredServiceTest", false);

    Path output = tempDir.resolve("docs");
    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    flow.generate(sampleAnalysisResult(), config, tempDir, output);

    String content = Files.readString(output.resolve("src/main/java/com/example/Service.md"));
    assertThat(content).doesNotContain("IgnoredServiceTest");
    assertThat(content).contains("ServiceTest");
  }

  @Test
  void generate_withDiagramEnabledAddsDiagramOutputsAndInvokesDiagramCallback() throws Exception {
    Config config = baseConfig();
    config.getDocs().setDiagram(true);

    Path output = tempDir.resolve("docs");
    RecordingProgressListener progress = new RecordingProgressListener();
    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    DocumentFlow.Result result =
        flow.generate(sampleAnalysisResult(), config, tempDir, output, progress);

    assertThat(result.totalCount()).isEqualTo(4);
    assertThat(progress.diagramGenerating).isTrue();
    assertThat(Files.exists(output.resolve("diagrams/class_dependencies.md"))).isTrue();
    assertThat(Files.exists(output.resolve("diagrams/inheritance_hierarchy.md"))).isTrue();
    assertThat(Files.exists(output.resolve("diagrams/com_example_Service_calls.md"))).isTrue();
  }

  @Test
  void generate_throwsValidationExceptionWhenFormatIsUnsupported() {
    Config config = baseConfig();
    config.getDocs().setFormat("docx");

    DocumentFlow flow = new DocumentFlow((cfg, root) -> new StubLlmClient("unused"));

    assertThatThrownBy(
            () -> flow.generate(sampleAnalysisResult(), config, tempDir, tempDir.resolve("docs")))
        .isInstanceOf(DocumentFlow.ValidationException.class)
        .hasMessageContaining("docx");
  }

  private void writeTasksJsonl(Path directory, String testClassName, boolean selected)
      throws IOException {
    Files.createDirectories(directory);
    String content =
        "{\"task_id\":\"task-1\",\"class_fqn\":\"com.example.Service\",\"method_name\":\"doWork\","
            + "\"test_class_name\":\""
            + testClassName
            + "\",\"selected\":"
            + selected
            + "}\n";
    Files.writeString(directory.resolve("tasks.jsonl"), content);
  }

  private Config baseConfig() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setId("test-project");
    config.setProject(projectConfig);
    config.setDocs(new Config.DocsConfig());
    return config;
  }

  private AnalysisResult sampleAnalysisResult() {
    AnalysisResult result = new AnalysisResult("test-project");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    classInfo.setFilePath("src/main/java/com/example/Service.java");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("void doWork()");
    classInfo.setMethods(List.of(method));
    result.setClasses(List.of(classInfo));
    return result;
  }

  private static final class StubLlmClient implements LlmClientPort {
    private final String response;

    private StubLlmClient(String response) {
      this.response = response;
    }

    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      return response;
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile("stub", Set.of(), Optional.empty());
    }
  }

  private static final class RecordingProgressListener implements DocumentFlow.ProgressListener {
    private boolean markdownGenerating;
    private int markdownCompleteCount;
    private int lastMarkdownCount;
    private boolean htmlGenerating;
    private boolean pdfGenerating;
    private boolean diagramGenerating;
    private Path singleFileOutput;
    private boolean llmGenerating;

    @Override
    public void onMarkdownGenerating() {
      markdownGenerating = true;
    }

    @Override
    public void onMarkdownComplete(int count) {
      markdownCompleteCount++;
      lastMarkdownCount = count;
    }

    @Override
    public void onHtmlGenerating() {
      htmlGenerating = true;
    }

    @Override
    public void onPdfGenerating() {
      pdfGenerating = true;
    }

    @Override
    public void onDiagramGenerating() {
      diagramGenerating = true;
    }

    @Override
    public void onSingleFileComplete(Path outputFile) {
      singleFileOutput = outputFile;
    }

    @Override
    public void onLlmGenerating() {
      llmGenerating = true;
    }

    @Override
    public void onLlmComplete(int count, Path outputPath) {
      // No-op for this test listener.
    }
  }
}
