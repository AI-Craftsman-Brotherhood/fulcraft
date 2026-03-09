package com.craftsmanbro.fulcraft.plugins.reporting.core.service.aggregator;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.DynamicSelectionReport;
import com.craftsmanbro.fulcraft.plugins.reporting.model.RunSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntry;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregates run-level summary metrics from tasks files using streaming processing.
 *
 * <p>Processes the JSONL file line-by-line without loading all tasks into memory. Note: This
 * aggregator processes task definitions (plan) only. Execution results are aggregated separately
 * from JUnit XML reports.
 */
public class RunSummaryAggregator {

  /**
   * Aggregate metrics from task entries (I/O-free).
   *
   * @param entries task entries (plan only)
   * @param runId Run identifier
   * @param isDryRun Whether this was a dry-run execution
   * @param dynamicReport Optional DynamicSelectionReport for selection impact metrics
   * @return Aggregated RunSummary
   */
  public RunSummary aggregate(
      final Iterable<TaskEntry> entries,
      final String runId,
      final boolean isDryRun,
      final DynamicSelectionReport dynamicReport) {
    Objects.requireNonNull(
        entries,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "entries"));
    Objects.requireNonNull(
        runId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "runId"));
    final RunSummary summary = new RunSummary();
    summary.setRunId(runId);
    summary.setTimestamp(System.currentTimeMillis());
    summary.setDryRun(isDryRun);
    final RunSummary taskSummary = new RunSummary();
    final Set<String> seenTasks = new HashSet<>();
    for (final TaskEntry entry : entries) {
      if (entry.hasTask()) {
        final TaskRecord task = entry.task();
        final String key = buildTaskKey(task);
        if (seenTasks.add(key)) {
          processTaskRecord(task, taskSummary, isDryRun);
        }
      }
    }
    summary.setTotalTasks(taskSummary.getTotalTasks());
    summary.setSelectedTasks(taskSummary.getSelectedTasks());
    if (dynamicReport != null) {
      summary.setWouldBeExcludedCount(dynamicReport.getWouldBeExcludedCount());
      summary.setExcludedCount(dynamicReport.getExcludedCount());
    } else {
      summary.setWouldBeExcludedCount(taskSummary.getWouldBeExcludedCount());
      summary.setExcludedCount(taskSummary.getExcludedCount());
    }
    final String compileRatePct =
        String.format(java.util.Locale.ROOT, "%.2f", summary.getCompileRate() * 100);
    final String passRatePct =
        String.format(java.util.Locale.ROOT, "%.2f", summary.getPassRate() * 100);
    Logger.info(
        MessageSource.getMessage(
            "report.run_summary.complete",
            summary.getTotalTasks(),
            summary.getExecutedTasks(),
            compileRatePct,
            passRatePct));
    return summary;
  }

  private String buildTaskKey(final TaskRecord task) {
    if (task.getTaskId() != null && !task.getTaskId().isBlank()) {
      return task.getTaskId();
    }
    final String classPart = task.getClassFqn() != null ? task.getClassFqn() : "unknown";
    final String methodPart = task.getMethodName() != null ? task.getMethodName() : "unknown";
    return (classPart + "#" + methodPart).toLowerCase(java.util.Locale.ROOT);
  }

  private void processTaskRecord(
      final TaskRecord task, final RunSummary summary, final boolean isDryRun) {
    summary.incrementTotalTasks();
    final Boolean selected = task.getSelected();
    if (Boolean.TRUE.equals(selected)) {
      summary.incrementSelectedTasks();
    }
    if (isDryRun) {
      if (checkWouldBeExcluded(task)) {
        summary.incrementWouldBeExcludedCount();
      }
    } else {
      if (checkExcluded(task)) {
        summary.incrementExcludedCount();
      }
    }
  }

  private boolean checkWouldBeExcluded(final TaskRecord task) {
    final Map<String, Object> breakdown = task.getFeasibilityBreakdown();
    if (breakdown == null) {
      return false;
    }
    final Object shadowScore = breakdown.get("shadow_score");
    if (shadowScore instanceof Number n && n.doubleValue() <= 0.0) {
      return true;
    }
    if (hasDynamicPenalty(breakdown)) {
      return true;
    }
    final Object dynamicReasons = breakdown.get("dynamic_reasons");
    if (dynamicReasons instanceof java.util.List<?> list) {
      for (final Object item : list) {
        if (item instanceof String s && s.startsWith("SKIP:")) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean checkExcluded(final TaskRecord task) {
    final Map<String, Object> breakdown = task.getFeasibilityBreakdown();
    if (breakdown != null && hasDynamicPenalty(breakdown)) {
      return true;
    }
    final Boolean selected = task.getSelected();
    if (Boolean.FALSE.equals(selected) && task.getExclusionReason() != null) {
      final String reason = task.getExclusionReason();
      if (reason.contains("dynamic") || reason.contains("confidence")) {
        return true;
      }
    }
    final Double score = task.getFeasibilityScore();
    return score != null && score <= 0.0;
  }

  private boolean hasDynamicPenalty(final Map<String, Object> breakdown) {
    final Object penalties = breakdown.get("penalties");
    if (penalties instanceof Map<?, ?> penaltiesMap) {
      final Object value = penaltiesMap.get("DYNAMIC_REASONS");
      if (value instanceof Number n) {
        return n.doubleValue() > 0.0;
      }
    }
    return false;
  }
}
