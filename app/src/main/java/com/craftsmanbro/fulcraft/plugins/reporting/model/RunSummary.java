package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary of a single run (tool execution) with aggregated quality and cost metrics.
 *
 * <p>Designed for streaming aggregation - counters are incremented during JSONL processing and
 * rates are computed on demand.
 */
public class RunSummary {

  // === Identification ===
  @JsonProperty("run_id")
  private String runId;

  @JsonProperty("timestamp")
  private long timestamp;

  @JsonProperty("is_dry_run")
  private boolean isDryRun;

  // === Task Counts ===
  @JsonProperty("total_tasks")
  private int totalTasks;

  @JsonProperty("selected_tasks")
  private int selectedTasks;

  @JsonProperty("executed_tasks")
  private int executedTasks;

  // === Compile/Pass/Fix Counts ===
  @JsonProperty("compile_success_count")
  private int compileSuccessCount;

  @JsonProperty("test_pass_count")
  private int testPassCount;

  @JsonProperty("fix_attempted_count")
  private int fixAttemptedCount;

  @JsonProperty("fix_success_count")
  private int fixSuccessCount;

  // === LLM/Retry Counts ===
  @JsonProperty("total_llm_calls")
  private int totalLlmCalls;

  @JsonProperty("total_retries")
  private int totalRetries;

  @JsonProperty("static_fix_count")
  private int staticFixCount;

  @JsonProperty("runtime_fix_count")
  private int runtimeFixCount;

  // === Selection Impact (Phase 4) ===
  @JsonProperty("would_be_excluded_count")
  private int wouldBeExcludedCount;

  @JsonProperty("excluded_count")
  private int excludedCount;

  // === Quality Gate Metrics ===
  @JsonProperty("coverage")
  private CoverageSummary coverage;

  @JsonProperty("static_analysis")
  private StaticAnalysisSummary staticAnalysis;

