package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DynamicFeatureEventTest {

  @Test
  void builder_truncatesSnippetAndCopiesEvidence() {
    Map<String, String> evidence = new HashMap<>();
    evidence.put("key", "value");
    String snippet = "line1\n" + "x".repeat(300);

    DynamicFeatureEvent event =
        DynamicFeatureEvent.builder()
            .featureType(DynamicFeatureType.REFLECTION)
            .featureSubtype("CLASS_FORNAME")
            .filePath("src/main/java/com/example/TestClass.java")
            .classFqn("com.example.TestClass")
            .methodSig("foo()")
            .snippet(snippet)
            .evidence(evidence)
            .severity(DynamicFeatureSeverity.LOW)
            .build();

    evidence.put("key", "changed");

    assertThat(event.evidence()).containsEntry("key", "value");
    assertThat(event.snippet()).contains("\\n");
    assertThat(event.snippet()).endsWith("...");
    assertThat(event.snippet().length()).isLessThanOrEqualTo(200);
  }

  @Test
  void wireName_returnsEnumName() {
    assertThat(DynamicFeatureType.REFLECTION.wireName()).isEqualTo("REFLECTION");
  }

  @Test
  void create_defaultsEvidenceWhenNull() {
    DynamicFeatureEvent event =
        DynamicFeatureEvent.create(
            DynamicFeatureType.CLASSLOADER,
            "LOADCLASS",
            "src/main/java/com/example/Loader.java",
            "com.example.Loader",
            "load()",
            10,
            10,
            null,
            null,
            DynamicFeatureSeverity.MEDIUM);

    assertThat(event.snippet()).isNull();
    assertThat(event.evidence()).isEmpty();
  }

  @Test
  void create_escapesShortSnippetWithoutTruncation() {
    DynamicFeatureEvent event =
        DynamicFeatureEvent.create(
            DynamicFeatureType.REFLECTION,
            "METHOD_INVOKE",
            "src/main/java/com/example/Reflector.java",
            "com.example.Reflector",
            "run()",
            4,
            4,
            "line1\r\nline2",
            Map.of("k", "v"),
            DynamicFeatureSeverity.HIGH);

    assertThat(event.snippet()).isEqualTo("line1\\nline2");
    assertThat(event.evidence()).containsEntry("k", "v");
  }
}
