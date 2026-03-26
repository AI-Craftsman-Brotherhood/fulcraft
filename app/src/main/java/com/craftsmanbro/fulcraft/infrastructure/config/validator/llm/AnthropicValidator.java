package com.craftsmanbro.fulcraft.infrastructure.config.validator.llm;

import com.craftsmanbro.fulcraft.config.Config;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/** Validator for Anthropic (Claude) LLM configurations. */
public class AnthropicValidator extends AbstractLlmValidator {

  private static final String ENV_ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";
  private static final String MSG_MODEL_NAME_REQUIRED =
      "'llm.model_name' is required for anthropic provider";

  @Override
  public void validate(final Config.LlmConfig config, final List<String> errors) {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "config"));
    Objects.requireNonNull(
        errors,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "errors"));

    if (StringUtils.isBlank(config.getModelName())) {
      errors.add(MSG_MODEL_NAME_REQUIRED);
    }
    validateApiKey(config, ENV_ANTHROPIC_API_KEY, "anthropic", errors);
  }
}
