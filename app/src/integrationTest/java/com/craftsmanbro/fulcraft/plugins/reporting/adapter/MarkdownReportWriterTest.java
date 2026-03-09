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
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownReportWriterTest {

  @TempDir Path tempDir;
  private Locale previousLocale;

  @BeforeEach
  void setUp() {
    previousLocale = MessageSource.getLocale();
    MessageSource.setLocale(Locale.ENGLISH);
  }

  @AfterEach
  void tearDown() {
    MessageSource.setLocale(previousLocale);
  }

  @Test
  void generateMarkdown_includesCoverageAndDetailsSections() {
    ReportData data = buildReportData();
    MarkdownReportWriter writer = new MarkdownReportWriter();

    String markdown = writer.generateMarkdown(data);

    assertThat(markdown).contains("UT Generation Report");
    assertThat(markdown).contains("proj-1");
    assertThat(markdown).contains("`run-1`");
    assertThat(markdown).contains("Summary");
    assertThat(markdown).contains("Analysis Insight");
    assertThat(markdown).contains("このクラスは依存関係が複雑です。");
    assertThat(markdown).contains("Coverage");
    assertThat(markdown).contains("50.0%");
    assertThat(markdown).doesNotContain("Brittle Patterns");
    assertThat(markdown).contains("Failures (1)");
    assertThat(markdown).contains("Successes (1)");
    assertThat(markdown).contains("Skipped (1)");
    assertThat(markdown).contains("boom \\| oops line2");
    assertThat(markdown).contains("Click to expand (1 tasks)");
    assertThat(markdown).contains("gpt-4");
    assertThat(markdown).contains("42");
    assertThat(markdown).contains("Execution Notes");
    assertThat(markdown).contains("error one");
    assertThat(markdown).contains("warning one");
    assertThat(markdown).contains("1.5 s");
  }

  @Test
  void generateMarkdown_analysisOnlyReportIncludesHotspotsAndRecommendations() {
    AnalysisResult analysisResult = new AnalysisResult();

    MethodInfo calculateInvoice = new MethodInfo();
    calculateInvoice.setName("calculateInvoice");
    calculateInvoice.setLoc(120);
    calculateInvoice.setCyclomaticComplexity(32);
    calculateInvoice.setPartOfCycle(true);

    MethodInfo validateCustomer = new MethodInfo();
    validateCustomer.setName("validateCustomer");
    validateCustomer.setLoc(40);
    validateCustomer.setCyclomaticComplexity(9);
    validateCustomer.setDuplicate(true);

    ClassInfo invoiceService = new ClassInfo();
    invoiceService.setFqn("com.example.billing.ComplexInvoiceService");
    invoiceService.setFilePath("src/main/java/com/example/billing/ComplexInvoiceService.java");
    invoiceService.setLoc(240);
    invoiceService.setMethods(List.of(calculateInvoice, validateCustomer));

    MethodInfo processPayment = new MethodInfo();
    processPayment.setName("processPayment");
    processPayment.setLoc(80);
    processPayment.setCyclomaticComplexity(18);
    processPayment.setDeadCode(true);

    ClassInfo paymentService = new ClassInfo();
    paymentService.setFqn("com.example.billing.PaymentService");
    paymentService.setFilePath("src/main/java/com/example/billing/PaymentService.java");
    paymentService.setLoc(310);
    paymentService.setMethods(List.of(processPayment));

    analysisResult.setClasses(List.of(invoiceService, paymentService));

    ReportData data =
        ReportData.builder()
            .runId("run-analysis")
            .projectId("proj-analysis")
            .timestamp(Instant.parse("2026-02-06T03:16:15Z"))
            .analysisResult(analysisResult)
            .totalClassesAnalyzed(2)
            .totalMethodsAnalyzed(3)
            .build();

    MarkdownReportWriter writer = new MarkdownReportWriter();
    String markdown = writer.generateMarkdown(data);

    assertThat(markdown).contains("Analysis Hotspots");
    assertThat(markdown).contains("High Complexity Methods");
    assertThat(markdown).contains("Analyzed Classes");
    assertThat(markdown).contains("Risky Methods");
    assertThat(markdown).contains("Recommendations");
    assertThat(markdown).contains("`com.example.billing.ComplexInvoiceService`");
    assertThat(markdown).contains("calculateInvoice");
    assertThat(markdown).contains("32");
    assertThat(markdown).contains("src/main/java/com/example/billing/ComplexInvoiceService.html");
  }

  @Test
  void generateMarkdown_excludesImplicitDefaultConstructorFromClassHotspots() {
    AnalysisResult analysisResult = new AnalysisResult();

    MethodInfo implicitConstructor = new MethodInfo();
    implicitConstructor.setName("CtorSample");
    implicitConstructor.setSignature("CtorSample()");
    implicitConstructor.setParameterCount(0);
    implicitConstructor.setLoc(0);

    MethodInfo businessMethod = new MethodInfo();
    businessMethod.setName("run");
    businessMethod.setLoc(20);
    businessMethod.setCyclomaticComplexity(6);

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CtorSample");
    classInfo.setFilePath("src/main/java/com/example/CtorSample.java");
    classInfo.setLoc(40);
    classInfo.setMethods(List.of(implicitConstructor, businessMethod));

    analysisResult.setClasses(List.of(classInfo));

    ReportData data =
        ReportData.builder()
            .runId("run-md-ctor")
            .projectId("proj-md-ctor")
            .timestamp(Instant.parse("2026-02-06T03:16:15Z"))
            .analysisResult(analysisResult)
            .totalClassesAnalyzed(1)
            .totalMethodsAnalyzed(1)
            .build();

    MarkdownReportWriter writer = new MarkdownReportWriter();
    String markdown = writer.generateMarkdown(data);

    assertThat(markdown)
        .contains("| [`com.example.CtorSample`](src/main/java/com/example/CtorSample.html) | 1 |");
  }

  @Test
  void generateMarkdown_handlesMinuteDurationFormatting() {
    ReportData data =
        ReportData.builder()
            .runId("run-duration")
            .projectId("proj-duration")
            .timestamp(Instant.parse("2024-01-02T03:04:05Z"))
            .durationMs(61000)
            .build();

    MarkdownReportWriter writer = new MarkdownReportWriter();
    String markdown = writer.generateMarkdown(data);

    assertThat(markdown)
        .contains(MessageSource.getMessage("report.duration.minutes_seconds", 1, 1));
  }

  @Test
  void writeReport_throwsReportWriteException_whenOutputPathIsNotDirectory() throws Exception {
    Path blockedPath = tempDir.resolve("blocked");
    Files.writeString(blockedPath, "not-a-directory");

    MarkdownReportWriter writer = new MarkdownReportWriter(blockedPath, "report.md");

    assertThatThrownBy(() -> writer.writeReport(buildReportData(), new Config()))
        .isInstanceOf(ReportWriteException.class);
  }

  @Test
  void getFormat_returnsMarkdown() {
    MarkdownReportWriter writer = new MarkdownReportWriter();
    assertThat(writer.getFormat()).isEqualTo(ReportFormat.MARKDOWN);
  }

  private ReportData buildReportData() {
    GenerationSummary summary = new GenerationSummary();
    summary.setProjectId("proj-1");
    summary.setTotalTasks(3);
    summary.setSucceeded(1);
    summary.setFailed(1);
    summary.setSkipped(1);
    summary.setSuccessRate(0.33);

    GenerationTaskResult failure = new GenerationTaskResult();
    failure.setTaskId("task-1");
    failure.setClassFqn("com.example.Foo");
    failure.setMethodName("doWork");
    failure.setStatus(GenerationTaskResult.Status.FAILURE);
    failure.setErrorMessage("boom | oops\nline2");
    failure.setGenerationResult(
        GenerationResult.failure()
            .errorMessage("boom")
            .llmModelUsed("gpt-4")
            .tokenUsage(42)
            .build());

    GenerationTaskResult success = new GenerationTaskResult();
    success.setTaskId("task-2");
    success.setClassFqn("com.example.Bar");
    success.setMethodName("run");
    success.setStatus(GenerationTaskResult.Status.SUCCESS);
    success.setGenerationResult(
        GenerationResult.success().llmModelUsed("gpt-4").tokenUsage(42).build());

    GenerationTaskResult skipped = new GenerationTaskResult();
    skipped.setTaskId("task-3");
    skipped.setStatus(GenerationTaskResult.Status.SKIPPED);
    skipped.setErrorMessage("not selected");

    return ReportData.builder()
        .runId("run-1")
        .projectId("proj-1")
        .timestamp(Instant.parse("2024-01-02T03:04:05Z"))
        .summary(summary)
        .analysisHumanSummary("このクラスは依存関係が複雑です。")
        .lineCoverage(0.5)
        .branchCoverage(0.25)
        .taskResults(List.of(failure, success, skipped))
        .durationMs(1500)
        .errors(List.of("error one"))
        .warnings(List.of("warning one"))
        .build();
  }
}
