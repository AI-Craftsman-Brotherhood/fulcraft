package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class BranchSummaryTest {

  @Test
  void setters_handleNullAndExposeUnmodifiableLists() {
    BranchSummary summary = new BranchSummary();

    summary.setGuards(null);
    summary.setSwitches(null);
    summary.setPredicates(null);

    assertThat(summary.getGuards()).isEmpty();
    assertThat(summary.getSwitches()).isEmpty();
    assertThat(summary.getPredicates()).isEmpty();

    assertThatThrownBy(() -> summary.getGuards().add(new GuardSummary()))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> summary.getSwitches().add("case"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> summary.getPredicates().add("x > 0"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void setters_copyProvidedLists() {
    BranchSummary summary = new BranchSummary();

    summary.setSwitches(List.of("A", "B"));
    summary.setPredicates(List.of("x > 0"));

    assertThat(summary.getSwitches()).containsExactly("A", "B");
    assertThat(summary.getPredicates()).containsExactly("x > 0");
  }
}
