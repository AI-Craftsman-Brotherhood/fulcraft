package com.craftsmanbro.fulcraft.plugins.reporting.core.service.aggregator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportFileAccessor;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class TaskProcessorTest {

  private final TaskProcessor processor = new TaskProcessor();

  @Test
  void determineTestClassNameUsesTaskOverride() {
    TaskRecord task = new TaskRecord();
    task.setTestClassName("CustomTestName");

    String name = processor.determineTestClassName("com.example.Foo", task);

    assertEquals("CustomTestName", name);
  }

  @Test
  void determineTestClassNameGeneratesFromClassAndMethod() {
    TaskRecord task = new TaskRecord();
    task.setMethodName("do_work");

    String name = processor.determineTestClassName("com.example.Outer.Inner", task);

    assertEquals("Outer_Inner_do_workGeneratedTest", name);
  }

  @Test
  void determineTestClassNameUsesUnknownMethodWhenMissing() {
    TaskRecord task = new TaskRecord();

    String name = processor.determineTestClassName("com.example.Foo", task);

    assertEquals("Foo_UnknownMethodGeneratedTest", name);
  }

  @Test
  void determineTestClassNameSanitizesMethodNameCharacters() {
    TaskRecord task = new TaskRecord();
    task.setMethodName("do-work(v1)");

    String name = processor.determineTestClassName("com.example.Foo", task);

    assertEquals("Foo_do_work_v1_GeneratedTest", name);
  }

  @Test
  void determineTestClassNameFallsBackWhenOverrideBlankAndClassFqnMissing() {
    TaskRecord task = new TaskRecord();
    task.setTestClassName("  ");
    task.setMethodName("a-b()");

    String name = processor.determineTestClassName(null, task);

    assertEquals("_a_b__GeneratedTest", name);
  }

  @Test
  void determineTestClassNameUsesLastSegmentWhenLeadingSegmentIsUppercase() {
    TaskRecord task = new TaskRecord();
    task.setMethodName("run");

    String name = processor.determineTestClassName("Foo.Bar", task);

    assertEquals("Bar_runGeneratedTest", name);
  }

  @Test
  void determineTestClassNameHandlesBlankClassFqn() {
    TaskRecord task = new TaskRecord();

    String name = processor.determineTestClassName("   ", task);

    assertEquals("_UnknownMethodGeneratedTest", name);
  }

  @Test
  void determineBaseTestNameHandlesFqnTestClassName() {
    String base =
        processor.determineBaseTestName("com.example.Foo", "com.example.Foo_barGeneratedTest");

    assertEquals("com.example.Foo_barGeneratedTest", base);
  }

  @Test
  void determineBaseTestNameReturnsClassFqnWhenNull() {
    String base = processor.determineBaseTestName("com.example.Foo", null);

    assertEquals("com.example.Foo", base);
  }

  @Test
  void determineBaseTestNameReturnsClassFqnWhenBlank() {
    String base = processor.determineBaseTestName("com.example.Foo", "  ");

    assertEquals("com.example.Foo", base);
  }

  @Test
  void determineBaseTestNamePrefixesPackageForSimpleName() {
    String base = processor.determineBaseTestName("com.example.Foo", "Foo_barGeneratedTest");

    assertEquals("com.example.Foo_barGeneratedTest", base);
  }

  @Test
  void determineBaseTestNameReturnsSimpleNameWhenClassHasNoPackage() {
    String base = processor.determineBaseTestName("Foo", "Foo_barGeneratedTest");

    assertEquals("Foo_barGeneratedTest", base);
  }

  @Test
  void processTaskMarksTestNotFoundWhenReportFileMissing() {
    TaskRecord task = selectedTask();

    StubReportFileAccessor accessor = new StubReportFileAccessor();
    accessor.reportFile = Optional.of(Path.of("reports", "missing.xml"));
    accessor.reportFileExists = false;
    ReportTaskResult result =
        processor.processTask(task, "run-1", Path.of("reports"), true, true, accessor);

    assertEquals("test_not_found", result.getStatus());
    assertFalse(accessor.parseReportCalled);
  }

  @Test
  void processTaskMarksNotSelectedBeforeReportLookup() {
    TaskRecord task = selectedTask();
    task.setSelected(false);

    StubReportFileAccessor accessor = new StubReportFileAccessor();
    ReportTaskResult result =
        processor.processTask(task, "run-2", Path.of("reports"), false, false, accessor);

    assertEquals("not_selected", result.getStatus());
    assertFalse(accessor.findReportFileCalled);
    assertFalse(accessor.parseReportCalled);
  }

  @Test
  void processTaskMarksBuildFailedWhenReportsDirectoryUnavailable() {
    TaskRecord task = selectedTask();

    StubReportFileAccessor accessor = new StubReportFileAccessor();
    ReportTaskResult result =
        processor.processTask(task, "run-3", Path.of("reports"), false, false, accessor);

    assertEquals("build_failed", result.getStatus());
    assertFalse(accessor.findReportFileCalled);
  }

  @Test
  void processTaskTreatsNullSelectedAsSelectable() {
    TaskRecord task = selectedTask();
    task.setSelected(null);

    StubReportFileAccessor accessor = new StubReportFileAccessor();
    accessor.reportFile = Optional.empty();
    ReportTaskResult result =
        processor.processTask(task, "run-null-selected", Path.of("reports"), true, false, accessor);

    assertEquals("build_failed", result.getStatus());
    assertTrue(accessor.findReportFileCalled);
  }

  @Test
  void processTaskMarksBuildFailedWhenNoReportsArePresent() {
    TaskRecord task = selectedTask();

    StubReportFileAccessor accessor = new StubReportFileAccessor();
    accessor.reportFile = Optional.empty();
    ReportTaskResult result =
        processor.processTask(task, "run-4", Path.of("reports"), true, false, accessor);

    assertEquals("build_failed", result.getStatus());
  }

  @Test
  void processTaskMarksBuildFailedWhenReportParseFails() {
    TaskRecord task = selectedTask();

    StubReportFileAccessor accessor = new StubReportFileAccessor();
    accessor.reportFile = Optional.of(Path.of("reports", "TEST-com.example.Foo.xml"));
    accessor.reportFileExists = true;
    accessor.parseReportReturn = false;

    ReportTaskResult result =
        processor.processTask(task, "run-5", Path.of("reports"), true, true, accessor);

    assertEquals("build_failed", result.getStatus());
    assertTrue(accessor.parseReportCalled);
  }

  @Test
  void processTaskMarksTestFailedWhenParsedResultHasFailures() {
    TaskRecord task = selectedTask();

    StubReportFileAccessor accessor = new StubReportFileAccessor();
    accessor.reportFile = Optional.of(Path.of("reports", "TEST-com.example.Foo.xml"));
    accessor.reportFileExists = true;
    accessor.parseReportReturn = true;
    accessor.parseMutator =
        result -> {
          result.setTestsRun(1);
          result.setTestsFailed(1);
          result.setTestsError(0);
        };

    ReportTaskResult result =
        processor.processTask(task, "run-6", Path.of("reports"), true, true, accessor);

    assertEquals("test_failed", result.getStatus());
  }

  @Test
  void processTaskMarksTestFailedWhenParsedResultHasErrorsOnly() {
    TaskRecord task = selectedTask();

    StubReportFileAccessor accessor = new StubReportFileAccessor();
    accessor.reportFile = Optional.of(Path.of("reports", "TEST-com.example.Foo.xml"));
    accessor.reportFileExists = true;
    accessor.parseReportReturn = true;
    accessor.parseMutator =
        result -> {
          result.setTestsRun(1);
          result.setTestsFailed(0);
          result.setTestsError(1);
        };

    ReportTaskResult result =
        processor.processTask(task, "run-errors", Path.of("reports"), true, true, accessor);

    assertEquals("test_failed", result.getStatus());
  }

  @Test
  void processTaskMarksSuccessWhenParsedResultHasNoFailures() {
    TaskRecord task = selectedTask();

    StubReportFileAccessor accessor = new StubReportFileAccessor();
    accessor.reportFile = Optional.of(Path.of("reports", "TEST-com.example.Foo.xml"));
    accessor.reportFileExists = true;
    accessor.parseReportReturn = true;
    accessor.parseMutator =
        result -> {
          result.setTestsRun(1);
          result.setTestsFailed(0);
          result.setTestsError(0);
        };

    ReportTaskResult result =
        processor.processTask(task, "run-7", Path.of("reports"), true, true, accessor);

    assertEquals("success", result.getStatus());
  }

  private TaskRecord selectedTask() {
    TaskRecord task = new TaskRecord();
    task.setTaskId("t1");
    task.setProjectId("proj");
    task.setClassFqn("com.example.Foo");
    task.setMethodName("bar");
    task.setSelected(true);
    return task;
  }

  private static class StubReportFileAccessor implements ReportFileAccessor {
    private Optional<Path> reportFile = Optional.empty();
    private boolean reportFileExists;
    private boolean parseReportReturn;
    private Consumer<ReportTaskResult> parseMutator = ignored -> {};
    private boolean findReportFileCalled;
    private boolean parseReportCalled;

    @Override
    public Path resolveReportDir(Path projectRoot) {
      throw new UnsupportedOperationException("Not needed for this test");
    }

    @Override
    public boolean isReportsDirectory(Path reportDir) {
      throw new UnsupportedOperationException("Not needed for this test");
    }

    @Override
    public boolean hasAnyReportFile(Path reportDir) {
      throw new UnsupportedOperationException("Not needed for this test");
    }

    @Override
    public Optional<Path> findReportFile(
        Path reportDir, String baseTestName, String testClassName) {
      findReportFileCalled = true;
      return reportFile;
    }

    @Override
    public boolean reportFileExists(Path reportFile) {
      return reportFileExists;
    }

    @Override
    public boolean parseReport(Path reportFile, ReportTaskResult result) {
      parseReportCalled = true;
      parseMutator.accept(result);
      return parseReportReturn;
    }
  }
}
