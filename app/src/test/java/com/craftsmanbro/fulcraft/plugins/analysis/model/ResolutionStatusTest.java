package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResolutionStatusTest {

  @Test
  void isResolved_acceptsOnlyResolved() {
    assertThat(ResolutionStatus.isResolved(ResolutionStatus.RESOLVED)).isTrue();
    assertThat(ResolutionStatus.isResolved(ResolutionStatus.UNRESOLVED)).isFalse();
    assertThat(ResolutionStatus.isResolved(ResolutionStatus.AMBIGUOUS)).isFalse();
    assertThat(ResolutionStatus.isResolved(null)).isFalse();
  }

  @Test
  void isUnresolved_acceptsUnresolvedAndAmbiguous() {
    assertThat(ResolutionStatus.isUnresolved(ResolutionStatus.UNRESOLVED)).isTrue();
    assertThat(ResolutionStatus.isUnresolved(ResolutionStatus.AMBIGUOUS)).isTrue();
    assertThat(ResolutionStatus.isUnresolved(ResolutionStatus.RESOLVED)).isFalse();
    assertThat(ResolutionStatus.isUnresolved(null)).isFalse();
  }
}
