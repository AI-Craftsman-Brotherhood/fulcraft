package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmRetryPromptBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmRetryPromptBuilderTest {

  @Test
  void buildRetryPrompt_usesJapaneseTemplateAndReplacesPlaceholders() {
    LlmRetryPromptBuilder builder = new LlmRetryPromptBuilder();

    String prompt =
        builder.buildRetryPrompt("ORIGINAL", List.of("reason-one", "reason-two"), 3, true);

    assertThat(prompt).startsWith("ORIGINAL\n\n---\n");
    assertThat(prompt).contains("前回出力に整合性エラーがありました。");
    assertThat(prompt).contains("- reason-one");
    assertThat(prompt).contains("- reason-two");
    assertThat(prompt).contains("- メソッド見出し数は 3 件に一致させること");
    assertThat(prompt).doesNotContain("{{VALIDATION_REASONS}}");
    assertThat(prompt).doesNotContain("{{METHOD_COUNT}}");
  }

  @Test
  void buildRetryPrompt_usesEnglishTemplateAndReplacesPlaceholders() {
    LlmRetryPromptBuilder builder = new LlmRetryPromptBuilder();

    String prompt = builder.buildRetryPrompt("BASE", List.of("missing section"), 5, false);

    assertThat(prompt).startsWith("BASE\n\n---\n");
    assertThat(prompt).contains("The previous output had consistency issues.");
    assertThat(prompt).contains("- missing section");
    assertThat(prompt).contains("- Keep method heading count exactly 5");
    assertThat(prompt).doesNotContain("{{VALIDATION_REASONS}}");
    assertThat(prompt).doesNotContain("{{METHOD_COUNT}}");
  }

  @Test
  void buildRetryPrompt_handlesEmptyValidationReasons() {
    LlmRetryPromptBuilder builder = new LlmRetryPromptBuilder();

    String prompt = builder.buildRetryPrompt("BASE", List.of(), 2, true);

    assertThat(prompt).contains("- メソッド見出し数は 2 件に一致させること");
    assertThat(prompt).doesNotContain("{{VALIDATION_REASONS}}");
  }
}
