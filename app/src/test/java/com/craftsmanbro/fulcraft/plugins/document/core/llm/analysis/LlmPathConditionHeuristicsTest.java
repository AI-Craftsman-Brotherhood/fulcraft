package com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmPathConditionHeuristicsTest {

  private final LlmPathConditionHeuristics heuristics = new LlmPathConditionHeuristics();

  @Test
  void isLikelyErrorIndicator_shouldDetectCommonFailureSignals() {
    assertThat(heuristics.isLikelyErrorIndicator("Invalid request causes exception")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("nullの場合は例外を送出する")).isTrue();
  }

  @Test
  void isLikelyErrorIndicator_shouldSuppressBoundaryNonNullChecks() {
    assertThat(heuristics.isLikelyErrorIndicator("Boundary check: customer != null")).isFalse();
    assertThat(heuristics.isLikelyErrorIndicator("境界条件 customer は null でない")).isFalse();
  }

  @Test
  void isLikelyFlowCondition_shouldDetectSuccessAndSwitchFlowHints() {
    assertThat(heuristics.isLikelyFlowCondition("Switch case status=\"NEW\"")).isTrue();
    assertThat(heuristics.isLikelyFlowCondition("success")).isTrue();
    assertThat(heuristics.isLikelyFlowCondition("Main success path")).isTrue();
  }

  @Test
  void isLikelyFlowCondition_shouldReturnFalseForIrrelevantText() {
    assertThat(heuristics.isLikelyFlowCondition("validation error")).isFalse();
    assertThat(heuristics.isLikelyFlowCondition("")).isFalse();
  }

  @Test
  void isLikelyErrorIndicator_shouldHandleEarlyReturnAndNullSafetyPhrases() {
    assertThat(heuristics.isLikelyErrorIndicator("short-circuit when value is null")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("value cannot be null")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("early return on boundary condition")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("request failed")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("入力が不正な場合")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("処理に失敗したケース")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator(null)).isFalse();
    assertThat(heuristics.isLikelyErrorIndicator("safe path")).isFalse();
  }

  @Test
  void isLikelyErrorIndicator_shouldDetectAllConfiguredKeywordVariants() {
    assertThat(heuristics.isLikelyErrorIndicator("operation error detected")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("request fail state")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("unexpected exception occurred")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("invalid payload")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("boundary overflow")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("early-return branch")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("early return branch")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("short-circuit path")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("short circuit path")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("value == null")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("value==null")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("value is null")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("value nullの場合に失敗")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("value null の場合に失敗")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("value cannot be null")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("value must not be null")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("処理失敗")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("例外ケース")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("不正入力")).isTrue();
    assertThat(heuristics.isLikelyErrorIndicator("境界値ケース")).isTrue();
  }

  @Test
  void isLikelyErrorIndicator_shouldSuppressBoundaryNonNullPhraseVariants() {
    assertThat(heuristics.isLikelyErrorIndicator("boundary check: value != null")).isFalse();
    assertThat(heuristics.isLikelyErrorIndicator("boundary check: value !=null")).isFalse();
    assertThat(heuristics.isLikelyErrorIndicator("boundary check: value nullでない")).isFalse();
    assertThat(heuristics.isLikelyErrorIndicator("boundary check: value null でない")).isFalse();
    assertThat(heuristics.isLikelyErrorIndicator("boundary check: value nullではない")).isFalse();
    assertThat(heuristics.isLikelyErrorIndicator("boundary check: value null ではない")).isFalse();
    assertThat(heuristics.isLikelyErrorIndicator("境界条件: value nullでない")).isFalse();
  }

  @Test
  void isLikelyFlowCondition_shouldDetectLoopRelatedTokens() {
    assertThat(heuristics.isLikelyFlowCondition("loop guard: i < size")).isTrue();
    assertThat(heuristics.isLikelyFlowCondition("loop-continue branch")).isTrue();
    assertThat(heuristics.isLikelyFlowCondition("loop-break condition")).isTrue();
    assertThat(heuristics.isLikelyFlowCondition("case-\"OPEN\"")).isTrue();
    assertThat(heuristics.isLikelyFlowCondition("main success")).isTrue();
    assertThat(heuristics.isLikelyFlowCondition(null)).isFalse();
  }
}
