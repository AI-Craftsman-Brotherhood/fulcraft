package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DynamicResolutionTest {

  @Test
  void builder_derivesTrustLevelFromConfidenceWhenNotExplicitlySet() {
    DynamicResolution res = DynamicResolution.builder().confidence(0.6).build();

    assertThat(res.trustLevel()).isEqualTo(TrustLevel.MEDIUM);
  }

  @Test
  void builder_allowsExplicitTrustLevelOverride() {
    DynamicResolution res =
        DynamicResolution.builder().confidence(0.95).trustLevel(TrustLevel.LOW).build();

    assertThat(res.trustLevel()).isEqualTo(TrustLevel.LOW);
  }

  @Test
  void builder_setsRuleId() {
    DynamicResolution res =
        DynamicResolution.builder()
            .subtype(DynamicResolution.CLASS_FORNAME_LITERAL)
            .ruleId(ResolutionRuleId.LITERAL)
            .build();

    assertThat(res.ruleId()).isEqualTo(ResolutionRuleId.LITERAL);
  }

  @Test
  void builder_defaultsRuleIdToNull() {
    DynamicResolution res =
        DynamicResolution.builder().subtype(DynamicResolution.BRANCH_CANDIDATES).build();

    assertThat(res.ruleId()).isNull();
  }

  @Test
  void ruleIds_haveDescriptions() {
    for (ResolutionRuleId ruleId : ResolutionRuleId.values()) {
      assertThat(ruleId.getDescription()).isNotBlank();
    }
  }

  @Test
  void compactConstructor_defaultsTypeTrustAndCollections() {
    DynamicResolution resolution =
        new DynamicResolution(
            " ",
            DynamicResolution.METHOD_RESOLVE,
            "src/main/java/Foo.java",
            "com.example.Foo",
            "bar()",
            42,
            "com.example.Bar",
            "baz()",
            null,
            null,
            0.95,
            null,
            null,
            null,
            null);

    assertThat(resolution.type()).isEqualTo(DynamicResolution.TYPE_RESOLUTION);
    assertThat(resolution.trustLevel()).isEqualTo(TrustLevel.HIGH);
    assertThat(resolution.providers()).isEmpty();
    assertThat(resolution.candidates()).isEmpty();
    assertThat(resolution.evidence()).isEmpty();
    assertThatThrownBy(() -> resolution.providers().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> resolution.candidates().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> resolution.evidence().put("k", "v"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void compactConstructor_preservesExplicitTypeAndTrustLevel() {
    DynamicResolution resolution =
        new DynamicResolution(
            "CUSTOM_TYPE",
            DynamicResolution.METHOD_RESOLVE,
            null,
            null,
            null,
            -1,
            null,
            null,
            List.of(),
            List.of(),
            1.0,
            TrustLevel.LOW,
            null,
            null,
            Map.of());

    assertThat(resolution.type()).isEqualTo("CUSTOM_TYPE");
    assertThat(resolution.trustLevel()).isEqualTo(TrustLevel.LOW);
  }

  @Test
  void builder_makesDefensiveCopiesOfCollections() {
    List<String> providers = new ArrayList<>(List.of("p1"));
    List<String> candidates = new ArrayList<>(List.of("c1"));
    Map<String, String> evidence = new HashMap<>(Map.of("verified", "true"));

    DynamicResolution resolution =
        DynamicResolution.builder()
            .providers(providers)
            .candidates(candidates)
            .evidence(evidence)
            .build();

    providers.add("p2");
    candidates.add("c2");
    evidence.put("new", "value");

    assertThat(resolution.providers()).containsExactly("p1");
    assertThat(resolution.candidates()).containsExactly("c1");
    assertThat(resolution.evidence()).containsEntry("verified", "true").hasSize(1);
  }
}
