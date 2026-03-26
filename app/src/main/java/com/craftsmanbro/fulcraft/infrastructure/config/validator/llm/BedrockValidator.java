package com.craftsmanbro.fulcraft.infrastructure.config.validator.llm;

import com.craftsmanbro.fulcraft.config.Config;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/** Validator for Bedrock LLM configurations. */
public class BedrockValidator extends AbstractLlmValidator {

  private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";

  private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";

  private static final String ENV_AWS_REGION = "AWS_REGION";

  private static final String ENV_AWS_DEFAULT_REGION = "AWS_DEFAULT_REGION";

  private static final String MSG_BEDROCK_ENV_REQUIRED =
      " env var is required for bedrock provider";

  @Override
  public void validate(final Config.LlmConfig config, final List<String> errors) {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "config must not be null"));
    Objects.requireNonNull(
        errors,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "errors must not be null"));
    if (StringUtils.isBlank(config.getModelName())) {
      errors.add("'llm.model_name' (model id) is required for bedrock provider");
    }
    validateCredentials(config, errors);
    validateRegion(config, errors);
  }

  private void validateCredentials(final Config.LlmConfig config, final List<String> errors) {
    requireConfigOrEnv(
        "'llm.aws_access_key_id'", config.getAwsAccessKeyId(), ENV_AWS_ACCESS_KEY_ID, errors);
    requireConfigOrEnv(
        "'llm.aws_secret_access_key'",
        config.getAwsSecretAccessKey(),
        ENV_AWS_SECRET_ACCESS_KEY,
        errors);
  }

  private void validateRegion(final Config.LlmConfig config, final List<String> errors) {
    requireConfigOrEnv(
        "'llm.aws_region'", config.getAwsRegion(), ENV_AWS_REGION, ENV_AWS_DEFAULT_REGION, errors);
  }

  private void requireConfigOrEnv(
      final String displayKey,
      final String configValue,
      final String envName,
      final List<String> errors) {
    if (StringUtils.isBlank(configValue) && StringUtils.isBlank(getEnv(envName))) {
      errors.add(displayKey + " or " + envName + MSG_BEDROCK_ENV_REQUIRED);
    }
  }

  private void requireConfigOrEnv(
      final String displayKey,
      final String configValue,
      final String envName1,
      final String envName2,
      final List<String> errors) {
    if (StringUtils.isBlank(configValue)
        && StringUtils.isBlank(getEnv(envName1))
        && StringUtils.isBlank(getEnv(envName2))) {
      errors.add(displayKey + " or " + envName1 + "/" + envName2 + MSG_BEDROCK_ENV_REQUIRED);
    }
  }
}
