package com.craftsmanbro.fulcraft.infrastructure.metrics.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CodeMetricsTest {

  @Test
  void constructor_acceptsNonNegativeValues() {
    CodeMetrics metrics = new CodeMetrics(3, 2);

    assertThat(metrics.cyclomaticComplexity()).isEqualTo(3);
    assertThat(metrics.maxNestingDepth()).isEqualTo(2);
  }

  @Test
  void constructor_rejectsNegativeValues() {
    assertThatThrownBy(() -> new CodeMetrics(-1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cyclomaticComplexity");

    assertThatThrownBy(() -> new CodeMetrics(1, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxNestingDepth");
  }
}
