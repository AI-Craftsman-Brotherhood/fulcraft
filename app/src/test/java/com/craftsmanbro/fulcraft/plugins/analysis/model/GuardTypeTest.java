package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuardTypeTest {

  @Test
  void isRepresentativePathGuard_matchesExpectedTypes() {
    assertThat(GuardType.FAIL_GUARD.isRepresentativePathGuard()).isTrue();
    assertThat(GuardType.MESSAGE_GUARD.isRepresentativePathGuard()).isTrue();
    assertThat(GuardType.LOOP_GUARD_CONTINUE.isRepresentativePathGuard()).isTrue();
    assertThat(GuardType.LOOP_GUARD_BREAK.isRepresentativePathGuard()).isTrue();
    assertThat(GuardType.LEGACY.isRepresentativePathGuard()).isFalse();
  }
}
