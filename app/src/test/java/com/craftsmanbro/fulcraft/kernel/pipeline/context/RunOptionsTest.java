package com.craftsmanbro.fulcraft.kernel.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunOptionsTest {

  @Test
  void shouldStoreFlags() {
    RunOptions options = new RunOptions();

    assertThat(options.isDryRun()).isFalse();
    assertThat(options.isFailFast()).isFalse();
    assertThat(options.isShowSummary()).isFalse();

    options.setDryRun(true);
    options.setFailFast(true);
    options.setShowSummary(true);

    assertThat(options.isDryRun()).isTrue();
    assertThat(options.isFailFast()).isTrue();
    assertThat(options.isShowSummary()).isTrue();
  }
}
