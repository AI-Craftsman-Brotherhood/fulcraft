package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.fs.impl.PathOrder;
import org.junit.jupiter.api.Test;

class PathOrderAdapterTest {

  @Test
  void stableComparatorDelegatesToPathOrder() {
    assertThat(PathOrderAdapter.STABLE).isSameAs(PathOrder.STABLE);
  }
}
