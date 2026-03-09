package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregated summary of failure/success rates by dynamic reason.
 *
 * <p>Provides a mapping from normalized reason keys to their respective statistics.
 */
public class ReasonSummary {

  @JsonProperty("run_id")
  private String runId;

  @JsonProperty("timestamp")
  private long timestamp;

  @JsonProperty("is_dry_run")
  private boolean isDryRun;

  @JsonProperty("reason_stats")
  private Map<String, ReasonStats> reasonStats = new TreeMap<>();

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

  public Map<String, ReasonStats> getReasonStats() {
    return Collections.unmodifiableMap(reasonStats);
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

  public void setReasonStats(final Map<String, ReasonStats> reasonStats) {
    // Use TreeMap for deterministic ordering
    this.reasonStats = reasonStats != null ? new TreeMap<>(reasonStats) : new TreeMap<>();
  }

  // === Helper Methods ===
  /**
   * Get or create stats for a specific reason key.
   *
   * @param reasonKey normalized reason key
   * @return existing or new ReasonStats instance
   */
  public ReasonStats getOrCreateStats(final String reasonKey) {
    return reasonStats.computeIfAbsent(reasonKey, k -> new ReasonStats());
  }
}
