package com.craftsmanbro.fulcraft.plugins.document.core.llm.context;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Builds retry prompts that include validation feedback and regeneration constraints. */
public final class LlmRetryPromptBuilder {

  private static final String RETRY_TEMPLATE_JA_RESOURCE =
      "prompts/document/llm_retry_notes_ja.txt";

  private static final String RETRY_TEMPLATE_EN_RESOURCE =
      "prompts/document/llm_retry_notes_en.txt";

  private static final String VALIDATION_REASONS_PLACEHOLDER = "{{VALIDATION_REASONS}}";

  private static final String METHOD_COUNT_PLACEHOLDER = "{{METHOD_COUNT}}";

  public String buildRetryPrompt(
      final String originalPrompt,
      final List<String> validationReasons,
      final int methodCount,
      final boolean japanese) {
    final StringBuilder sb = new StringBuilder(originalPrompt);
    sb.append("\n\n---\n");
    sb.append(buildRetryNotes(validationReasons, methodCount, japanese));
    return sb.toString();
  }

  private String buildRetryNotes(
      final List<String> validationReasons, final int methodCount, final boolean japanese) {
    final String template =
        loadRetryTemplate(japanese ? RETRY_TEMPLATE_JA_RESOURCE : RETRY_TEMPLATE_EN_RESOURCE);
    return template
        .replace(VALIDATION_REASONS_PLACEHOLDER, formatValidationReasons(validationReasons))
        .replace(METHOD_COUNT_PLACEHOLDER, String.valueOf(methodCount));
  }

  private String formatValidationReasons(final List<String> validationReasons) {
    if (validationReasons == null || validationReasons.isEmpty()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    for (final String reason : validationReasons) {
      sb.append("- ").append(reason).append("\n");
    }
    return sb.toString();
  }

  private String loadRetryTemplate(final String resourcePath) {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (input == null) {
        throw new IllegalStateException(
            MessageSource.getMessage(
                "document.resource.llm_retry_template.not_found", resourcePath));
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(
          MessageSource.getMessage(
              "document.resource.llm_retry_template.load_failed", resourcePath),
          e);
    }
  }
}
