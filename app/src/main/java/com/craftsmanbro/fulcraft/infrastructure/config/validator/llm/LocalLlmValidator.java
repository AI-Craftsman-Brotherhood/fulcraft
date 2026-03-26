package com.craftsmanbro.fulcraft.infrastructure.config.validator.llm;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Validator for Local and Ollama LLM configurations.
 *
 * <p>For local providers, the following settings are required:
 *
 * <ul>
 *   <li>{@code llm.url} - Base URL of the local LLM server (e.g., http://localhost:11434/v1)
 *   <li>{@code llm.model_name} - Name of the model to use
 * </ul>
 *
 * <p>Cloud-specific settings (e.g., {@code api_key}) are ignored for local providers with a warning
 * log.
 */
public class LocalLlmValidator extends AbstractLlmValidator {

  private static final String ERR_MSG_SUFFIX = " is required for local/ollama provider";

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
    // Required fields for local provider
    if (StringUtils.isBlank(config.getUrl())) {
      errors.add("'%s.%s'%s".formatted(LLM, KEY_URL, ERR_MSG_SUFFIX));
    }
    if (StringUtils.isBlank(config.getModelName())) {
      errors.add("'%s.%s'%s".formatted(LLM, KEY_MODEL_NAME, ERR_MSG_SUFFIX));
    }
    // Warn about ignored cloud-specific settings
    warnIgnoredCloudSettings(config);
  }

  /**
   * Logs warnings for cloud-specific settings that are ignored for local providers.
   *
   * @param config The LLM configuration to check.
   */
  private void warnIgnoredCloudSettings(final Config.LlmConfig config) {
    if (StringUtils.isNotBlank(config.getApiKey())) {
      Logger.debug(
          "llm.api_key is set but will be ignored for local provider "
              + "(local LLM servers typically do not require API key authentication)");
    }
    // Azure-specific settings
    if (StringUtils.isNotBlank(config.getAzureDeployment())) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "llm.azure_deployment is ignored for local provider"));
    }
    if (StringUtils.isNotBlank(config.getAzureApiVersion())) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "llm.azure_api_version is ignored for local provider"));
    }
    // Vertex AI settings
    if (StringUtils.isNotBlank(config.getVertexProject())) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "llm.vertex_project is ignored for local provider"));
    }
    if (StringUtils.isNotBlank(config.getVertexLocation())) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "llm.vertex_location is ignored for local provider"));
    }
    if (StringUtils.isNotBlank(config.getVertexModel())) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "llm.vertex_model is ignored for local provider"));
    }
    // AWS Bedrock settings
    if (StringUtils.isNotBlank(config.getAwsAccessKeyId())) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "llm.aws_access_key_id is ignored for local provider"));
    }
    if (StringUtils.isNotBlank(config.getAwsSecretAccessKey())) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "llm.aws_secret_access_key is ignored for local provider"));
    }
    if (StringUtils.isNotBlank(config.getAwsRegion())) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "llm.aws_region is ignored for local provider"));
    }
  }
}
