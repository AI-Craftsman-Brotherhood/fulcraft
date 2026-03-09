package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmListStructureNormalizerTest {

  private final LlmListStructureNormalizer normalizer = new LlmListStructureNormalizer();

  @Test
  void normalizeOrderedListLines_shouldCanonicalizeMixedBulletAndNumberedLines() {
    String raw =
        """
        - 1. first viewpoint
        2. second viewpoint
          continued explanation
        - 4. third viewpoint
        """;

    String normalized = normalizer.normalizeOrderedListLines(raw);

    assertThat(normalized).contains("1. first viewpoint");
    assertThat(normalized).contains("2. second viewpoint");
    assertThat(normalized).contains("3. third viewpoint");
    assertThat(normalized).contains("  continued explanation");
    assertThat(normalized).doesNotContain("- 1.");
    assertThat(normalized).doesNotContain("4. third viewpoint");
  }

  @Test
  void hasNonCanonicalOrderedList_shouldDetectOnlyNonCanonicalCases() {
    String canonical = """
        1. first
        2. second
        """;
    String nonCanonical = """
        - 1. first
        3. second
        """;

    assertThat(normalizer.hasNonCanonicalOrderedList(canonical)).isFalse();
    assertThat(normalizer.hasNonCanonicalOrderedList(nonCanonical)).isTrue();
  }
}
