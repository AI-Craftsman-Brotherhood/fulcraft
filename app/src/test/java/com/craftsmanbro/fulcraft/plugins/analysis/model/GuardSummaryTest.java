package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardSummaryTest {

  @Test
  void legacyConstructor_setsLegacyTypeAndEmptyEffects() {
    GuardSummary summary = new GuardSummary("x != null");

    assertThat(summary.getType()).isEqualTo(GuardType.LEGACY);
    assertThat(summary.getCondition()).isEqualTo("x != null");
    assertThat(summary.getEffects()).isEmpty();
  }

  @Test
  void constructor_copiesEffectList() {
    List<String> effects = new ArrayList<>(List.of("throw"));
    GuardSummary summary =
        new GuardSummary(GuardType.FAIL_GUARD, "x == null", effects, "msg", "L1");

    effects.add("log");

    assertThat(summary.getEffects()).containsExactly("throw");
  }

  @Test
  void effects_areUnmodifiableAndNullSafe() {
    GuardSummary summary = new GuardSummary();

    summary.setEffects(null);

    assertThat(summary.getEffects()).isEmpty();
    assertThatThrownBy(() -> summary.getEffects().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