  // === Getters ===
  public String getRunId() {
    return runId;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public boolean isDryRun() {
    return isDryRun;
  }

  public int getTotalTasks() {
    return totalTasks;
  }

  public int getSelectedTasks() {
    return selectedTasks;
  }

  public int getExecutedTasks() {
    return executedTasks;
  }

  public int getCompileSuccessCount() {
    return compileSuccessCount;
  }

  public int getTestPassCount() {
    return testPassCount;
  }

  public int getFixAttemptedCount() {
    return fixAttemptedCount;
  }

  public int getFixSuccessCount() {
    return fixSuccessCount;
  }

  public int getTotalLlmCalls() {
    return totalLlmCalls;
  }

  public int getTotalRetries() {
    return totalRetries;
  }

  public int getStaticFixCount() {
    return staticFixCount;
  }

  public int getRuntimeFixCount() {
    return runtimeFixCount;
  }

  public int getWouldBeExcludedCount() {
    return wouldBeExcludedCount;
  }

  public int getExcludedCount() {
    return excludedCount;
  }

  public CoverageSummary getCoverage() {
    return coverage;
  }

  public StaticAnalysisSummary getStaticAnalysis() {
    return staticAnalysis;
  }

  // === Setters ===
  public void setRunId(final String runId) {
    this.runId = runId;
  }

  public void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  public void setDryRun(final boolean dryRun) {
    isDryRun = dryRun;
  }

  public void setTotalTasks(final int totalTasks) {
    this.totalTasks = totalTasks;
  }

  public void setSelectedTasks(final int selectedTasks) {
    this.selectedTasks = selectedTasks;
  }

  public void setExecutedTasks(final int executedTasks) {
    this.executedTasks = executedTasks;
  }

  public void setCompileSuccessCount(final int compileSuccessCount) {
    this.compileSuccessCount = compileSuccessCount;
  }

  public void setTestPassCount(final int testPassCount) {
    this.testPassCount = testPassCount;
  }

  public void setFixAttemptedCount(final int fixAttemptedCount) {
    this.fixAttemptedCount = fixAttemptedCount;
  }

  public void setFixSuccessCount(final int fixSuccessCount) {
    this.fixSuccessCount = fixSuccessCount;
  }

  public void setTotalLlmCalls(final int totalLlmCalls) {
    this.totalLlmCalls = totalLlmCalls;
  }

  public void setTotalRetries(final int totalRetries) {
    this.totalRetries = totalRetries;
  }

  public void setStaticFixCount(final int staticFixCount) {
    this.staticFixCount = staticFixCount;
  }

  public void setRuntimeFixCount(final int runtimeFixCount) {
    this.runtimeFixCount = runtimeFixCount;
  }

  public void setWouldBeExcludedCount(final int wouldBeExcludedCount) {
    this.wouldBeExcludedCount = wouldBeExcludedCount;
  }

  public void setExcludedCount(final int excludedCount) {
    this.excludedCount = excludedCount;
  }

  public void setCoverage(final CoverageSummary coverage) {
    this.coverage = coverage;
  }

  public void setStaticAnalysis(final StaticAnalysisSummary staticAnalysis) {
    this.staticAnalysis = staticAnalysis;
  }

  // === Computed Rates (JSON serialized for output) ===
  /** Compile success rate: compileSuccessCount / executedTasks */
  @JsonProperty("compile_rate")
  public double getCompileRate() {
    return executedTasks == 0 ? 0.0 : (double) compileSuccessCount / executedTasks;
  }

  /** Test pass rate: testPassCount / executedTasks */
  @JsonProperty("pass_rate")
  public double getPassRate() {
    return executedTasks == 0 ? 0.0 : (double) testPassCount / executedTasks;
  }

  /** Fix success rate: fixSuccessCount / fixAttemptedCount */
  @JsonProperty("fix_success_rate")
  public double getFixSuccessRate() {
    return fixAttemptedCount == 0 ? 0.0 : (double) fixSuccessCount / fixAttemptedCount;
  }

  /** Average LLM calls per task */
  @JsonProperty("llm_calls_per_task")
  public double getLlmCallsPerTask() {
    return executedTasks == 0 ? 0.0 : (double) totalLlmCalls / executedTasks;
  }

  /** LLM calls per successful task */
  @JsonProperty("llm_calls_per_success")
  public double getLlmCallsPerSuccess() {
    return testPassCount == 0 ? 0.0 : (double) totalLlmCalls / testPassCount;
  }

  /** Average retries per task */
  @JsonProperty("retries_per_task")
  public double getRetriesPerTask() {
    return executedTasks == 0 ? 0.0 : (double) totalRetries / executedTasks;
  }

  /** Would-be-excluded rate (dry-run mode): wouldBeExcludedCount / totalTasks */
  @JsonProperty("would_be_excluded_rate")
  public double getWouldBeExcludedRate() {
    return totalTasks == 0 ? 0.0 : (double) wouldBeExcludedCount / totalTasks;
  }

  /** Excluded rate (enforce mode): excludedCount / totalTasks */
  @JsonProperty("excluded_rate")
  public double getExcludedRate() {
    return totalTasks == 0 ? 0.0 : (double) excludedCount / totalTasks;
  }

  // === Increment Methods for Streaming Aggregation ===
  public void incrementTotalTasks() {
    this.totalTasks++;
  }

  public void incrementSelectedTasks() {
    this.selectedTasks++;
  }

  public void incrementExecutedTasks() {
    this.executedTasks++;
  }

  public void incrementCompileSuccessCount() {
    this.compileSuccessCount++;
  }

  public void incrementTestPassCount() {
    this.testPassCount++;
  }

  public void incrementFixAttemptedCount() {
    this.fixAttemptedCount++;
  }

  public void incrementFixSuccessCount() {
    this.fixSuccessCount++;
  }

  public void addLlmCalls(final int count) {
    this.totalLlmCalls += count;
  }

  public void addRetries(final int count) {
    this.totalRetries += count;
  }

  public void addStaticFixCount(final int count) {
    this.staticFixCount += count;
  }

  public void addRuntimeFixCount(final int count) {
    this.runtimeFixCount += count;
  }

  public void incrementWouldBeExcludedCount() {
    this.wouldBeExcludedCount++;
  }

  public void incrementExcludedCount() {
    this.excludedCount++;
  }
}
