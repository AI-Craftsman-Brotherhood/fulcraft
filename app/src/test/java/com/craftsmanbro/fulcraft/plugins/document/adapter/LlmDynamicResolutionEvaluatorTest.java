package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmDynamicResolutionEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmDynamicResolutionEvaluatorTest {

  private final LlmDynamicResolutionEvaluator evaluator = new LlmDynamicResolutionEvaluator();

  @Test
  void treatsVerifiedHighTrustSingleCandidateAsConfirmedEvenWhenConfidenceIsBelowOne() {
    DynamicResolution resolution =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(0.9)
            .trustLevel(TrustLevel.HIGH)
            .candidates(List.of("com.example.CustomerService#processCustomer(String)"))
            .evidence(Map.of("verified", "true"))
            .build();

    assertThat(evaluator.isResolutionUncertain(resolution)).isFalse();
  }

  @Test
  void treatsVerifiedFalseAsUncertain() {
    DynamicResolution resolution =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(1.0)
            .trustLevel(TrustLevel.HIGH)
            .candidates(List.of("com.example.CustomerService#processCustomer(String)"))
            .evidence(Map.of("verified", "false"))
            .build();

    assertThat(evaluator.isResolutionUncertain(resolution)).isTrue();
  }

  @Test
  void treatsMediumTrustAsUncertainEvenWhenVerifiedTrue() {
    DynamicResolution resolution =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(0.9)
            .trustLevel(TrustLevel.MEDIUM)
            .candidates(List.of("com.example.CustomerService#processCustomer(String)"))
            .evidence(Map.of("verified", "true"))
            .build();

    assertThat(evaluator.isResolutionUncertain(resolution)).isTrue();
  }

  @Test
  void treatsMultipleCandidatesAsUncertainEvenWhenVerifiedTrue() {
    DynamicResolution resolution =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(1.0)
            .trustLevel(TrustLevel.HIGH)
            .candidates(
                List.of(
                    "com.example.CustomerService#processCustomer(String)",
                    "com.example.CustomerService#processCustomer(Object)"))
            .evidence(Map.of("verified", "true"))
            .build();

    assertThat(evaluator.isResolutionUncertain(resolution)).isTrue();
  }
}
