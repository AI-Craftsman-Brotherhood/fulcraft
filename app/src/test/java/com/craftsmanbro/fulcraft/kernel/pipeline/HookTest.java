package com.craftsmanbro.fulcraft.kernel.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the {@link Hook} enum. */
class HookTest {

  @Test
  void shouldHavePreAndPostValues() {
    Hook[] values = Hook.values();

    assertThat(values).hasSize(2).containsExactly(Hook.PRE, Hook.POST);
  }

  @Test
  void shouldParsePreFromName() {
    Hook pre = Hook.valueOf("PRE");

    assertThat(pre).isEqualTo(Hook.PRE);
  }

  @Test
  void shouldParsePostFromName() {
    Hook post = Hook.valueOf("POST");

    assertThat(post).isEqualTo(Hook.POST);
  }

  @Test
  void shouldHaveCorrectOrdinals() {
    assertThat(Hook.PRE.ordinal()).isZero();
    assertThat(Hook.POST.ordinal()).isEqualTo(1);
  }
}
