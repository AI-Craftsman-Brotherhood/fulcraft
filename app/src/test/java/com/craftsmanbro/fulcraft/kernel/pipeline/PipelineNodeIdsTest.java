package com.craftsmanbro.fulcraft.kernel.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import org.junit.jupiter.api.Test;

class PipelineNodeIdsTest {

  @Test
  void officialTopLevelShouldContainCanonicalOrder() {
    assertThat(PipelineNodeIds.OFFICIAL_TOP_LEVEL)
        .containsExactly(
            PipelineNodeIds.ANALYZE,
            PipelineNodeIds.GENERATE,
            PipelineNodeIds.REPORT,
            PipelineNodeIds.DOCUMENT,
            PipelineNodeIds.EXPLORE);
  }

  @Test
  void normalizeRequiredShouldLowercase() {
    assertThat(PipelineNodeIds.normalizeRequired("  ANALYZE  ", "nodeId"))
        .isEqualTo(PipelineNodeIds.ANALYZE);
  }

  @Test
  void normalizeRequiredShouldRejectBlank() {
    assertThatThrownBy(() -> PipelineNodeIds.normalizeRequired(" ", "nodeId"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            MessageSource.getMessage("kernel.pipeline.node_ids.error.blank", "nodeId"));
  }

  @Test
  void classifyPhaseShouldMapUnknownToGenerate() {
    assertThat(PipelineNodeIds.classifyPhase("junit-select")).isEqualTo(PipelineNodeIds.GENERATE);
  }
}
