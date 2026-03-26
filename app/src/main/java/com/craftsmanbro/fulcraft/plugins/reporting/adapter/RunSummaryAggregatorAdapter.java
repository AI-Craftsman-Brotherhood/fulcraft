package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.reporting.core.service.aggregator.RunSummaryAggregator;
import com.craftsmanbro.fulcraft.plugins.reporting.model.DynamicSelectionReport;
import com.craftsmanbro.fulcraft.plugins.reporting.model.RunSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.DefaultTaskEntriesSource;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Adapter that aggregates run summaries from tasks files. */
public class RunSummaryAggregatorAdapter {

  private final TaskEntriesSource entriesSource;

  private final RunSummaryAggregator aggregator;

  public RunSummaryAggregatorAdapter() {
    this(new RunSummaryAggregator(), null);
  }

  public RunSummaryAggregatorAdapter(final RunSummaryAggregator aggregator) {
    this(aggregator, null);
  }

  public RunSummaryAggregatorAdapter(
      final RunSummaryAggregator aggregator, final TaskEntriesSource entriesSource) {
    this.aggregator =
        Objects.requireNonNull(
            aggregator,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "aggregator"));
    this.entriesSource = entriesSource != null ? entriesSource : new DefaultTaskEntriesSource();
  }

  public RunSummary aggregate(
      final Path tasksFile,
      final String runId,
      final boolean isDryRun,
      final DynamicSelectionReport dynamicReport)
      throws IOException {
    Objects.requireNonNull(
        tasksFile,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "tasksFile"));
    Objects.requireNonNull(
        runId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "runId"));
    if (!Files.exists(tasksFile)) {
      throw new IOException(MessageSource.getMessage("report.tasks_file.not_found", tasksFile));
    }
    try (var reader = entriesSource.read(tasksFile)) {
      return aggregator.aggregate(reader, runId, isDryRun, dynamicReport);
    }
  }

  public RunSummary aggregate(
      final BufferedReader reader,
      final String runId,
      final boolean isDryRun,
      final DynamicSelectionReport dynamicReport) {
    Objects.requireNonNull(
        reader,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "reader"));
    Objects.requireNonNull(
        runId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "runId"));
    try (var jsonlReader = entriesSource.readJsonl(reader)) {
      return aggregator.aggregate(jsonlReader, runId, isDryRun, dynamicReport);
    }
  }
}
