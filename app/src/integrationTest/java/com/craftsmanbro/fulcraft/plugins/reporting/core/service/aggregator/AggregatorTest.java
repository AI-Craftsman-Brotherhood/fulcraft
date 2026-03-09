package com.craftsmanbro.fulcraft.plugins.reporting.core.service.aggregator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportFileAccessor;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AggregatorTest {

  @TempDir Path tempDir;

  @Test
  void marksNotSelectedTasksWithoutReadingReports() throws Exception {
    TaskRecord task =
        task(
            "t1", "proj", "com.example.Foo", "src/main/java/com/example/Foo.java", "doIt()", false);

    List<ReportTaskResult> results =
        new Aggregator(
                new com.craftsmanbro.fulcraft.plugins.reporting.adapter.DefaultReportFileAccessor())
            .aggregateResults(List.of(task), tempDir, "run-1");

    assertEquals(1, results.size());
    ReportTaskResult res = results.get(0);
    assertEquals("not_selected", res.getStatus());
    assertEquals("run-1", res.getRunId());
    assertEquals("proj", res.getProjectId());
  }

  @Test
  void reportsSuccessWhenJUnitXmlPresent() throws Exception {
    TaskRecord task =
        task("t2", "proj", "com.example.Foo", "src/main/java/com/example/Foo.java", "bar()", true);
    Path reportDir = Files.createDirectories(tempDir.resolve("build/test-results/test"));
    String baseName = "TEST-com.example.Foo_bar__GeneratedTest.xml";
    writeReport(
        reportDir.resolve(baseName),
        """
            <testsuite tests="1" failures="0" errors="0" skipped="0">
              <testcase classname="com.example.Foo" name="testBar"/>
            </testsuite>
            """);

    List<ReportTaskResult> results =
        new Aggregator(
                new com.craftsmanbro.fulcraft.plugins.reporting.adapter.DefaultReportFileAccessor())
            .aggregateResults(List.of(task), tempDir, "run-2");

    assertEquals(1, results.size());
    ReportTaskResult res = results.get(0);
    assertEquals("success", res.getStatus());
    assertEquals(1, res.getTestsRun());
    assertEquals(0, res.getTestsFailed());
    assertEquals(0, res.getTestsError());
    assertEquals(0, res.getTestsSkipped());
    assertNotNull(res.getFailureDetails());
    assertTrue(res.getFailureDetails().isEmpty());
  }

  @Test
  void marksTestNotFoundWhenNoMatchingReport() throws Exception {
    TaskRecord task = task("t3", "proj", null, "org/example/NoReport.java", "missing()", true);
    Path reportDir = Files.createDirectories(tempDir.resolve("build/test-results/test"));
    writeReport(
        reportDir.resolve("TEST-com.example.Other_test__GeneratedTest.xml"),
        """
            <testsuite tests="1" failures="0" errors="0" skipped="0">
              <testcase classname="com.example.Other" name="testOther"/>
            </testsuite>
            """);

    List<ReportTaskResult> results =
        new Aggregator(
                new com.craftsmanbro.fulcraft.plugins.reporting.adapter.DefaultReportFileAccessor())
            .aggregateResults(List.of(task), tempDir, "run-3");

    assertEquals(1, results.size());
    ReportTaskResult res = results.get(0);
    assertEquals("test_not_found", res.getStatus());
    assertEquals(
        "org.example.NoReport", res.getClassFqn(), "Should derive FQN from file_path when missing");
  }

  @Test
  void marksBuildFailedWhenReportCannotBeParsed() throws Exception {
    TaskRecord task =
        task("t4", "proj", "com.example.Bad", "src/main/java/com/example/Bad.java", "baz()", true);
    Path reportDir = Files.createDirectories(tempDir.resolve("build/test-results/test"));
    writeReport(
        reportDir.resolve("TEST-com.example.Bad_baz__GeneratedTest.xml"), "not-xml-content");

    List<ReportTaskResult> results =
        new Aggregator(
                new com.craftsmanbro.fulcraft.plugins.reporting.adapter.DefaultReportFileAccessor())
            .aggregateResults(List.of(task), tempDir, "run-4");

    assertEquals(1, results.size());
    assertEquals("build_failed", results.get(0).getStatus());
  }

  @Test
  void marksBuildFailedWhenReportDirectoryMissing() {
    TaskRecord task =
        task(
            "t5",
            "proj",
            "com.example.NoReports",
            "src/main/java/com/example/NoReports.java",
            "doIt()",
            true);

    List<ReportTaskResult> results =
        new Aggregator(
                new com.craftsmanbro.fulcraft.plugins.reporting.adapter.DefaultReportFileAccessor())
            .aggregateResults(List.of(task), tempDir, "run-5");

    assertEquals(1, results.size());
    assertEquals("build_failed", results.get(0).getStatus());
  }

  @Test
  void findsReportFileWithSuffixPattern() throws Exception {
    TaskRecord task =
        task("t6", "proj", "com.example.Foo", "src/main/java/com/example/Foo.java", "bar()", true);
    Path reportDir = Files.createDirectories(tempDir.resolve("build/test-results/test"));
    writeReport(
        reportDir.resolve("TEST-com.example.Foo_bar__GeneratedTest_1.xml"),
        """
            <testsuite tests="1" failures="0" errors="0" skipped="0">
              <testcase classname="com.example.Foo" name="testBar"/>
            </testsuite>
            """);

    List<ReportTaskResult> results =
        new Aggregator(
                new com.craftsmanbro.fulcraft.plugins.reporting.adapter.DefaultReportFileAccessor())
            .aggregateResults(List.of(task), tempDir, "run-6");

    assertEquals(1, results.size());
    assertEquals("success", results.get(0).getStatus());
  }

  @Test
  void findsReportFileBySimpleClassNamePattern() throws Exception {
    TaskRecord task =
        task("t7", "proj", "com.example.Foo", "src/main/java/com/example/Foo.java", "bar()", true);
    Path reportDir = Files.createDirectories(tempDir.resolve("build/test-results/test"));
    writeReport(
        reportDir.resolve("TEST-Foo_bar__GeneratedTest.xml"),
        """
            <testsuite tests="1" failures="0" errors="0" skipped="0">
              <testcase classname="com.example.Foo" name="testBar"/>
            </testsuite>
            """);

    List<ReportTaskResult> results =
        new Aggregator(
                new com.craftsmanbro.fulcraft.plugins.reporting.adapter.DefaultReportFileAccessor())
            .aggregateResults(List.of(task), tempDir, "run-7");

    assertEquals(1, results.size());
    assertEquals("success", results.get(0).getStatus());
  }

  @Test
  void findsReportFileWithoutTestPrefixPattern() throws Exception {
    TaskRecord task =
        task("t8", "proj", "com.example.Foo", "src/main/java/com/example/Foo.java", "bar()", true);
    Path reportDir = Files.createDirectories(tempDir.resolve("build/test-results/test"));
    writeReport(
        reportDir.resolve("com.example.Foo_bar__GeneratedTest.xml"),
        """
            <testsuite tests="1" failures="0" errors="0" skipped="0">
              <testcase classname="com.example.Foo" name="testBar"/>
            </testsuite>
            """);

    List<ReportTaskResult> results =
        new Aggregator(
                new com.craftsmanbro.fulcraft.plugins.reporting.adapter.DefaultReportFileAccessor())
            .aggregateResults(List.of(task), tempDir, "run-8");

    assertEquals(1, results.size());
    assertEquals("success", results.get(0).getStatus());
  }

  @Test
  void fallsBackToMavenSurefireReportsWhenGradleDirMissing() throws Exception {
    TaskRecord task =
        task("t9", "proj", "com.example.Foo", "src/main/java/com/example/Foo.java", "bar()", true);
    Path reportDir = Files.createDirectories(tempDir.resolve("target/surefire-reports"));
    writeReport(
        reportDir.resolve("TEST-com.example.Foo_bar__GeneratedTest.xml"),
        """
            <testsuite tests="1" failures="0" errors="0" skipped="0">
              <testcase classname="com.example.Foo" name="testBar"/>
            </testsuite>
            """);

    List<ReportTaskResult> results =
        new Aggregator(
                new com.craftsmanbro.fulcraft.plugins.reporting.adapter.DefaultReportFileAccessor())
            .aggregateResults(List.of(task), tempDir, "run-9");

    assertEquals(1, results.size());
    assertEquals("success", results.get(0).getStatus());
  }

  @Test
  void capturesFailureDetailsFromJUnitXml() throws Exception {
    TaskRecord task =
        task("t10", "proj", "com.example.Foo", "src/main/java/com/example/Foo.java", "bar()", true);
    Path reportDir = Files.createDirectories(tempDir.resolve("build/test-results/test"));
    writeReport(
        reportDir.resolve("TEST-com.example.Foo_bar__GeneratedTest.xml"),
        """
                        <testsuite tests="3" failures="1" errors="1" skipped="0">
                          <testcase classname="com.example.Foo" name="testOk"/>
                          <testcase classname="com.example.Foo" name="testFail">
                            <failure message="boom">line1
            line2</failure>
                          </testcase>
                          <testcase classname="com.example.Foo" name="testError">
                            <error>errline1
            errline2</error>
                          </testcase>
                        </testsuite>
                        """);

    List<ReportTaskResult> results =
        new Aggregator(
                new com.craftsmanbro.fulcraft.plugins.reporting.adapter.DefaultReportFileAccessor())
            .aggregateResults(List.of(task), tempDir, "run-10");

    assertEquals(1, results.size());

    ReportTaskResult res = results.get(0);
    assertEquals("test_failed", res.getStatus());
    assertEquals(3, res.getTestsRun());
    assertEquals(1, res.getTestsFailed());
    assertEquals(1, res.getTestsError());
    assertNotNull(res.getFailureDetails());
    assertEquals(2, res.getFailureDetails().size());
    assertEquals("testFail", res.getFailureDetails().get(0).getTestMethod());
    assertEquals("boom", res.getFailureDetails().get(0).getMessageHead());
    assertEquals("testError", res.getFailureDetails().get(1).getTestMethod());
  }

  @Test
  void delegatesTaskProcessingWithResolvedReportState() {
    TaskRecord task1 =
        task("t11", "proj", "com.example.Foo", "src/main/java/com/example/Foo.java", "a()", true);
    TaskRecord task2 =
        task("t12", "proj", "com.example.Bar", "src/main/java/com/example/Bar.java", "b()", true);
    Path resolvedReportDir = tempDir.resolve("resolved-reports");

    StubReportFileAccessor accessor = new StubReportFileAccessor(resolvedReportDir, true, false);
    CapturingTaskProcessor taskProcessor = new CapturingTaskProcessor();

    List<ReportTaskResult> results =
        new Aggregator(accessor, taskProcessor)
            .aggregateResults(List.of(task1, task2), tempDir, "run-delegate");

    assertEquals(2, results.size());
    assertEquals("captured", results.get(0).getStatus());
    assertEquals("captured", results.get(1).getStatus());
    assertEquals(2, taskProcessor.calls.size());

    Call first = taskProcessor.calls.get(0);
    assertEquals("t11", first.taskId());
    assertEquals("run-delegate", first.runId());
    assertEquals(resolvedReportDir, first.reportDir());
    assertTrue(first.reportsAvailable());
    assertFalse(first.reportsPresent());
  }

  @Test
  void constructorsRejectNullDependencies() {
    assertThrows(NullPointerException.class, () -> new Aggregator(null));

    StubReportFileAccessor accessor =
        new StubReportFileAccessor(tempDir.resolve("reports"), true, true);
    assertThrows(NullPointerException.class, () -> new Aggregator(accessor, null));
  }

  @Test
  void aggregateResultsRejectsNullInputs() {
    Aggregator aggregator =
        new Aggregator(
            new StubReportFileAccessor(tempDir.resolve("reports"), true, true),
            new CapturingTaskProcessor());
    TaskRecord task =
        task("t13", "proj", "com.example.Baz", "src/main/java/com/example/Baz.java", "c()", true);

    assertThrows(
        NullPointerException.class, () -> aggregator.aggregateResults(null, tempDir, "run"));
    assertThrows(
        NullPointerException.class, () -> aggregator.aggregateResults(List.of(task), null, "run"));
    assertThrows(
        NullPointerException.class,
        () -> aggregator.aggregateResults(List.of(task), tempDir, null));
  }

  private TaskRecord task(
      String id,
      String projectId,
      String classFqn,
      String filePath,
      String methodName,
      Boolean selected) {
    TaskRecord t = new TaskRecord();
    t.setTaskId(id);
    t.setProjectId(projectId);
    t.setClassFqn(classFqn);
    t.setFilePath(filePath);
    t.setMethodName(methodName);
    t.setSelected(selected);
    return t;
  }

  private void writeReport(Path path, String content) throws Exception {
    Files.writeString(path, content, StandardCharsets.UTF_8);
  }

  private static class StubReportFileAccessor implements ReportFileAccessor {
    private final Path reportDir;
    private final boolean reportsDirectory;
    private final boolean anyReportFile;

    private StubReportFileAccessor(
        Path reportDir, boolean reportsDirectory, boolean anyReportFile) {
      this.reportDir = reportDir;
      this.reportsDirectory = reportsDirectory;
      this.anyReportFile = anyReportFile;
    }

    @Override
    public Path resolveReportDir(Path projectRoot) {
      return reportDir;
    }

    @Override
    public boolean isReportsDirectory(Path reportDir) {
      return reportsDirectory;
    }

    @Override
    public boolean hasAnyReportFile(Path reportDir) {
      return anyReportFile;
    }

    @Override
    public Optional<Path> findReportFile(
        Path reportDir, String baseTestName, String testClassName) {
      return Optional.empty();
    }

    @Override
    public boolean reportFileExists(Path reportFile) {
      return false;
    }

    @Override
    public boolean parseReport(Path reportFile, ReportTaskResult result) {
      return false;
    }
  }

  private static class CapturingTaskProcessor extends TaskProcessor {
    private final List<Call> calls = new ArrayList<>();

    @Override
    public ReportTaskResult processTask(
        TaskRecord task,
        String runId,
        Path reportDir,
        boolean reportsAvailable,
        boolean reportsPresent,
        ReportFileAccessor reportFileAccessor) {
      calls.add(
          new Call(
              task.getTaskId(),
              runId,
              reportDir,
              reportsAvailable,
              reportsPresent,
              reportFileAccessor));
      ReportTaskResult result = new ReportTaskResult();
      result.setTaskId(task.getTaskId());
      result.setRunId(runId);
      result.setStatus("captured");
      return result;
    }
  }

  private record Call(
      String taskId,
      String runId,
      Path reportDir,
      boolean reportsAvailable,
      boolean reportsPresent,
      ReportFileAccessor reportFileAccessor) {}
}
