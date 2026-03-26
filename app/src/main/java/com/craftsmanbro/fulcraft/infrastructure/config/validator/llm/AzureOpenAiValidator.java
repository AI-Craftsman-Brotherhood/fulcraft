package com.craftsmanbro.fulcraft.infrastructure.config.validator.llm;

import com.craftsmanbro.fulcraft.config.Config;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/** Validator for Azure OpenAI LLM configurations. */
public class AzureOpenAiValidator extends AbstractLlmValidator {

  private static final String KEY_AZURE_DEPLOYMENT = "azure_deployment";
  private static final String KEY_AZURE_API_VERSION = "azure_api_version";
  private static final String ENV_AZURE_OPENAI_API_KEY = "AZURE_OPENAI_API_KEY";
  private static final String MSG_REQUIRED_TEMPLATE =
      "'llm.%s' is required for azure-openai provider";

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

    if (StringUtils.isBlank(config.getAzureDeployment())) {
      errors.add(MSG_REQUIRED_TEMPLATE.formatted(KEY_AZURE_DEPLOYMENT));
    }
    validateApiKey(config, ENV_AZURE_OPENAI_API_KEY, "azure-openai", errors);
    if (StringUtils.isBlank(config.getAzureApiVersion())) {
      errors.add(MSG_REQUIRED_TEMPLATE.formatted(KEY_AZURE_API_VERSION));
    }
    if (StringUtils.isBlank(config.getUrl())) {
      errors.add(MSG_REQUIRED_TEMPLATE.formatted(KEY_URL));
    }
  }
}
