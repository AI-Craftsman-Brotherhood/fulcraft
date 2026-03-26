package com.craftsmanbro.fulcraft.plugins.document.core.llm.context;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Loads LLM document prompt templates from classpath resources. */
public final class LlmPromptTemplateLoader {

  private static final String PROMPT_TEMPLATE_JA_RESOURCE =
      "prompts/document/llm_detail_prompt_ja.md";
  private static final String PROMPT_TEMPLATE_EN_RESOURCE =
      "prompts/document/llm_detail_prompt_en.md";

  public String loadPromptTemplate() {
    final Locale locale = MessageSource.getLocale();
    final boolean japanese = locale == null || "ja".equalsIgnoreCase(locale.getLanguage());
    final String resourcePath =
        japanese ? PROMPT_TEMPLATE_JA_RESOURCE : PROMPT_TEMPLATE_EN_RESOURCE;
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (input == null) {
        throw new IllegalStateException(
            MessageSource.getMessage(
                "document.resource.llm_prompt_template.not_found", resourcePath));
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(
          MessageSource.getMessage(
              "document.resource.llm_prompt_template.load_failed", resourcePath),
          e);
    }
  }
}
