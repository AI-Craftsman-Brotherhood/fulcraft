package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LlmDocumentTextUtilsTest {

  @Test
  void isNoneMarker_shouldRecognizeSupportedMarkers() {
    assertThat(LlmDocumentTextUtils.isNoneMarker(" none ")).isTrue();
    assertThat(LlmDocumentTextUtils.isNoneMarker("\u306a\u3057")).isTrue();
    assertThat(LlmDocumentTextUtils.isNoneMarker("")).isFalse();
  }

  @Test
  void normalizeLine_shouldLowercaseAndRemoveBackticks() {
    String normalized = LlmDocumentTextUtils.normalizeLine("  `Hello World`  ");

    assertThat(normalized).isEqualTo("hello world");
  }

  @Test
  void containsMethodToken_shouldMatchWholeMethodTokenOnly() {
    String text = "calls processOrder() and processOrderItem()";

    assertThat(LlmDocumentTextUtils.containsMethodToken(text, "processOrder")).isTrue();
    assertThat(LlmDocumentTextUtils.containsMethodToken(text, "process")).isFalse();
  }

  @Test
  void extractAndNormalizeMethodName_shouldHandleReferenceAndSignatureFormats() {
    String fromRef =
        LlmDocumentTextUtils.extractMethodName(
            "com.example.Service#processOrder(java.lang.String)");
    String fromSignature =
        LlmDocumentTextUtils.normalizeMethodName("public `String` processOrder(java.lang.String)");

    assertThat(fromRef).isEqualTo("processOrder");
    assertThat(fromSignature).isEqualTo("processorder");
  }

  @Test
  void splitTopLevelCsv_shouldIgnoreNestedCommas() {
    List<String> tokens =
        LlmDocumentTextUtils.splitTopLevelCsv(
            "String, Map<String, List<Integer>>, fn(a, b), Optional<Value>");

    assertThat(tokens)
        .containsExactly("String", "Map<String, List<Integer>>", "fn(a, b)", "Optional<Value>");
  }

  @Test
  void emptyIfNull_shouldReturnEmptyStringOnlyForNull() {
    assertThat(LlmDocumentTextUtils.emptyIfNull(null)).isEmpty();
    assertThat(LlmDocumentTextUtils.emptyIfNull("value")).isEqualTo("value");
  }
}
