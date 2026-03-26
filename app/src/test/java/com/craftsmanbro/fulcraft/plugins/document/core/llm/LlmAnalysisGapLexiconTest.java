package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LlmAnalysisGapLexiconTest {

  @Test
  void containsAnalysisGapStatement_shouldDetectSourceExcerptShownVariant() {
    String line =
        "A loop guard indicates break (the exact downstream effects are not fully shown in the provided source excerpt)";

    assertThat(LlmAnalysisGapLexicon.containsAnalysisGapStatement(line)).isTrue();
    assertThat(LlmAnalysisGapLexicon.isAnalysisGapLine("- " + line)).isTrue();
  }

  @Test
  void containsAnalysisGapStatement_shouldDetectLegacyExcerptVariant() {
    String line = "Details beyond the provided excerpt are not fully available.";

    assertThat(LlmAnalysisGapLexicon.containsAnalysisGapStatement(line)).isTrue();
    assertThat(LlmAnalysisGapLexicon.isAnalysisGapLine("- " + line)).isTrue();
  }

  @Test
  void containsAnalysisGapStatement_shouldIgnoreDeterministicFactLine() {
    String line = "If quantity <= 0, the method adds an error and continues.";

    assertThat(LlmAnalysisGapLexicon.containsAnalysisGapStatement(line)).isFalse();
    assertThat(LlmAnalysisGapLexicon.isAnalysisGapLine("- " + line)).isFalse();
  }

  @Test
  void containsAnalysisGapStatement_shouldReturnFalseForNullOrBlankText() {
    assertThat(LlmAnalysisGapLexicon.containsAnalysisGapStatement(null)).isFalse();
    assertThat(LlmAnalysisGapLexicon.containsAnalysisGapStatement("   ")).isFalse();
  }

  @Test
  void isAnalysisGapLine_shouldIgnoreNoneMarkersAndBlankLine() {
    assertThat(LlmAnalysisGapLexicon.isAnalysisGapLine("- none")).isFalse();
    assertThat(LlmAnalysisGapLexicon.isAnalysisGapLine("- なし")).isFalse();
    assertThat(LlmAnalysisGapLexicon.isAnalysisGapLine("   ")).isFalse();
  }

  @Test
  void analysisGapPattern_shouldExposeReusablePattern() {
    Pattern pattern = LlmAnalysisGapLexicon.analysisGapPattern();

    assertThat(pattern.matcher("analysis data is missing").find()).isTrue();
    assertThat(pattern.matcher("deterministic execution path").find()).isFalse();
  }
}
