package com.craftsmanbro.fulcraft.infrastructure.config.validator.llm;

import com.craftsmanbro.fulcraft.config.Config;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/** Validator for OpenAI and OpenAI-compatible LLM configurations. */
public class OpenAiValidator extends AbstractLlmValidator {

  private static final String ENV_OPENAI_API_KEY = "OPENAI_API_KEY";

  private static final String PROVIDER_LABEL = "openai/openai-compatible";

  private static final String MODEL_NAME_KEY_LABEL = "llm.model_name";

  private static final String MSG_OPENAI_MODEL_NAME_REQUIRED =
      "'" + MODEL_NAME_KEY_LABEL + "' is required for " + PROVIDER_LABEL + " provider";

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
    validateApiKey(config, ENV_OPENAI_API_KEY, PROVIDER_LABEL, errors);
    if (StringUtils.isBlank(config.getModelName())) {
      errors.add(MSG_OPENAI_MODEL_NAME_REQUIRED);
    }
  }
}
