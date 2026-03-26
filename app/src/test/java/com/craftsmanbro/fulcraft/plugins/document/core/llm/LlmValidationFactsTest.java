package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmValidationFactsTest {

  @Test
  void hasAnyCautions_shouldReturnTrueWhenAnyCautionSetExists() {
    LlmValidationFacts facts =
        factsWith(Set.of("high"), Set.of(), Set.of(), Set.of(), Map.of(), Set.of(), Map.of());

    assertThat(facts.hasAnyCautions()).isTrue();
  }

  @Test
  void hasAnyCautions_shouldReturnTrueWhenDeadCodeOrDuplicateExists() {
    LlmValidationFacts deadCodeFacts =
        factsWith(Set.of(), Set.of("orphan"), Set.of(), Set.of(), Map.of(), Set.of(), Map.of());
    LlmValidationFacts duplicateFacts =
        factsWith(Set.of(), Set.of(), Set.of("dup"), Set.of(), Map.of(), Set.of(), Map.of());

    assertThat(deadCodeFacts.hasAnyCautions()).isTrue();
    assertThat(duplicateFacts.hasAnyCautions()).isTrue();
  }

  @Test
  void hasAnyCautions_shouldReturnFalseWhenNoCautionSetExists() {
    LlmValidationFacts facts =
        factsWith(Set.of(), Set.of(), Set.of(), Set.of(), Map.of(), Set.of(), Map.of());

    assertThat(facts.hasAnyCautions()).isFalse();
  }

  @Test
  void uncertainDynamicMethodNamesFor_shouldFallbackToClassWideWhenMapIsMissing() {
    LlmValidationFacts facts =
        factsWith(Set.of(), Set.of(), Set.of(), Set.of("processcustomer"), null, Set.of(), null);

    assertThat(facts.uncertainDynamicMethodNamesFor("resolve")).containsExactly("processcustomer");
  }

  @Test
  void uncertainDynamicMethodNamesFor_shouldUseMethodScopedMappingWithNormalizedOwner() {
    LlmValidationFacts facts =
        factsWith(
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("classwide"),
            Map.of("resolve", Set.of("scoped")),
            Set.of(),
            Map.of());

    assertThat(
            facts.uncertainDynamicMethodNamesFor("com.example.Service#resolve(java.lang.String)"))
        .containsExactly("scoped");
  }

  @Test
  void uncertainDynamicMethodNamesFor_shouldReturnEmptyWhenFallbackUnavailableOrOwnerBlank() {
    LlmValidationFacts noFallbackFacts =
        factsWith(Set.of(), Set.of(), Set.of(), null, null, Set.of(), Map.of());
    LlmValidationFacts scopedFacts =
        factsWith(
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("classwide"),
            Map.of("resolve", Set.of("scoped")),
            Set.of(),
            Map.of());

    assertThat(noFallbackFacts.uncertainDynamicMethodNamesFor("resolve")).isEmpty();
    assertThat(scopedFacts.uncertainDynamicMethodNamesFor("   ")).isEmpty();
  }

  @Test
  void knownMissingDynamicMethodNamesFor_shouldReturnEmptyWhenNotFound() {
    LlmValidationFacts facts =
        factsWith(
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Map.of(),
            Set.of("legacy"),
            Map.of("resolve", Set.of("legacyScoped")));

    assertThat(facts.knownMissingDynamicMethodNamesFor("unknownMethod")).isEmpty();
    assertThat(facts.knownMissingDynamicMethodNamesFor("resolve")).containsExactly("legacyScoped");
  }

  @Test
  void knownMissingDynamicMethodNamesFor_shouldFallbackToClassWideAndRejectBlankOwner() {
    LlmValidationFacts classWideFallbackFacts =
        factsWith(Set.of(), Set.of(), Set.of(), Set.of(), Map.of(), Set.of("legacy"), null);
    LlmValidationFacts scopedFacts =
        factsWith(
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Map.of(),
            Set.of("legacy"),
            Map.of("resolve", Set.of("legacyScoped")));

    assertThat(classWideFallbackFacts.knownMissingDynamicMethodNamesFor("anyMethod"))
        .containsExactly("legacy");
    assertThat(scopedFacts.knownMissingDynamicMethodNamesFor("   ")).isEmpty();
  }

  private LlmValidationFacts factsWith(
      Set<String> highComplexityMethods,
      Set<String> deadCodeMethods,
      Set<String> duplicateMethods,
      Set<String> uncertainClassWide,
      Map<String, Set<String>> uncertainByMethod,
      Set<String> knownMissingClassWide,
      Map<String, Set<String>> knownMissingByMethod) {
    return new LlmValidationFacts(
        List.of("resolve"),
        highComplexityMethods,
        deadCodeMethods,
        duplicateMethods,
        uncertainClassWide,
        Set.of(),
        uncertainByMethod,
        knownMissingClassWide,
        Set.of(),
        knownMissingByMethod,
        Set.of("resolve"),
        Set.of(),
        Set.of(),
        false,
        false,
        "",
        "com.example.Service",
        "Service",
        Map.of());
  }
}
