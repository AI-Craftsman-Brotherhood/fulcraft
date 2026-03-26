package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReasonSummaryTest {

  @Test
  void setReasonStatsUsesSortedKeysAndIsUnmodifiable() {
    ReasonSummary summary = new ReasonSummary();

    Map<String, ReasonStats> source = new HashMap<>();
    source.put("b", new ReasonStats());
    source.put("a", new ReasonStats());

    summary.setReasonStats(source);

    List<String> keys = new ArrayList<>(summary.getReasonStats().keySet());
    assertEquals(List.of("a", "b"), keys);
    assertThrows(
        UnsupportedOperationException.class, () -> summary.getReasonStats().put("c", null));
  }

  @Test
  void getOrCreateStatsReturnsExistingInstance() {
    ReasonSummary summary = new ReasonSummary();

    ReasonStats first = summary.getOrCreateStats("low_conf");
    ReasonStats second = summary.getOrCreateStats("low_conf");

    assertNotNull(first);
    assertSame(first, second);
    assertEquals(1, summary.getReasonStats().size());
  }
}
