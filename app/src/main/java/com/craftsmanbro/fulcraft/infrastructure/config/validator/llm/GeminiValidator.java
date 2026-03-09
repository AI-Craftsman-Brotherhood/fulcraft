package com.craftsmanbro.fulcraft.infrastructure.config.validator.llm;

import com.craftsmanbro.fulcraft.config.Config;
import java.util.List;
import java.util.Objects;

/** Validator for Gemini LLM configurations. */
public class GeminiValidator extends AbstractLlmValidator {

  private static final String ENV_GEMINI_API_KEY = "GEMINI_API_KEY";

  @Override
  public void validate(final Config.LlmConfig config, final List<String> errors) {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "llmConfig"));
    Objects.requireNonNull(
        errors,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "errors"));
    validateApiKey(config, ENV_GEMINI_API_KEY, "gemini", errors);
  }
}
