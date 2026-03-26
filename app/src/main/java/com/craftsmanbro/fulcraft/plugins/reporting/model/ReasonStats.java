package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Statistics for a single dynamic reason, tracking failure rates and fix outcomes.
 *
 * <p>Mutable builder for streaming aggregation, immutable snapshot via toRecord().
 */
public class ReasonStats {

  @JsonProperty("task_count")
  private int taskCount;

  @JsonProperty("compile_success_count")
  private int compileSuccessCount;

  @JsonProperty("compile_fail_count")
  private int compileFailCount;

  @JsonProperty("test_pass_count")
  private int testPassCount;

  @JsonProperty("test_fail_count")
  private int testFailCount;

  @JsonProperty("fix_attempted_count")
  private int fixAttemptedCount;

  @JsonProperty("fix_success_count")
  private int fixSuccessCount;

  @JsonProperty("would_be_excluded_count")
  private int wouldBeExcludedCount;

  @JsonProperty("excluded_count")
  private int excludedCount;

  // === Increment Methods ===

  public void incrementTaskCount() {
    this.taskCount++;
  }

  public void incrementCompileSuccess() {
    this.compileSuccessCount++;
  }

  public void incrementCompileFail() {
    this.compileFailCount++;
  }

  public void incrementTestPass() {
    this.testPassCount++;
  }

  public void incrementTestFail() {
    this.testFailCount++;
  }

  public void incrementFixAttempted() {
    this.fixAttemptedCount++;
  }

  public void incrementFixSuccess() {
    this.fixSuccessCount++;
  }

  public void incrementWouldBeExcluded() {
    this.wouldBeExcludedCount++;
  }

  public void incrementExcluded() {
    this.excludedCount++;
  }

  // === Getters ===

  public int getTaskCount() {
    return taskCount;
  }

  public int getCompileSuccessCount() {
    return compileSuccessCount;
  }

  public int getCompileFailCount() {
    return compileFailCount;
  }

  public int getTestPassCount() {
    return testPassCount;
  }

  public int getTestFailCount() {
    return testFailCount;
  }

  public int getFixAttemptedCount() {
    return fixAttemptedCount;
  }

  public int getFixSuccessCount() {
    return fixSuccessCount;
  }

  public int getWouldBeExcludedCount() {
    return wouldBeExcludedCount;
  }

  public int getExcludedCount() {
    return excludedCount;
  }

  // === Computed Rates ===

  /** Compile fail rate: compileFailCount / taskCount */
  @JsonProperty("compile_fail_rate")
  public double getCompileFailRate() {
    return taskCount == 0 ? 0.0 : (double) compileFailCount / taskCount;
  }

  /** Runtime fail rate (test did not pass): testFailCount / taskCount */
  @JsonProperty("runtime_fail_rate")
  public double getRuntimeFailRate() {
    return taskCount == 0 ? 0.0 : (double) testFailCount / taskCount;
  }

  /** Fix attempt rate: fixAttemptedCount / taskCount */
  @JsonProperty("fix_attempt_rate")
  public double getFixAttemptRate() {
    return taskCount == 0 ? 0.0 : (double) fixAttemptedCount / taskCount;
  }

  /** Fix success rate: fixSuccessCount / fixAttemptedCount */
  @JsonProperty("fix_success_rate")
  public double getFixSuccessRate() {
    return fixAttemptedCount == 0 ? 0.0 : (double) fixSuccessCount / fixAttemptedCount;
  }

  /** Would-be-excluded rate (dry-run): wouldBeExcludedCount / taskCount */
  @JsonProperty("would_be_excluded_rate")
  public double getWouldBeExcludedRate() {
    return taskCount == 0 ? 0.0 : (double) wouldBeExcludedCount / taskCount;
  }

  /** Excluded rate (enforce): excludedCount / taskCount */
  @JsonProperty("excluded_rate")
  public double getExcludedRate() {
    return taskCount == 0 ? 0.0 : (double) excludedCount / taskCount;
  }
}
