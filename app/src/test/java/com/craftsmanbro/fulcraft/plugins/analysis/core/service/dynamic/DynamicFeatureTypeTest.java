package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DynamicFeatureTypeTest {

  @Test
  void wireName_returnsStableNamesInDeclarationOrder() {
    assertThat(Arrays.stream(DynamicFeatureType.values()).map(DynamicFeatureType::wireName))
        .containsExactly(
            "REFLECTION",
            "PROXY",
            "CLASSLOADER",
            "SERVICELOADER",
            "DI",
            "ANNOTATION",
            "SERIALIZATION",
            "INVOKEDYNAMIC");
  }

  @Test
  void wireName_valuesAreUnique() {
    Set<String> wireNames =
        Arrays.stream(DynamicFeatureType.values())
            .map(DynamicFeatureType::wireName)
            .collect(Collectors.toSet());

    assertThat(wireNames).hasSize(DynamicFeatureType.values().length);
  }
}
