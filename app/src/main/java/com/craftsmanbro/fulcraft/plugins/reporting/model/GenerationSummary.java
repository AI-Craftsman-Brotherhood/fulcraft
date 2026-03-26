package com.craftsmanbro.fulcraft.plugins.reporting.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Feature-layer neutral generation summary representation. */
public class GenerationSummary {

  private String projectId;

  private String runId;

  private long timestamp;

  private int totalTasks;

  private int succeeded;

  private int failed;

  private int skipped;

  private long durationMs;

  private Double successRate;

  private Double successRateDelta;

  private Map<String, Integer> errorCategoryCounts;

  private Double lineCoverage;

  private Double branchCoverage;

  private List<GenerationTaskResult> details = new ArrayList<>();

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(final String projectId) {
    this.projectId = projectId;
  }

  public String getRunId() {
    return runId;
  }

  public void setRunId(final String runId) {
    this.runId = runId;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  public int getTotalTasks() {
    return totalTasks;
  }

  public void setTotalTasks(final int totalTasks) {
    this.totalTasks = totalTasks;
  }

  public int getSucceeded() {
    return succeeded;
  }

  public void setSucceeded(final int succeeded) {
    this.succeeded = succeeded;
  }

  public int getFailed() {
    return failed;
  }

  public void setFailed(final int failed) {
    this.failed = failed;
  }

  public int getSkipped() {
    return skipped;
  }

  public void setSkipped(final int skipped) {
    this.skipped = skipped;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(final long durationMs) {
    this.durationMs = durationMs;
  }

  public Double getSuccessRate() {
    return successRate;
  }

  public void setSuccessRate(final Double successRate) {
    this.successRate = successRate;
  }

  public Double getSuccessRateDelta() {
    return successRateDelta;
  }

  public void setSuccessRateDelta(final Double successRateDelta) {
    this.successRateDelta = successRateDelta;
  }

  public Map<String, Integer> getErrorCategoryCounts() {
    if (errorCategoryCounts == null) {
      return Collections.emptyMap();
    }
    return new TreeMap<>(errorCategoryCounts);
  }

  public void setErrorCategoryCounts(final Map<String, Integer> errorCategoryCounts) {
    if (errorCategoryCounts == null) {
      this.errorCategoryCounts = null;
      return;
    }
    this.errorCategoryCounts = new TreeMap<>(errorCategoryCounts);
  }

  public Double getLineCoverage() {
    return lineCoverage;
  }

  public void setLineCoverage(final Double lineCoverage) {
    this.lineCoverage = lineCoverage;
  }

  public Double getBranchCoverage() {
    return branchCoverage;
  }

  public void setBranchCoverage(final Double branchCoverage) {
    this.branchCoverage = branchCoverage;
  }

  public List<GenerationTaskResult> getDetails() {
    return Collections.unmodifiableList(details);
  }

  public void setDetails(final List<GenerationTaskResult> details) {
    this.details =
        new ArrayList<>(
            Objects.requireNonNull(
                details,
                com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                    "report.common.error.argument_null", "details")));
  }

  public void addDetail(final GenerationTaskResult detail) {
    this.details.add(
        Objects.requireNonNull(
            detail,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "detail")));
  }
}
