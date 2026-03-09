package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Complete report in a structured format for CI/dashboard integration.
 *
 * <p>Contains all aggregation results in a machine-readable format with stable schema.
 *
 * <p>The type name keeps the historical "Phase6" label for backward compatibility.
 *
 * @param runId Unique identifier for this run
 * @param timestamp Unix timestamp (milliseconds) when the run was executed
 * @param isDryRun Whether this was a dry-run or enforce mode
 * @param summary Run-level aggregated metrics
 * @param reasonStats Reason-based aggregated metrics (key: reason, value: stats)
 * @param evaluation Selection strength evaluation result
 * @param recommendations List of parameter recommendations
 */
public record Phase6Report(
    @JsonProperty("run_id") String runId,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("is_dry_run") boolean isDryRun,
    @JsonProperty("summary") SummarySection summary,
    @JsonProperty("reason_stats") Map<String, ReasonStatsSection> reasonStats,
    @JsonProperty("evaluation") EvaluationSection evaluation,
    @JsonProperty("recommendations") List<RecommendationSection> recommendations) {

  /** Compact constructor that creates defensive copies of collections. */
  public Phase6Report {
    reasonStats = reasonStats != null ? Map.copyOf(reasonStats) : Map.of();
    recommendations = recommendations != null ? List.copyOf(recommendations) : List.of();
  }

  /**
   * Summary metrics section.
   *
   * @param totalTasks Total number of tasks in run
   * @param executedTasks Number of tasks actually executed
   * @param selectedTasks Number of tasks selected
   * @param compileSuccessCount Number of tasks that compiled successfully
   * @param testPassCount Number of tasks with passing tests
   * @param fixAttemptedCount Number of fix attempts
   * @param fixSuccessCount Number of successful fixes
   * @param excludedCount Number of excluded tasks (enforce mode)
   * @param wouldBeExcludedCount Number of would-be-excluded tasks (dry-run)
   * @param wouldBeExcludedRate Would-be-excluded rate (0.0-1.0)
   * @param compileRate Compile success rate (0.0-1.0)
   * @param passRate Test pass rate (0.0-1.0)
   * @param fixSuccessRate Fix success rate (0.0-1.0)
   * @param excludedRate Excluded rate (0.0-1.0)
   * @param llmCallsPerTask Average LLM calls per task
   * @param retriesPerTask Average retries per task
   */
  public record SummarySection(
      @JsonProperty("total_tasks") int totalTasks,
      @JsonProperty("executed_tasks") int executedTasks,
      @JsonProperty("selected_tasks") int selectedTasks,
      @JsonProperty("compile_success_count") int compileSuccessCount,
      @JsonProperty("test_pass_count") int testPassCount,
      @JsonProperty("fix_attempted_count") int fixAttemptedCount,
      @JsonProperty("fix_success_count") int fixSuccessCount,
      @JsonProperty("excluded_count") int excludedCount,
      @JsonProperty("would_be_excluded_count") int wouldBeExcludedCount,
      @JsonProperty("would_be_excluded_rate") double wouldBeExcludedRate,
      @JsonProperty("compile_rate") double compileRate,
      @JsonProperty("pass_rate") double passRate,
      @JsonProperty("fix_success_rate") double fixSuccessRate,
      @JsonProperty("excluded_rate") double excludedRate,
      @JsonProperty("llm_calls_per_task") double llmCallsPerTask,
      @JsonProperty("retries_per_task") double retriesPerTask) {}

  /**
   * Reason-level statistics section.
   *
   * @param taskCount Number of tasks tagged with this reason
   * @param compileFailCount Compile failures
   * @param compileFailRate Compile failure rate (0.0-1.0)
   * @param runtimeFailRate Runtime failure rate (0.0-1.0)
   * @param fixAttemptedCount Fix attempts
   * @param fixSuccessCount Successful fixes
   * @param fixSuccessRate Fix success rate (0.0-1.0)
   * @param excludedCount Excluded tasks
   * @param excludedRate Excluded rate (0.0-1.0)
   */
  public record ReasonStatsSection(
      @JsonProperty("task_count") int taskCount,
      @JsonProperty("compile_fail_count") int compileFailCount,
      @JsonProperty("compile_fail_rate") double compileFailRate,
      @JsonProperty("runtime_fail_rate") double runtimeFailRate,
      @JsonProperty("fix_attempted_count") int fixAttemptedCount,
      @JsonProperty("fix_success_count") int fixSuccessCount,
      @JsonProperty("fix_success_rate") double fixSuccessRate,
      @JsonProperty("excluded_count") int excludedCount,
      @JsonProperty("excluded_rate") double excludedRate) {}

  /**
   * Selection evaluation section.
   *
   * @param strength Evaluation result: OK, TOO_STRICT, TOO_WEAK
   * @param reason Human-readable explanation
   * @param excludedRate Actual excluded rate
   * @param compileFailRate Compile failure rate
   * @param wastedPotential Ratio of excluded tasks that could have succeeded
   * @param dominantFailureReason Reason with highest failure concentration (if TOO_WEAK)
   */
  public record EvaluationSection(
      @JsonProperty("strength") String strength,
      @JsonProperty("reason") String reason,
      @JsonProperty("excluded_rate") double excludedRate,
      @JsonProperty("compile_fail_rate") double compileFailRate,
      @JsonProperty("wasted_potential") double wastedPotential,
      @JsonProperty("dominant_failure_reason") String dominantFailureReason) {}

  /**
   * Parameter recommendation section.
   *
   * @param parameterName Name of the parameter
   * @param currentValue Current value
   * @param recommendedValue Recommended value
   * @param change Change amount (positive=increase, negative=decrease)
   * @param reason Human-readable explanation
   * @param triggerReason The reason key that triggered this recommendation
   */
  public record RecommendationSection(
      @JsonProperty("parameter_name") String parameterName,
      @JsonProperty("current_value") double currentValue,
      @JsonProperty("recommended_value") double recommendedValue,
      @JsonProperty("change") double change,
      @JsonProperty("reason") String reason,
      @JsonProperty("trigger_reason") String triggerReason) {}
}
