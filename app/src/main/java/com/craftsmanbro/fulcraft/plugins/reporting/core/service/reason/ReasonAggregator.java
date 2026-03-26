package com.craftsmanbro.fulcraft.plugins.reporting.core.service.reason;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonStats;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReasonSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.plugins.reporting.taskio.TaskEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aggregates reason-based metrics from tasks files using streaming processing.
 *
 * <p>Processes tasks files and extracts dynamic_reasons from feasibility_breakdown to track
 * correlations between reasons and selection/exclusion. Note: This aggregator processes task
 * definitions (plan) only.
 */
public class ReasonAggregator {

  private static final String UNKNOWN = "unknown";

  // Pattern for extracting reason type from parameterized reasons
  private static final Pattern LOW_CONF_PATTERN =
      Pattern.compile("low_confidence\\([\\d.]+\\)<[\\d.]+");

  private static final Pattern UNRESOLVED_PATTERN =
      Pattern.compile("unresolved\\((\\d+)\\)\\*[\\d.]+");

  private static final Pattern EXTERNAL_PATTERN = Pattern.compile("external\\((\\d+)\\)\\*[\\d.]+");

  private static final Pattern SKIP_PATTERN = Pattern.compile("SKIP:min_conf<[\\d.]+");

  // Known simple reason keys
  private static final Set<String> SIMPLE_REASONS =
      Set.of(
          "spi_low_conf",
          "branch_candidates",
          "interprocedural_candidates",
          "one_hop",
          "external_not_found");

  /**
   * Aggregate reason-based metrics from task entries (I/O-free).
   *
   * @param entries task entries (plan only)
   * @param runId Run identifier
   * @param isDryRun Whether this was a dry-run execution
   * @return Aggregated ReasonSummary
   */
  public ReasonSummary aggregate(
      final Iterable<TaskEntry> entries, final String runId, final boolean isDryRun) {
    Objects.requireNonNull(
        entries,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "entries"));
    final ReasonSummary summary = new ReasonSummary();
    summary.setRunId(runId);
    summary.setTimestamp(System.currentTimeMillis());
    summary.setDryRun(isDryRun);
    final Set<String> seenTasks = new HashSet<>();
    for (final TaskEntry entry : entries) {
      if (entry.hasTask()) {
        final TaskRecord task = entry.task();
        final String key = buildKey(task);
        if (seenTasks.add(key)) {
          processTaskRecord(task, summary, isDryRun);
        }
      }
    }
    Logger.info(
        MessageSource.getMessage(
            "report.reason.summary_complete", summary.getReasonStats().size()));
    return summary;
  }

  private String buildKey(final TaskRecord task) {
    return buildKey(task.getTaskId(), task.getClassFqn(), task.getMethodName());
  }

  private String buildKey(final String taskId, final String classFqn, final String methodName) {
    if (taskId != null && !taskId.isBlank()) {
      return taskId;
    }
    final String classPart = classFqn != null ? classFqn : UNKNOWN;
    final String methodPart = methodName != null ? methodName : UNKNOWN;
    return (classPart + "#" + methodPart).toLowerCase(Locale.ROOT);
  }

  /** Process a task record (plan only). */
  private void processTaskRecord(
      final TaskRecord task, final ReasonSummary summary, final boolean isDryRun) {
    final List<String> reasons = extractReasons(task);
    if (reasons.isEmpty()) {
      return;
    }
    final boolean wouldBeExcluded = checkWouldBeExcluded(task);
    final boolean excluded = checkExcluded(task);
    for (final String rawReason : reasons) {
      final String normalizedKey = normalizeReason(rawReason);
      if (normalizedKey == null) {
        continue;
      }
      final ReasonStats stats = summary.getOrCreateStats(normalizedKey);
      stats.incrementTaskCount();
      if (isDryRun && wouldBeExcluded) {
        stats.incrementWouldBeExcluded();
      } else if (!isDryRun && excluded) {
        stats.incrementExcluded();
      }
    }
  }

  /** Extract dynamic reasons from task feasibility breakdown. */
  List<String> extractReasons(final TaskRecord task) {
    final List<String> result = new ArrayList<>();
    final Map<String, Object> breakdown = task.getFeasibilityBreakdown();
    if (breakdown == null) {
      return result;
    }
    // Extract from "dynamic_reasons" field
    extractDynamicReasons(breakdown, result);
    // Also extract from "signals.dynamicReasons" if present
    extractSignalReasons(breakdown, result);
    return result;
  }

  private void extractDynamicReasons(
      final Map<String, Object> breakdown, final List<String> result) {
    final Object dynamicReasons = breakdown.get("dynamic_reasons");
    if (dynamicReasons instanceof List<?> list) {
      for (final Object item : list) {
        if (item instanceof String s) {
          result.add(s);
        }
      }
    }
  }

  private void extractSignalReasons(
      final Map<String, Object> breakdown, final List<String> result) {
    final Object signals = breakdown.get("signals");
    if (signals instanceof Map<?, ?> signalsMap) {
      final Object signalReasons = signalsMap.get("dynamicReasons");
      if (signalReasons instanceof List<?> list) {
        for (final Object item : list) {
          if (item instanceof String s && !result.contains(s)) {
            result.add(s);
          }
        }
      }
    }
  }

  /**
   * Normalize a raw reason string to a canonical key.
   *
   * <p>Examples: - "low_confidence(0.75)<0.8" → "low_conf" - "unresolved(2)*0.10" → "unresolved" -
   * "external(1)*0.20" → "external" - "spi_low_conf" → "spi_low_conf" - "SKIP:min_conf<0.5" →
   * "skip_low_conf"
   */
  String normalizeReason(final String rawReason) {
    if (rawReason == null || rawReason.isBlank()) {
      return null;
    }
    final String trimmed = rawReason.trim();
    // Check simple reasons first
    if (SIMPLE_REASONS.contains(trimmed)) {
      return trimmed;
    }
    // Pattern matching for parameterized reasons
    final Matcher lowConfMatcher = LOW_CONF_PATTERN.matcher(trimmed);
    if (lowConfMatcher.matches()) {
      return "low_conf";
    }
    final Matcher unresolvedMatcher = UNRESOLVED_PATTERN.matcher(trimmed);
    if (unresolvedMatcher.matches()) {
      return "unresolved";
    }
    final Matcher externalMatcher = EXTERNAL_PATTERN.matcher(trimmed);
    if (externalMatcher.matches()) {
      return "external";
    }
    final Matcher skipMatcher = SKIP_PATTERN.matcher(trimmed);
    if (skipMatcher.matches()) {
      return "skip_low_conf";
    }
    // Return trimmed as-is for unknown patterns (allows dynamic discovery)
    return trimmed;
  }

  /** Check if task would be excluded (dry-run mode). */
  private boolean checkWouldBeExcluded(final TaskRecord task) {
    final Map<String, Object> breakdown = task.getFeasibilityBreakdown();
    if (breakdown == null) {
      return false;
    }
    // Check shadow_score or dynamic_reasons containing SKIP
    final Object shadowScore = breakdown.get("shadow_score");
    if (shadowScore instanceof Number n && n.doubleValue() <= 0.0) {
      return true;
    }
    if (hasDynamicPenalty(breakdown)) {
      return true;
    }
    final Object dynamicReasons = breakdown.get("dynamic_reasons");
    if (dynamicReasons instanceof List<?> list) {
      for (final Object item : list) {
        if (item instanceof String s && s.startsWith("SKIP:")) {
          return true;
        }
      }
    }
    return false;
  }

  /** Check if task was excluded (enforce mode). */
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
