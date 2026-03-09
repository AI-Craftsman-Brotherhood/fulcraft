package com.craftsmanbro.fulcraft.infrastructure.config.validator.llm;

/** Abstract base class for LLM validators containing common validation logic. */
public abstract class AbstractLlmValidator implements LlmConfigValidator {

  protected static final String LLM = "llm";
  protected static final String KEY_MODEL_NAME = "model_name";
  protected static final String KEY_URL = "url";

  protected static final String MSG_API_KEY_REQUIRED = "'llm.api_key' is required.";

  /**
   * Retrieves an environment variable.
   *
   * <p>This method exists to allow partial mocking of environment variables in unit tests.
   *
   * @param name The name of the environment variable.
   * @return The value of the environment variable, or null if not defined.
   */
  protected String getEnv(final String name) {
    return System.getenv(name);
  }

  /**
   * Validates that either the config API key or the provider-specific environment variable is set.
   *
   * @param config The LLM configuration
   * @param envVarName The name of the provider-specific environment variable
   * @param providerName The name of the provider (for the error message)
   * @param errors The list of errors
   */
  protected void validateApiKey(
      final com.craftsmanbro.fulcraft.config.Config.LlmConfig config,
      final String envVarName,
      final String providerName,
      final java.util.List<String> errors) {
    final String envApiKey = getEnv(envVarName);
    if (org.apache.commons.lang3.StringUtils.isBlank(envApiKey)
        && org.apache.commons.lang3.StringUtils.isBlank(config.getApiKey())) {
      errors.add(
          String.format(
              "'llm.api_key' or %s env var is required for %s provider", envVarName, providerName));
    }
  }
}
