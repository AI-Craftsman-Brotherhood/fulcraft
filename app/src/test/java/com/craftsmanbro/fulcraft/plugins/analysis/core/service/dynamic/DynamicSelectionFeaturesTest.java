package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DynamicSelectionFeaturesTest {

  @Test
  void from_nullMethod_returnsSafeDefaults() {
    DynamicSelectionFeatures features = DynamicSelectionFeatures.from(null);

    assertThat(features.minConfidence()).isEqualTo(1.0);
    assertThat(features.unresolvedCount()).isEqualTo(0);
    assertThat(features.externalOrNotFoundCount()).isEqualTo(0);
    assertThat(features.hasServiceLoader()).isFalse();
    assertThat(features.serviceLoaderMinConfidence()).isEqualTo(1.0);
    assertThat(features.serviceLoaderCandidateCount()).isEqualTo(0);
    assertThat(features.highCount()).isEqualTo(0);
    assertThat(features.mediumCount()).isEqualTo(0);
    assertThat(features.lowCount()).isEqualTo(0);
  }

  @Test
  void from_dynamicFeatureCounts_withoutResolutions_usesFallbacks() {
    MethodInfo method = new MethodInfo();
    method.setDynamicFeatureHigh(1);
    method.setDynamicFeatureMedium(1);
    method.setDynamicFeatureLow(1);
    method.setDynamicFeatureHasServiceLoader(true);
    method.setDynamicResolutions(List.of());

    DynamicSelectionFeatures features = DynamicSelectionFeatures.from(method);

    assertThat(features.minConfidence()).isEqualTo(0.79);
    assertThat(features.unresolvedCount()).isEqualTo(3);
    assertThat(features.externalOrNotFoundCount()).isEqualTo(0);
    assertThat(features.hasServiceLoader()).isTrue();
    assertThat(features.serviceLoaderMinConfidence()).isEqualTo(0.79);
    assertThat(features.serviceLoaderCandidateCount()).isEqualTo(0);
    assertThat(features.highCount()).isEqualTo(1);
    assertThat(features.mediumCount()).isEqualTo(1);
    assertThat(features.lowCount()).isEqualTo(1);
  }

  @Test
  void from_resolutions_calculatesConfidenceAndCounts() {
    MethodInfo method = new MethodInfo();
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .subtype(DynamicResolution.SERVICELOADER_PROVIDERS)
                .providers(List.of("a", "b"))
                .confidence(0.6)
                .resolvedClassFqn("")
                .build(),
            DynamicResolution.builder()
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.95)
                .resolvedClassFqn("com.example.Foo")
                .build()));

    DynamicSelectionFeatures features = DynamicSelectionFeatures.from(method);

    assertThat(features.minConfidence()).isEqualTo(0.6);
    assertThat(features.unresolvedCount()).isEqualTo(1);
    assertThat(features.externalOrNotFoundCount()).isEqualTo(1);
    assertThat(features.hasServiceLoader()).isTrue();
    assertThat(features.serviceLoaderMinConfidence()).isEqualTo(0.6);
    assertThat(features.serviceLoaderCandidateCount()).isEqualTo(2);
    assertThat(features.highCount()).isEqualTo(1);
    assertThat(features.mediumCount()).isEqualTo(1);
    assertThat(features.lowCount()).isEqualTo(0);
  }

  @Test
  void from_methodWithoutSignals_returnsSafeDefaults() {
    MethodInfo method = new MethodInfo();
    method.setDynamicResolutions(List.of());

    DynamicSelectionFeatures features = DynamicSelectionFeatures.from(method);

    assertThat(features.minConfidence()).isEqualTo(1.0);
    assertThat(features.unresolvedCount()).isZero();
    assertThat(features.externalOrNotFoundCount()).isZero();
    assertThat(features.hasServiceLoader()).isFalse();
    assertThat(features.serviceLoaderMinConfidence()).isEqualTo(1.0);
    assertThat(features.serviceLoaderCandidateCount()).isZero();
    assertThat(features.highCount()).isZero();
    assertThat(features.mediumCount()).isZero();
    assertThat(features.lowCount()).isZero();
  }

  @Test
  void from_resolutions_normalizesOutOfRangeConfidenceAndSkipsNullResolution() {
    MethodInfo method = new MethodInfo();
    DynamicResolution serviceLoaderNaN =
        DynamicResolution.builder()
            .subtype(DynamicResolution.SERVICELOADER_PROVIDERS)
            .providers(List.of("a"))
            .confidence(Double.NaN)
            .resolvedClassFqn("")
            .build();
    DynamicResolution serviceLoaderMedium =
        DynamicResolution.builder()
            .subtype(DynamicResolution.SERVICELOADER_PROVIDERS)
            .providers(List.of("b", "c"))
            .confidence(0.8)
            .resolvedClassFqn("com.example.Provider")
            .build();
    DynamicResolution negativeConfidence =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(-5.0)
            .resolvedClassFqn(" ")
            .build();
    DynamicResolution tooHighConfidence =
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(5.0)
            .resolvedClassFqn("com.example.Foo")
            .build();

    method.setDynamicResolutions(
        new ArrayList<>(
            Arrays.asList(
                null,
                serviceLoaderNaN,
                serviceLoaderMedium,
                negativeConfidence,
                tooHighConfidence)));

    DynamicSelectionFeatures features = DynamicSelectionFeatures.from(method);

    assertThat(features.minConfidence()).isEqualTo(0.0);
    assertThat(features.unresolvedCount()).isEqualTo(2);
    assertThat(features.externalOrNotFoundCount()).isEqualTo(2);
    assertThat(features.hasServiceLoader()).isTrue();
    assertThat(features.serviceLoaderMinConfidence()).isEqualTo(0.0);
    assertThat(features.serviceLoaderCandidateCount()).isEqualTo(3);
    assertThat(features.highCount()).isEqualTo(1);
    assertThat(features.mediumCount()).isEqualTo(1);
    assertThat(features.lowCount()).isEqualTo(2);
  }
}
