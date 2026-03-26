package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RedactionResultTest {

  @Test
  void unchanged_shouldHaveNoFindings() {
    RedactionResult result = RedactionResult.unchanged("text");

    assertThat(result.redactedText()).isEqualTo("text");
    assertThat(result.report()).isEqualTo(RedactionReport.EMPTY);
    assertThat(result.findings()).isEmpty();
    assertThat(result.hasFindings()).isFalse();
  }

  @Test
  void summary_whenNoFindings_returnsCleanMessage() {
    RedactionResult result = RedactionResult.unchanged("text");

    assertThat(result.summary()).isEqualTo("No sensitive data detected");
  }

  @Test
  void exceedsBlockThreshold_usesMaxConfidence() {
    RedactionResult result = new RedactionResult("text", new RedactionReport(1, 0, 0, 0, 0));

    assertThat(result.hasFindings()).isTrue();
    assertThat(result.exceedsBlockThreshold(0.9)).isTrue();
  }

  @Test
  void constructor_shouldRejectInvalidMaxConfidence() {
    assertThatThrownBy(() -> new RedactionResult("text", RedactionReport.EMPTY, List.of(), 1.1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_shouldRejectNullRequiredFields() {
    assertThatThrownBy(() -> new RedactionResult(null, RedactionReport.EMPTY, List.of(), 0.0))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RedactionResult("text", null, List.of(), 0.0))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RedactionResult("text", RedactionReport.EMPTY, null, 0.0))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructor_shouldDefensivelyCopyFindings() {
    var findings =
        new java.util.ArrayList<
            com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding>();
    findings.add(
        new com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding(
            "EMAIL", 0, 5, 0.9, "a@b", "regex:email"));

    RedactionResult result = new RedactionResult("text", RedactionReport.EMPTY, findings, 0.9);
    findings.clear();

    assertThat(result.findings()).hasSize(1);
  }

  @Test
  void legacyConstructor_withNullReport_usesEmptyReport() {
    RedactionResult result = new RedactionResult("text", null);

    assertThat(result.report()).isEqualTo(RedactionReport.EMPTY);
    assertThat(result.maxConfidence()).isEqualTo(0.0);
  }

  @Test
  void summary_usesReportCountWhenFindingsAreEmpty() {
    RedactionResult result = new RedactionResult("text", new RedactionReport(1, 0, 0, 0, 0));

    assertThat(result.findings()).isEmpty();
    assertThat(result.summary()).contains("Detected 1 findings");
  }
}
