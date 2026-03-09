package com.craftsmanbro.fulcraft.plugins.reporting.core.service.aggregator;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportFileAccessor;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates JUnit XML results for generated tests.
 *
 * <p>This is a thin orchestrator over the lower-level aggregator helpers (task loading, report
 * discovery, and XML parsing).
 */
public class Aggregator {

  private final ReportFileAccessor reportFileAccessor;

  private final TaskProcessor taskProcessor;

  public Aggregator(final ReportFileAccessor reportFileAccessor) {
    this(reportFileAccessor, new TaskProcessor());
  }

  // Allow injection for testing
  public Aggregator(
      final ReportFileAccessor reportFileAccessor, final TaskProcessor taskProcessor) {
    this.reportFileAccessor =
        Objects.requireNonNull(
            reportFileAccessor,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "reportFileAccessor"));
    this.taskProcessor =
        Objects.requireNonNull(
            taskProcessor,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "taskProcessor"));
  }

  public List<ReportTaskResult> aggregateResults(
      final List<TaskRecord> tasks, final Path projectRoot, final String runId) {
    Objects.requireNonNull(
        tasks,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "tasks"));
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "projectRoot"));
    Objects.requireNonNull(
        runId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "runId"));
    Logger.info(MessageSource.getMessage("report.aggregator.start", runId));
    final var reportDir = reportFileAccessor.resolveReportDir(projectRoot);
    Logger.debug(MessageSource.getMessage("report.aggregator.resolve_reports_dir", projectRoot));
    final var reportsAvailable = reportFileAccessor.isReportsDirectory(reportDir);
    final var reportsPresent = reportsAvailable && reportFileAccessor.hasAnyReportFile(reportDir);
    Logger.info(
        MessageSource.getMessage(
            "report.aggregator.reports_dir", reportDir, reportsAvailable, reportsPresent));
    final var results =
        tasks.stream()
            .map(task -> processTask(task, runId, reportDir, reportsAvailable, reportsPresent))
            .toList();
    Logger.info(MessageSource.getMessage("report.aggregator.completed", results.size()));
    return results;
  }

  protected ReportTaskResult processTask(
      final TaskRecord task,
      final String runId,
      final Path reportDir,
      final boolean reportsAvailable,
      final boolean reportsPresent) {
    return taskProcessor.processTask(
        task, runId, reportDir, reportsAvailable, reportsPresent, reportFileAccessor);
  }
}
