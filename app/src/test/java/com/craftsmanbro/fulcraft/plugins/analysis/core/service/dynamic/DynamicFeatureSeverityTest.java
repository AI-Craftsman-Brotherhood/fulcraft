package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DynamicFeatureSeverityTest {

  @Test
  void weights_areStableAndPositive() {
    assertThat(DynamicFeatureSeverity.LOW.getWeight()).isEqualTo(1);
    assertThat(DynamicFeatureSeverity.MEDIUM.getWeight()).isEqualTo(2);
    assertThat(DynamicFeatureSeverity.HIGH.getWeight()).isEqualTo(3);
  }
}
