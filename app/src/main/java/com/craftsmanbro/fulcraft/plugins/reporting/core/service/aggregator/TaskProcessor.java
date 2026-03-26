package com.craftsmanbro.fulcraft.plugins.reporting.core.service.aggregator;

import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportFileAccessor;
import com.craftsmanbro.fulcraft.plugins.reporting.core.util.AggregatorUtils;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.nio.file.Path;

/** Helper class for processing individual tasks during aggregation. */
public class TaskProcessor {

  private static final String STATUS_BUILD_FAILED = "build_failed";

  private static final String STATUS_NOT_SELECTED = "not_selected";

  private static final String STATUS_TEST_NOT_FOUND = "test_not_found";

  private static final String STATUS_TEST_FAILED = "test_failed";

  private static final String STATUS_SUCCESS = "success";

  private static final String UNKNOWN_METHOD_NAME = "UnknownMethod";

  private static final String GENERATED_TEST_SUFFIX = "GeneratedTest";

  /** Processes a single task and creates a ReportTaskResult. */
  public ReportTaskResult processTask(
      final TaskRecord task,
      final String runId,
      final Path reportDir,
      final boolean reportsAvailable,
      final boolean reportsPresent,
      final ReportFileAccessor reportFileAccessor) {
    final var result = createBaseResult(task, runId);
    if (task.getSelected() != null && !task.getSelected()) {
      result.setStatus(STATUS_NOT_SELECTED);
      return result;
    }
    if (!reportsAvailable) {
      result.setStatus(STATUS_BUILD_FAILED);
      return result;
    }
    return processWithReports(task, result, reportDir, reportsPresent, reportFileAccessor);
  }

  private ReportTaskResult createBaseResult(final TaskRecord task, final String runId) {
    final var result = new ReportTaskResult();
    result.setRunId(runId);
    result.setTaskId(task.getTaskId());
    result.setProjectId(task.getProjectId());
    result.setClassFqn(AggregatorUtils.safeClassFqn(task));
    result.setMethodName(task.getMethodName());
    return result;
  }

  private ReportTaskResult processWithReports(
      final TaskRecord task,
      final ReportTaskResult result,
      final Path reportDir,
      final boolean reportsPresent,
      final ReportFileAccessor reportFileAccessor) {
    final var testClassName = determineTestClassName(result.getClassFqn(), task);
    final var baseTestName = determineBaseTestName(result.getClassFqn(), testClassName);
    final var reportFileOpt =
        reportFileAccessor.findReportFile(reportDir, baseTestName, testClassName);
    if (reportFileOpt.isEmpty()) {
      result.setStatus(reportsPresent ? STATUS_TEST_NOT_FOUND : STATUS_BUILD_FAILED);
      return result;
    }
    final var reportFile = reportFileOpt.get();
    // Assuming ReportFileFinder finds existing files, but retaining check for
    // safety if implementation varies.
    // Ideally ReportFileFinder signature guarantees existence.
    if (!reportFileAccessor.reportFileExists(reportFile)) {
      result.setStatus(STATUS_TEST_NOT_FOUND);
      return result;
    }
    if (!reportFileAccessor.parseReport(reportFile, result)) {
      result.setStatus(STATUS_BUILD_FAILED);
      return result;
    }
    final boolean hasFailures = result.getTestsFailed() > 0 || result.getTestsError() > 0;
    result.setStatus(hasFailures ? STATUS_TEST_FAILED : STATUS_SUCCESS);
    return result;
  }

  /** Determines the test class name from task information. */
  public String determineTestClassName(final String classFqn, final TaskRecord task) {
    if (task.getTestClassName() != null && !task.getTestClassName().isBlank()) {
      return task.getTestClassName();
    }
    final var className = deriveClassNameBase(classFqn).replace('.', '_');
    final var methodPart =
        task.getMethodName() != null
            ? task.getMethodName().replaceAll("[^A-Za-z0-9]", "_")
            : UNKNOWN_METHOD_NAME;
    return className + "_" + methodPart + GENERATED_TEST_SUFFIX;
  }

  /** Determines the base test name (FQN) from class FQN and test class name. */
  public String determineBaseTestName(final String classFqn, final String testClassName) {
    if (testClassName == null || testClassName.isBlank()) {
      return classFqn;
    }
    if (testClassName.contains(".")) {
      return testClassName;
    }
    final var packageName =
        classFqn.contains(".") ? classFqn.substring(0, classFqn.lastIndexOf('.')) : "";
    return packageName.isEmpty() ? testClassName : packageName + "." + testClassName;
  }

  private String deriveClassNameBase(final String classFqn) {
    if (classFqn == null || classFqn.isBlank()) {
      return "";
    }
    if (!classFqn.contains(".")) {
      return classFqn;
    }
    final String[] parts = classFqn.split("\\.");
    final StringBuilder pkg = new StringBuilder();
    for (int i = 0; i < parts.length - 1; i++) {
      final String part = parts[i];
      if (!part.isEmpty() && Character.isUpperCase(part.charAt(0))) {
        break;
      }
      if (i > 0) {
        pkg.append(".");
      }
      pkg.append(part);
    }
    final String packageName = pkg.toString();
    if (!packageName.isEmpty() && classFqn.startsWith(packageName + ".")) {
      return classFqn.substring(packageName.length() + 1);
    }
    return classFqn.substring(classFqn.lastIndexOf('.') + 1);
  }
}
