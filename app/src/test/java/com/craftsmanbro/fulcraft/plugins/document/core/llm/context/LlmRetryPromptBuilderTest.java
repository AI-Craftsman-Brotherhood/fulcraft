package com.craftsmanbro.fulcraft.plugins.document.core.llm.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmRetryPromptBuilderTest {

  private final LlmRetryPromptBuilder builder = new LlmRetryPromptBuilder();

  @Test
  void buildRetryPrompt_shouldUseJapaneseTemplateAndRenderValidationReasons() {
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
  void buildRetryPrompt_shouldUseEnglishTemplateWhenJapaneseFlagIsFalse() {
    String prompt = builder.buildRetryPrompt("BASE", List.of("missing section"), 5, false);

    assertThat(prompt).startsWith("BASE\n\n---\n");
    assertThat(prompt).contains("The previous output had consistency issues.");
    assertThat(prompt).contains("- missing section");
    assertThat(prompt).contains("- Keep method heading count exactly 5");
    assertThat(prompt).doesNotContain("{{VALIDATION_REASONS}}");
    assertThat(prompt).doesNotContain("{{METHOD_COUNT}}");
  }

  @Test
  void buildRetryPrompt_shouldHandleNullAndEmptyValidationReasons() {
    String nullReasonsPrompt = builder.buildRetryPrompt("BASE", null, 1, true);
    String emptyReasonsPrompt = builder.buildRetryPrompt("BASE", List.of(), 2, false);

    assertThat(nullReasonsPrompt).contains("- メソッド見出し数は 1 件に一致させること");
    assertThat(nullReasonsPrompt).doesNotContain("{{VALIDATION_REASONS}}");
    assertThat(emptyReasonsPrompt).contains("- Keep method heading count exactly 2");
    assertThat(emptyReasonsPrompt).doesNotContain("{{VALIDATION_REASONS}}");
  }

  @Test
  void loadRetryTemplate_shouldThrowWhenResourceIsMissing() throws Exception {
    Method loadRetryTemplate =
        LlmRetryPromptBuilder.class.getDeclaredMethod("loadRetryTemplate", String.class);
    loadRetryTemplate.setAccessible(true);

    Throwable thrown =
        catchThrowable(
            () -> loadRetryTemplate.invoke(builder, "prompts/document/missing_retry_template.txt"));

    assertThat(thrown).isInstanceOf(InvocationTargetException.class);
    assertThat(thrown.getCause()).isInstanceOf(IllegalStateException.class);
    assertThat(thrown.getCause().getMessage())
        .contains("prompts/document/missing_retry_template.txt");
  }
}
