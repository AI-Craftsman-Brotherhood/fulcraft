package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.reporting.core.service.reason.ReasonAggregator;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.DefaultTaskEntriesSource;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntriesSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Adapter that aggregates reason summaries from tasks files. */
public class ReasonAggregatorAdapter {

  private final TaskEntriesSource entriesSource;

  private final ReasonAggregator aggregator;

  public ReasonAggregatorAdapter() {
    this(new ReasonAggregator(), null);
  }

  public ReasonAggregatorAdapter(final ReasonAggregator aggregator) {
    this(aggregator, null);
  }

  public ReasonAggregatorAdapter(
      final ReasonAggregator aggregator, final TaskEntriesSource entriesSource) {
    this.aggregator =
        Objects.requireNonNull(
            aggregator,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "aggregator"));
    this.entriesSource = entriesSource != null ? entriesSource : new DefaultTaskEntriesSource();
  }

  public ReasonSummary aggregate(final Path tasksFile, final String runId, final boolean isDryRun)
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
      return aggregator.aggregate(reader, runId, isDryRun);
    }
  }

  public ReasonSummary aggregate(
      final BufferedReader reader, final String runId, final boolean isDryRun) {
    Objects.requireNonNull(
        reader,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "reader"));
    Objects.requireNonNull(
        runId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "runId"));
    try (var jsonlReader = entriesSource.readJsonl(reader)) {
      return aggregator.aggregate(jsonlReader, runId, isDryRun);
    }
  }
}
