package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DynamicReasonCodeTest {

  @Test
  void allReasonCodesHaveDescriptions() {
    for (DynamicReasonCode code : DynamicReasonCode.values()) {
      assertThat(code.getDescription()).isNotNull();
      assertThat(code.getDescription()).isNotEmpty();
    }
  }

  @Test
  void reasonCodeValues_areCorrect() {
    assertThat(DynamicReasonCode.values()).hasSize(7);
    assertThat(DynamicReasonCode.UNSUPPORTED_EXPRESSION).isNotNull();
    assertThat(DynamicReasonCode.AMBIGUOUS_CANDIDATES).isNotNull();
    assertThat(DynamicReasonCode.DEPTH_LIMIT_EXCEEDED).isNotNull();
    assertThat(DynamicReasonCode.CANDIDATE_LIMIT_EXCEEDED).isNotNull();
    assertThat(DynamicReasonCode.UNRESOLVED_DEPENDENCY).isNotNull();
    assertThat(DynamicReasonCode.TARGET_CLASS_UNRESOLVED).isNotNull();
    assertThat(DynamicReasonCode.TARGET_METHOD_MISSING).isNotNull();
  }

  @Test
  void fromString_parsesCaseInsensitiveAndHandlesUnknown() {
    assertThat(DynamicReasonCode.fromString("ambiguous_candidates"))
        .isEqualTo(DynamicReasonCode.AMBIGUOUS_CANDIDATES);
    assertThat(DynamicReasonCode.fromString("  CANDIDATE_LIMIT_EXCEEDED "))
        .isEqualTo(DynamicReasonCode.CANDIDATE_LIMIT_EXCEEDED);
    assertThat(DynamicReasonCode.fromString("nope")).isNull();
    assertThat(DynamicReasonCode.fromString(" ")).isNull();
  }

  @Test
  void unsupportedExpression_hasCorrectDescription() {
    assertThat(DynamicReasonCode.UNSUPPORTED_EXPRESSION.getDescription()).contains("not supported");
  }

  @Test
  void ambiguousCandidates_hasCorrectDescription() {
    assertThat(DynamicReasonCode.AMBIGUOUS_CANDIDATES.getDescription())
        .contains("Multiple candidates");
  }

  @Test
  void depthLimitExceeded_hasCorrectDescription() {
    assertThat(DynamicReasonCode.DEPTH_LIMIT_EXCEEDED.getDescription()).contains("depth limit");
  }

  @Test
  void candidateLimitExceeded_hasCorrectDescription() {
    assertThat(DynamicReasonCode.CANDIDATE_LIMIT_EXCEEDED.getDescription())
        .contains("Candidate limit");
  }

  @Test
  void unresolvedDependency_hasCorrectDescription() {
    assertThat(DynamicReasonCode.UNRESOLVED_DEPENDENCY.getDescription()).contains("dependency");
  }

  @Test
  void targetClassUnresolved_hasCorrectDescription() {
    assertThat(DynamicReasonCode.TARGET_CLASS_UNRESOLVED.getDescription()).contains("Target class");
  }

  @Test
  void targetMethodMissing_hasCorrectDescription() {
    assertThat(DynamicReasonCode.TARGET_METHOD_MISSING.getDescription())
        .contains("requested method");
  }
}
