package com.craftsmanbro.fulcraft.kernel.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RunMetadataKeysTest {

  @Test
  void shouldExposeStableKeyNames() {
    assertThat(RunMetadataKeys.FLAKY_TEST_SUMMARY_KEY).isEqualTo("flakyTestSummary");
  }

  @Test
  void shouldNormalizeStageNamesForLlmMetadataKeys() {
    assertThat(RunMetadataKeys.llmStageKey(" Generate ")).isEqualTo("run.llm.stage.generate");
    assertThat(RunMetadataKeys.llmStageKey("report")).isEqualTo("run.llm.stage.report");
    assertThat(RunMetadataKeys.llmStageKey("custom-plugin")).isEqualTo("run.llm.stage.generate");
  }

  @Test
  void shouldRejectBlankStageNamesForLlmMetadataKeys() {
    assertThatThrownBy(() -> RunMetadataKeys.llmStageKey("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stageName");
  }
}
