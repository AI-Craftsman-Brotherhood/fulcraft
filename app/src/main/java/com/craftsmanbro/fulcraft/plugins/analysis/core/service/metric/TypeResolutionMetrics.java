package com.craftsmanbro.fulcraft.plugins.analysis.core.service.metric;

import com.craftsmanbro.fulcraft.plugins.analysis.core.model.UnresolvedReason;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe metrics for tracking type resolution success/failure during analysis.
 *
 * <p>Tracks:
 *
 * <ul>
 *   <li>Total type resolution attempts
 *   <li>Resolved types (full FQN known)
 *   <li>Unresolved types (type not found)
 *   <li>Partially resolved types (class name known but FQN unknown)
 *   <li>Breakdown by unresolved reason
 * </ul>
 */
public class TypeResolutionMetrics {

  private final AtomicInteger total = new AtomicInteger(0);

  private final AtomicInteger resolved = new AtomicInteger(0);

  private final AtomicInteger unresolved = new AtomicInteger(0);

  private final AtomicInteger partial = new AtomicInteger(0);

  private final Map<UnresolvedReason, AtomicInteger> unresolvedReasons = new ConcurrentHashMap<>();

  // "javaparser" or "spoon"
  private final String source;

  public TypeResolutionMetrics(final String source) {
    this.source = source;
    for (final UnresolvedReason reason : UnresolvedReason.values()) {
      unresolvedReasons.put(reason, new AtomicInteger(0));
    }
  }

  /** Record a successful type resolution */
  public void recordResolved() {
    total.incrementAndGet();
    resolved.incrementAndGet();
  }

  /** Record a failed type resolution with reason */
  public void recordUnresolved(final UnresolvedReason reason) {
    total.incrementAndGet();
    unresolved.incrementAndGet();
    final UnresolvedReason safeReason = reason != null ? reason : UnresolvedReason.UNKNOWN;
    unresolvedReasons
        .computeIfAbsent(safeReason, ignored -> new AtomicInteger(0))
        .incrementAndGet();
  }

  /** Record a partially resolved type (class name known, FQN unknown) */
  public void recordPartial() {
    total.incrementAndGet();
    partial.incrementAndGet();
  }

  public int getTotal() {
    return total.get();
  }

  public int getResolved() {
    return resolved.get();
  }

  public int getUnresolved() {
    return unresolved.get();
  }

  public int getPartial() {
    return partial.get();
  }

  public String getSource() {
    return source;
  }

  /** Returns the resolution rate as a ratio (0.0 to 1.0). Returns 0.0 if no attempts were made. */
  public double getResolutionRate() {
    final int totalCount = total.get();
    if (totalCount == 0) {
      return 0.0;
    }
    return (double) resolved.get() / totalCount;
  }

  /** Returns unresolved reason breakdown as a map of reason to count. */
  public Map<String, Integer> getUnresolvedBreakdown() {
    final Map<String, Integer> breakdown = new LinkedHashMap<>();
    for (final UnresolvedReason reason : UnresolvedReason.values()) {
      final AtomicInteger counter = unresolvedReasons.get(reason);
      if (counter == null) {
        continue;
      }
      final int count = counter.get();
      if (count > 0) {
        breakdown.put(reason.name(), count);
      }
    }
    return breakdown;
  }

  /** Merge another metrics instance into this one. */
  public void merge(final TypeResolutionMetrics other) {
    if (other == null) {
      return;
    }
    total.addAndGet(other.total.get());
    resolved.addAndGet(other.resolved.get());
    unresolved.addAndGet(other.unresolved.get());
    partial.addAndGet(other.partial.get());
    for (final UnresolvedReason reason : UnresolvedReason.values()) {
      final AtomicInteger target = unresolvedReasons.computeIfAbsent(reason, ignored -> new AtomicInteger(0));
      final AtomicInteger otherCounter = other.unresolvedReasons.get(reason);
      if (otherCounter != null) {
        target.addAndGet(otherCounter.get());
      }
    }
  }

  /** Convert to a map for JSON serialization. */
  public Map<String, Object> toMap() {
    final Map<String, Object> map = new LinkedHashMap<>();
    map.put("source", source);
    map.put("total", getTotal());
    map.put("resolved", getResolved());
    map.put("unresolved", getUnresolved());
    map.put("partial", getPartial());
    map.put("resolution_rate", Math.round(getResolutionRate() * 100.0) / 100.0);
    map.put("unresolved_breakdown", getUnresolvedBreakdown());
    return map;
  }

  @Override
  public String toString() {
    return String.format(
        "TypeResolutionMetrics[source=%s, total=%d, resolved=%d, unresolved=%d, partial=%d, rate=%.2f]",
        source, getTotal(), getResolved(), getUnresolved(), getPartial(), getResolutionRate());
  }
}
