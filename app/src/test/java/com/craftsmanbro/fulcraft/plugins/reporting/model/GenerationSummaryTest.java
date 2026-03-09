package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenerationSummaryTest {

  @Test
  void detailsAreDefensivelyCopiedAndUnmodifiable() {
    GenerationSummary summary = new GenerationSummary();
    List<GenerationTaskResult> source = new ArrayList<>();
    source.add(new GenerationTaskResult());

    summary.setDetails(source);
    source.add(new GenerationTaskResult());

    assertEquals(1, summary.getDetails().size());
    assertThrows(
        UnsupportedOperationException.class,
        () -> summary.getDetails().add(new GenerationTaskResult()));

    summary.addDetail(new GenerationTaskResult());
    assertEquals(2, summary.getDetails().size());
  }

  @Test
  void detailsSettersRejectNull() {
    GenerationSummary summary = new GenerationSummary();

    NullPointerException detailsException =
        assertThrows(NullPointerException.class, () -> summary.setDetails(null));
    NullPointerException detailException =
        assertThrows(NullPointerException.class, () -> summary.addDetail(null));

    assertTrue(detailsException.getMessage().endsWith("details"), detailsException.getMessage());
    assertTrue(detailException.getMessage().endsWith("detail"), detailException.getMessage());
  }

  @Test
  void errorCategoryCountsReturnsSortedDefensiveCopy() {
    GenerationSummary summary = new GenerationSummary();
    Map<String, Integer> source = new HashMap<>();
    source.put("b", 2);
    source.put("a", 1);

    summary.setErrorCategoryCounts(source);
    source.put("c", 3);

    Map<String, Integer> snapshot = summary.getErrorCategoryCounts();

    assertEquals(List.of("a", "b"), new ArrayList<>(snapshot.keySet()));
    assertFalse(snapshot.containsKey("c"));

    snapshot.put("x", 9);
    assertFalse(summary.getErrorCategoryCounts().containsKey("x"));
  }

  @Test
  void nullErrorCategoryCountsReturnsEmptyMap() {
    GenerationSummary summary = new GenerationSummary();
    summary.setErrorCategoryCounts(null);

    assertTrue(summary.getErrorCategoryCounts().isEmpty());
  }
}
