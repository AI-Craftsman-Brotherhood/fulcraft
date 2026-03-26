package com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicReasonCode;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmDynamicResolutionEvaluatorTest {

  private final LlmDynamicResolutionEvaluator evaluator = new LlmDynamicResolutionEvaluator();

  @Test
  void hasUncertainDynamicResolution_shouldReturnFalseForNullMethod() {
    assertThat(evaluator.hasOpenQuestionDynamicResolution(null)).isFalse();
    assertThat(evaluator.hasKnownMissingDynamicResolution(null)).isFalse();
    assertThat(evaluator.hasUncertainDynamicResolution(null)).isFalse();
  }

  @Test
  void hasKnownMissingDynamicResolution_shouldTreatTargetMethodMissingAsKnownMissing() {
    MethodInfo method = new MethodInfo();
    method.setDynamicResolutions(List.of(resolution(DynamicReasonCode.TARGET_METHOD_MISSING)));

    assertThat(evaluator.hasKnownMissingDynamicResolution(method)).isTrue();
    assertThat(evaluator.hasOpenQuestionDynamicResolution(method)).isFalse();
    assertThat(evaluator.hasUncertainDynamicResolution(method)).isTrue();
  }

  @Test
  void hasOpenQuestionDynamicResolution_shouldTreatTargetClassUnresolvedAsOpenQuestion() {
    MethodInfo method = new MethodInfo();
    method.setDynamicResolutions(List.of(resolution(DynamicReasonCode.TARGET_CLASS_UNRESOLVED)));

    assertThat(evaluator.hasOpenQuestionDynamicResolution(method)).isTrue();
    assertThat(evaluator.hasKnownMissingDynamicResolution(method)).isFalse();
    assertThat(evaluator.hasUncertainDynamicResolution(method)).isTrue();
  }

  @Test
  void isResolutionOpenQuestion_shouldUseLegacyFallbackWhenReasonCodeIsMissing() {
    DynamicResolution unresolved =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(0.8)
            .trustLevel(TrustLevel.HIGH)
            .candidates(List.of("com.example.CustomerService#processCustomer(String)"))
            .evidence(Map.of("verified", "n/a"))
            .build();

    assertThat(evaluator.isResolutionOpenQuestion(unresolved)).isTrue();
  }

  @Test
  void isResolutionOpenQuestion_shouldRespectLegacyVerifiedAndTrustHeuristics() {
    DynamicResolution verifiedHighSingleCandidate =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(1.0)
            .trustLevel(TrustLevel.HIGH)
            .candidates(List.of("com.example.Service#run()"))
            .evidence(Map.of("verified", "true"))
            .build();
    DynamicResolution explicitlyUnverified =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(1.0)
            .trustLevel(TrustLevel.HIGH)
            .candidates(List.of("com.example.Service#run()"))
            .evidence(Map.of("verified", "false"))
            .build();
    DynamicResolution mediumTrust =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(1.0)
            .trustLevel(TrustLevel.MEDIUM)
            .candidates(List.of("com.example.Service#run()"))
            .evidence(Map.of("verified", "true"))
            .build();
    DynamicResolution multipleCandidates =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(1.0)
            .trustLevel(TrustLevel.HIGH)
            .candidates(List.of("com.example.Service#run()", "com.example.Service#run(String)"))
            .evidence(Map.of("verified", "true"))
            .build();

    assertThat(evaluator.isResolutionOpenQuestion(verifiedHighSingleCandidate)).isFalse();
    assertThat(evaluator.isResolutionOpenQuestion(explicitlyUnverified)).isTrue();
    assertThat(evaluator.isResolutionOpenQuestion(mediumTrust)).isTrue();
    assertThat(evaluator.isResolutionOpenQuestion(multipleCandidates)).isTrue();
  }

  @Test
  void isResolutionUncertain_shouldCombineKnownMissingAndOpenQuestionChecks() {
    DynamicResolution knownMissing = resolution(DynamicReasonCode.TARGET_METHOD_MISSING);
    DynamicResolution openQuestion = resolution(DynamicReasonCode.UNSUPPORTED_EXPRESSION);

    assertThat(evaluator.isResolutionUncertain(null)).isFalse();
    assertThat(evaluator.isResolutionUncertain(knownMissing)).isTrue();
    assertThat(evaluator.isResolutionUncertain(openQuestion)).isTrue();
  }

  @Test
  void readVerifiedFlag_shouldReturnNaWhenVerifiedFlagIsMissingOrBlank() {
    DynamicResolution missing =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(1.0)
            .trustLevel(TrustLevel.HIGH)
            .candidates(List.of("com.example.CustomerService#processCustomer(String)"))
            .evidence(Map.of())
            .build();
    DynamicResolution blank =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(1.0)
            .trustLevel(TrustLevel.HIGH)
            .candidates(List.of("com.example.CustomerService#processCustomer(String)"))
            .evidence(Map.of("verified", "   "))
            .build();

    assertThat(evaluator.readVerifiedFlag(missing)).isEqualTo("n/a");
    assertThat(evaluator.readVerifiedFlag(blank)).isEqualTo("n/a");
  }

  private DynamicResolution resolution(DynamicReasonCode reasonCode) {
    return DynamicResolution.builder()
        .subtype(DynamicResolution.METHOD_RESOLVE)
        .confidence(1.0)
        .trustLevel(TrustLevel.HIGH)
        .candidates(List.of("com.example.CustomerService#processCustomer(String)"))
        .reasonCode(reasonCode)
        .evidence(Map.of("verified", "true"))
        .build();
  }
}
