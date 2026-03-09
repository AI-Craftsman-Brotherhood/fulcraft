package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stores historical run summaries for trend analysis.
 *
 * <p>This class maintains a list of past execution results, enabling comparison between runs and
 * tracking improvement over time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportHistory {

  private List<HistoricalRun> runs = new ArrayList<>();

  /** A summary of a single historical run. */
  public record HistoricalRun(
      String runId,
      long timestamp,
      int totalTasks,
      int succeeded,
      int failed,
      int skipped,
      double successRate) {}

  /** Creates an empty ReportHistory. */
  public ReportHistory() {}

  /** Creates a ReportHistory with the given runs. */
  public ReportHistory(final List<HistoricalRun> runs) {
    this.runs =
        new ArrayList<>(
            Objects.requireNonNull(
                runs,
                com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                    "report.common.error.argument_null", "runs must not be null")));
  }

  /** Gets the list of historical runs. */
  public List<HistoricalRun> getRuns() {
    return List.copyOf(runs);
  }

  /** Sets the list of historical runs. */
  public void setRuns(final List<HistoricalRun> runs) {
    this.runs =
        new ArrayList<>(
            Objects.requireNonNull(
                runs,
                com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                    "report.common.error.argument_null", "runs must not be null")));
  }

  /** Adds a run to the history. */
  public void addRun(final HistoricalRun run) {
    this.runs.add(
        Objects.requireNonNull(
            run,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "run must not be null")));
  }

  /** Gets the most recent run, or null if empty. */
  @JsonIgnore
  public HistoricalRun getLatestRun() {
    if (runs.isEmpty()) {
      return null;
    }
    return runs.get(runs.size() - 1);
  }

  /** Gets the count of historical runs. */
  public int size() {
    return runs.size();
  }

  /** Checks if there are no historical runs. */
  @JsonIgnore
  public boolean isEmpty() {
    return runs.isEmpty();
  }

  /**
   * Creates a HistoricalRun from a GenerationSummary.
   *
   * @param summary the generation summary to convert
   * @return a new HistoricalRun
   */
  public static HistoricalRun fromSummary(final GenerationSummary summary) {
    Objects.requireNonNull(
        summary,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "summary must not be null"));
    final int totalProcessed = Math.max(0, summary.getTotalTasks() - summary.getSkipped());
    final Double reportedSuccessRate = summary.getSuccessRate();
    final double successRate;
    if (reportedSuccessRate != null) {
      successRate = reportedSuccessRate;
    } else if (totalProcessed > 0) {
      successRate = (double) summary.getSucceeded() / totalProcessed;
    } else {
      successRate = 0.0;
    }
    return new HistoricalRun(
        summary.getRunId(),
        summary.getTimestamp(),
        summary.getTotalTasks(),
        summary.getSucceeded(),
        summary.getFailed(),
        summary.getSkipped(),
        successRate);
  }
}
