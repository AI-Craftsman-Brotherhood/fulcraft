package com.craftsmanbro.fulcraft.plugins.analysis.core.service.metric;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.core.model.UnresolvedReason;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypeResolutionMetricsTest {

  @Test
  void recordsCountsAndBreakdown() {
    TypeResolutionMetrics metrics = new TypeResolutionMetrics("javaparser");

    metrics.recordResolved();
    metrics.recordPartial();
    metrics.recordUnresolved(UnresolvedReason.PARSE_ERROR);
    metrics.recordUnresolved(null);

    assertThat(metrics.getSource()).isEqualTo("javaparser");
    assertThat(metrics.getTotal()).isEqualTo(4);
    assertThat(metrics.getResolved()).isEqualTo(1);
    assertThat(metrics.getUnresolved()).isEqualTo(2);
    assertThat(metrics.getPartial()).isEqualTo(1);
    assertThat(metrics.getResolutionRate()).isEqualTo(0.25);

    Map<String, Integer> breakdown = metrics.getUnresolvedBreakdown();
    assertThat(breakdown).containsEntry("PARSE_ERROR", 1).containsEntry("UNKNOWN", 1).hasSize(2);

    Map<String, Object> serialized = metrics.toMap();
    assertThat(serialized.get("source")).isEqualTo("javaparser");
    assertThat(serialized.get("total")).isEqualTo(4);
    assertThat(serialized.get("resolved")).isEqualTo(1);
    assertThat(serialized.get("unresolved")).isEqualTo(2);
    assertThat(serialized.get("partial")).isEqualTo(1);
    assertThat(serialized.get("resolution_rate")).isEqualTo(0.25);
    assertThat(serialized.get("unresolved_breakdown")).isInstanceOf(Map.class);
  }

  @Test
  void merge_accumulatesCounters() {
    TypeResolutionMetrics base = new TypeResolutionMetrics("spoon");
    base.recordResolved();
    base.recordResolved();
    base.recordUnresolved(UnresolvedReason.MISSING_CLASSPATH);

    TypeResolutionMetrics other = new TypeResolutionMetrics("spoon");
    other.recordPartial();
    other.recordUnresolved(UnresolvedReason.MISSING_CLASSPATH);
    other.recordUnresolved(UnresolvedReason.MISSING_CLASSPATH);
    other.recordUnresolved(UnresolvedReason.REFLECTION_CALL);

    base.merge(other);

    assertThat(base.getTotal()).isEqualTo(7);
    assertThat(base.getResolved()).isEqualTo(2);
    assertThat(base.getUnresolved()).isEqualTo(4);
    assertThat(base.getPartial()).isEqualTo(1);
    assertThat(base.getUnresolvedBreakdown())
        .containsEntry("MISSING_CLASSPATH", 3)
        .containsEntry("REFLECTION_CALL", 1);
  }

  @Test
  void resolutionRate_isZeroWhenNoAttempts() {
    TypeResolutionMetrics metrics = new TypeResolutionMetrics("spoon");

    assertThat(metrics.getTotal()).isZero();
    assertThat(metrics.getResolutionRate()).isZero();
    assertThat(metrics.getUnresolvedBreakdown()).isEmpty();
  }

  @Test
  void merge_withNull_doesNothing() {
    TypeResolutionMetrics metrics = new TypeResolutionMetrics("javaparser");
    metrics.recordResolved();
    metrics.recordUnresolved(UnresolvedReason.NO_CLASSPATH_MODE);

    metrics.merge(null);

    assertThat(metrics.getTotal()).isEqualTo(2);
    assertThat(metrics.getResolved()).isEqualTo(1);
    assertThat(metrics.getUnresolved()).isEqualTo(1);
    assertThat(metrics.getPartial()).isZero();
    assertThat(metrics.getUnresolvedBreakdown()).containsEntry("NO_CLASSPATH_MODE", 1);
  }

  @Test
  void toString_containsSourceAndRoundedRate() {
    TypeResolutionMetrics metrics = new TypeResolutionMetrics("javaparser");
    metrics.recordResolved();
    metrics.recordUnresolved(UnresolvedReason.PARSE_ERROR);
    metrics.recordPartial();

    assertThat(metrics.toString())
        .contains("source=javaparser")
        .contains("total=3")
        .contains("resolved=1")
        .contains("unresolved=1")
        .contains("partial=1")
        .contains("rate=0.33");
  }
}
