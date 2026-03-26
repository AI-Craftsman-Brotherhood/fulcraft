package com.craftsmanbro.fulcraft.infrastructure.llm.safety.redaction.detector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding;
import org.junit.jupiter.api.Test;

class FindingTest {

  @Test
  void overlaps_detectsIntersectingRanges() {
    Finding first = new Finding("A", 0, 5, 0.5, "alpha", "rule:a");
    Finding second = new Finding("B", 4, 7, 0.6, "beta", "rule:b");
    Finding third = new Finding("C", 6, 9, 0.6, "gamma", "rule:c");

    assertThat(first.overlaps(second)).isTrue();
    assertThat(first.overlaps(third)).isFalse();
  }

  @Test
  void maskedSnippet_redactsShortSnippets() {
    Finding shortSnippet = new Finding("A", 0, 3, 0.5, "abc", "rule:a");
    Finding longSnippet = new Finding("B", 0, 10, 0.5, "ABCDEFGHIJ", "rule:b");

    assertThat(shortSnippet.maskedSnippet()).isEqualTo("[REDACTED]");
    assertThat(longSnippet.maskedSnippet()).isEqualTo("AB***IJ");
  }

  @Test
  void constructor_rejectsInvalidArguments() {
    assertThatThrownBy(() -> new Finding("A", -1, 1, 0.5, "x", "rule:a"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Finding("A", 2, 1, 0.5, "x", "rule:a"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Finding("A", 0, 1, 1.5, "x", "rule:a"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void certain_setsFullConfidenceAndExpectedLength() {
    Finding finding = Finding.certain("TOKEN", 2, 8, "secret", "rule:token");

    assertThat(finding.confidence()).isEqualTo(1.0);
    assertThat(finding.length()).isEqualTo(6);
  }

  @Test
  void constructor_rejectsNullRequiredFields() {
    assertThatThrownBy(() -> new Finding(null, 0, 1, 0.5, "x", "rule:a"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Finding("A", 0, 1, 0.5, "x", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void toString_containsKeyFields() {
    Finding finding = new Finding("EMAIL", 3, 10, 0.95, "a@b.com", "regex:email");

    assertThat(finding.toString()).contains("type=EMAIL", "pos=[3,10)", "rule=regex:email");
  }
}
