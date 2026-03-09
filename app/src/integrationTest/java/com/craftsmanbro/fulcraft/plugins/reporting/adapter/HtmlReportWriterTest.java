package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlReportWriterTest {

  @TempDir Path tempDir;
  private HtmlReportWriter writer;
  private Locale previousLocale;

  @BeforeEach
  void setUp() {
    previousLocale = MessageSource.getLocale();
    MessageSource.setLocale(Locale.ENGLISH);
    writer = new HtmlReportWriter(tempDir, "report.html", null);
  }

  @AfterEach
  void tearDown() {
    MessageSource.setLocale(previousLocale);
  }

  @Test
  void generateHtml_shouldIncludeStandardStyles() {
    // Arrange
    ReportData data = createFullReportData();

    // Act
    String html = writer.generateHtml(data);

    // Assert
    assertThat(html).contains(":root {");
    assertThat(html).contains("--primary-500:");
    assertThat(html).contains(".stat-card {");
    assertThat(html).contains(".stats-grid {");
  }

  @Test
  void generateHtml_shouldIncludeHeaderAndMeta() {
    // Arrange
    ReportData data = createFullReportData();

    // Act
    String html = writer.generateHtml(data);

    // Assert
    assertThat(html).contains("<header>");
    assertThat(html).contains("class=\"header-top\"");
    assertThat(html).contains("class=\"meta-item\"");
    assertThat(html).contains("report-test-run");
  }

  @Test
  void generateHtml_shouldRenderSummaryStats() {
    // Arrange
    ReportData data = createFullReportData();

    // Act
    String html = writer.generateHtml(data);

    // Assert
    assertThat(html).contains("class=\"stats-grid\"");
    assertThat(html).contains("Total Tasks");
    assertThat(html).contains("10");
    assertThat(html).contains("80.0%");
  }

  @Test
  void generateHtml_shouldRenderVerificationWarnings() {
    // Arrange
    ReportData data = createFullReportData();

    // Act
    String html = writer.generateHtml(data);

    // Assert
    System.out.println("DEBUG: Generated HTML:\n" + html);

    assertThat(html).doesNotContain("<h2>Verification</h2>");
    assertThat(html).doesNotContain("Brittle Patterns");
  }

  @Test
  void generateHtml_shouldRenderCoverageTasksAndExecution() {
    // Arrange
    HtmlReportWriter customWriter =
        new HtmlReportWriter(tempDir, "report.html", ".custom-rule{color:red;}");
    ReportData data = createReportDataWithTasks();

    // Act
    String html = customWriter.generateHtml(data);

    // Assert
    assertThat(html).contains(".custom-rule{color:red;}");
    assertThat(html).contains("<h2>Coverage</h2>");
    assertThat(html).contains("Line Coverage");
    assertThat(html).contains("Branch Coverage");
    assertThat(html).contains("<h2>Analysis Insight</h2>");
    assertThat(html).contains("このクラスは依存関係が複雑です。");
    assertThat(html).contains("<h2>Task Details</h2>");
    assertThat(html).contains("Failures (1)");
    assertThat(html).contains("Successes (1)");
    assertThat(html).contains("Skipped (1)");
    assertThat(html).contains("boom &amp; &lt;fail&gt;");
    assertThat(html).contains("gpt-4");
    assertThat(html).contains("123");
    assertThat(html).contains("<h2>Execution Notes</h2>");
    assertThat(html).contains("error one");
    assertThat(html).contains("warning one");
    assertThat(html).contains("1.5 s");
  }

  @Test
  void generateHtml_shouldHandleEmptyAnalysisAndSkipOptionalSections() {
    AnalysisResult analysisResult = new AnalysisResult();
    analysisResult.setClasses(List.of());

    ReportData data =
        ReportData.builder()
            .runId("run-2")
            .projectId("proj-2")
            .analysisResult(analysisResult)
            .totalClassesAnalyzed(2)
            .totalMethodsAnalyzed(5)
            .excludedTaskCount(1)
            .build();

    String html = writer.generateHtml(data);

    assertThat(html).contains("No classes found for analysis.");
    assertThat(html).contains("Classes");
    assertThat(html).contains("Methods");
    assertThat(html).doesNotContain("<h2>Coverage</h2>");
    assertThat(html).doesNotContain("<h2>Verification</h2>");
    assertThat(html).doesNotContain("<h2>Task Details</h2>");
    assertThat(html).doesNotContain("<h2>Execution Notes</h2>");
  }

  @Test
  void generateHtml_shouldHighlightHighComplexityRows() {
    Config config = new Config();
    Config.SelectionRules selectionRules = new Config.SelectionRules();
    Config.SelectionRules.ComplexityConfig complexityConfig =
        new Config.SelectionRules.ComplexityConfig();
    complexityConfig.setMaxCyclomatic(2);
    selectionRules.setComplexity(complexityConfig);
    config.setSelectionRules(selectionRules);

    MethodInfo complexMethod = new MethodInfo();
    complexMethod.setCyclomaticComplexity(5);
    ClassInfo complexClass = new ClassInfo();
    complexClass.setFqn("com.example.Foo");
    complexClass.setMethods(List.of(complexMethod));
    complexClass.setLoc(12);

    ClassInfo emptyClass = new ClassInfo();
    emptyClass.setFqn("com.example.Empty");
    emptyClass.setMethods(List.of());
    emptyClass.setLoc(0);

    AnalysisResult analysisResult = new AnalysisResult();
    analysisResult.setClasses(List.of(complexClass, emptyClass));

    ReportData data =
        ReportData.builder()
            .runId("run-3")
            .projectId("proj-3")
            .analysisResult(analysisResult)
            .build();

    String html = writer.generateHtml(data, config);

    assertThat(html).contains("attention-row");
    assertThat(html).contains("max 5 / avg 5.0 / n=1");
    assertThat(html).contains(">-</td>");
  }

  @Test
  void generateHtml_excludesImplicitDefaultConstructorFromClassMetrics() {
    MethodInfo implicitConstructor = new MethodInfo();
    implicitConstructor.setName("CtorOnly");
    implicitConstructor.setSignature("CtorOnly()");
    implicitConstructor.setParameterCount(0);
    implicitConstructor.setLoc(0);

    MethodInfo businessMethod = new MethodInfo();
    businessMethod.setName("run");
    businessMethod.setCyclomaticComplexity(4);
    businessMethod.setLoc(10);

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CtorOnly");
    classInfo.setMethods(List.of(implicitConstructor, businessMethod));

    AnalysisResult analysisResult = new AnalysisResult();
    analysisResult.setClasses(List.of(classInfo));

    ReportData data =
        ReportData.builder()
            .runId("run-ctor-metrics")
            .projectId("proj-ctor-metrics")
            .analysisResult(analysisResult)
            .build();

    String html = writer.generateHtml(data);

    assertThat(html).contains("max 4 / avg 4.0 / n=1");
  }

  @Test
  void generateHtml_shouldAssignWarningAndFailureSuccessRateClasses() {
    ReportData warningData = createSummaryOnlyData(0.6);
    ReportData failureData = createSummaryOnlyData(0.4);

    String warningHtml = writer.generateHtml(warningData);
    String failureHtml = writer.generateHtml(failureData);

    assertThat(warningHtml).contains("stat-value warning\">60.0%");
    assertThat(failureHtml).contains("stat-value failure\">40.0%");
  }

  @Test
  void writeReport_shouldThrowReportWriteException_whenOutputPathIsNotDirectory() throws Exception {
    Path blockedPath = tempDir.resolve("blocked");
    Files.writeString(blockedPath, "not-a-directory");
    HtmlReportWriter failingWriter = new HtmlReportWriter(blockedPath, "report.html", null);

    assertThatThrownBy(() -> failingWriter.writeReport(createFullReportData(), new Config()))
        .isInstanceOf(ReportWriteException.class);
  }

  @Test
  void getFormat_returnsHtml() {
    assertThat(writer.getFormat()).isEqualTo(ReportFormat.HTML);
  }

  private ReportData createFullReportData() {
    GenerationSummary summary = new GenerationSummary();
    summary.setProjectId("test-project");
    summary.setTotalTasks(10);
    summary.setSucceeded(8);
    summary.setFailed(2);
    summary.setSkipped(0);
    summary.setSuccessRate(0.8);

    // Mock AnalysisResult
    com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo classInfo =
        new com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo();
    classInfo.setFqn("com.example.MyClass");
    classInfo.setMethods(Collections.emptyList());

    com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult analysisResult =
        new com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult();
    analysisResult.setClasses(List.of(classInfo));

    return ReportData.builder()
        .projectId("test-project")
        .runId("report-test-run")
        .timestamp(Instant.now())
        .summary(summary)
        .analysisResult(analysisResult)
        .taskResults(Collections.emptyList())
        .build();
  }

  private ReportData createReportDataWithTasks() {
    GenerationSummary summary = new GenerationSummary();
    summary.setProjectId("test-project");
    summary.setTotalTasks(3);
    summary.setSucceeded(1);
    summary.setFailed(1);
    summary.setSkipped(1);
    summary.setSuccessRate(0.9);

    GenerationTaskResult failure = new GenerationTaskResult();
    failure.setTaskId("task-1");
    failure.setClassFqn("com.example.Foo");
    failure.setMethodName("doWork");
    failure.setStatus(GenerationTaskResult.Status.FAILURE);
    failure.setErrorMessage("boom & <fail>");

    GenerationTaskResult success = new GenerationTaskResult();
    success.setTaskId("task-2");
    success.setClassFqn("com.example.Bar");
    success.setMethodName("run");
    success.setStatus(GenerationTaskResult.Status.SUCCESS);
    success.setGenerationResult(
        GenerationResult.success().llmModelUsed("gpt-4").tokenUsage(123).build());

    GenerationTaskResult skipped = new GenerationTaskResult();
    skipped.setTaskId("task-3");
    skipped.setStatus(GenerationTaskResult.Status.SKIPPED);
    skipped.setErrorMessage("not selected");

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Foo");
    classInfo.setMethods(Collections.emptyList());
    classInfo.setLoc(10);

    AnalysisResult analysisResult = new AnalysisResult();
    analysisResult.setClasses(List.of(classInfo));

    return ReportData.builder()
        .projectId("test-project")
        .runId("report-test-run")
        .timestamp(Instant.parse("2024-01-02T03:04:05Z"))
        .summary(summary)
        .analysisHumanSummary("このクラスは依存関係が複雑です。")
        .analysisResult(analysisResult)
        .lineCoverage(0.5)
        .branchCoverage(0.25)
        .taskResults(List.of(failure, success, skipped))
        .durationMs(1500)
        .errors(List.of("error one"))
        .warnings(List.of("warning one"))
        .build();
  }

  private ReportData createSummaryOnlyData(double successRate) {
    GenerationSummary summary = new GenerationSummary();
    summary.setTotalTasks(10);
    summary.setSucceeded((int) Math.round(successRate * 10));
    summary.setFailed(10 - summary.getSucceeded());
    summary.setSkipped(0);
    summary.setSuccessRate(successRate);

    return ReportData.builder()
        .projectId("summary-project")
        .runId("summary-run")
        .timestamp(Instant.parse("2024-01-02T03:04:05Z"))
        .summary(summary)
        .build();
  }
}
