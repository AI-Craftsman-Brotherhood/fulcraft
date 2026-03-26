package com.craftsmanbro.fulcraft.infrastructure.llm.safety.redaction.detector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionResult;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetectionResultTest {

  @Test
  void of_shouldCalculateMaxConfidence() {
    Finding low = new Finding("A", 0, 1, 0.2, "a", "rule:a");
    Finding high = new Finding("B", 2, 3, 0.9, "b", "rule:b");

    DetectionResult result = DetectionResult.of(List.of(low, high));

    assertThat(result.maxConfidence()).isEqualTo(0.9);
    assertThat(result.findingsCount()).isEqualTo(2);
  }

  @Test
  void merge_shouldCombineFindings() {
    Finding first = new Finding("A", 0, 1, 0.2, "a", "rule:a");
    Finding second = new Finding("B", 2, 3, 0.6, "b", "rule:b");

    DetectionResult left = DetectionResult.of(List.of(first));
    DetectionResult right = DetectionResult.of(List.of(second));

    DetectionResult merged = left.merge(right);

    assertThat(merged.findings()).containsExactly(first, second);
    assertThat(merged.maxConfidence()).isEqualTo(0.6);
  }

  @Test
  void constructor_shouldRejectMismatchedMaxConfidence() {
    Finding finding = new Finding("A", 0, 1, 0.7, "a", "rule:a");

    assertThatThrownBy(() -> new DetectionResult(List.of(finding), 0.2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void of_shouldReturnEmptyForNullOrEmptyFindings() {
    assertThat(DetectionResult.of(null)).isSameAs(DetectionResult.EMPTY);
    assertThat(DetectionResult.of(List.of())).isSameAs(DetectionResult.EMPTY);
  }

  @Test
  void merge_shouldShortCircuitForNullAndEmptyResults() {
    Finding finding = new Finding("A", 0, 1, 0.7, "a", "rule:a");
    DetectionResult nonEmpty = DetectionResult.of(List.of(finding));

    assertThat(nonEmpty.merge(null)).isSameAs(nonEmpty);
    assertThat(nonEmpty.merge(DetectionResult.EMPTY)).isSameAs(nonEmpty);
    assertThat(DetectionResult.EMPTY.merge(nonEmpty)).isSameAs(nonEmpty);
  }

  @Test
  void constructor_shouldDefensivelyCopyFindings() {
    var findings = new java.util.ArrayList<Finding>();
    findings.add(new Finding("A", 0, 1, 0.4, "a", "rule:a"));

    DetectionResult result = new DetectionResult(findings, 0.4);
    findings.add(new Finding("B", 2, 3, 0.5, "b", "rule:b"));

    assertThat(result.findings()).hasSize(1);
  }

  @Test
  void constructor_shouldRejectOutOfRangeConfidence() {
    assertThatThrownBy(() -> new DetectionResult(List.of(), -0.1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DetectionResult(List.of(), 1.1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_shouldRejectNullFindings() {
    assertThatThrownBy(() -> new DetectionResult(null, 0.0))
        .isInstanceOf(NullPointerException.class);
  }
}
