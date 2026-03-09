package com.craftsmanbro.fulcraft.infrastructure.llm.contract;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import java.util.Objects;

public interface LlmClientPort {

  /**
   * Generate test code for the given prompt using the provided LLM configuration.
   *
   * <p>The client implementation may honor provider/model/timeouts/etc from {@code llmConfig}.
   *
   * @param prompt The prompt to send to the LLM.
   * @param llmConfig The configuration for the LLM client.
   * @return The generated test code (e.g. source code string).
   */
  String generateTest(String prompt, Config.LlmConfig llmConfig);

  /**
   * Backward-compatible overload. Prefer {@link #generateTest(String, Config.LlmConfig)}.
   *
   * @param prompt The prompt to send to the LLM.
   * @param model The model name to use.
   * @return The generated test code.
   * @deprecated Use the configuration-based overload.
   */
  @Deprecated(since = "1.0.0", forRemoval = true)
  default String generateTest(final String prompt, final String model) {
    Objects.requireNonNull(
        prompt,
        MessageSource.getMessage(
            "infra.common.error.argument_null", "Prompt must not be null"));
    Objects.requireNonNull(
        model,
        MessageSource.getMessage(
            "infra.common.error.argument_null", "Model must not be null"));
    final Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setModelName(model);
    final ProviderProfile providerProfile = profile();
    if (providerProfile != null) {
      llmConfig.setProvider(providerProfile.providerName());
    }
    return generateTest(prompt, llmConfig);
  }

  /**
   * Checks if the LLM provider is reachable and healthy (e.g. API key valid, server running).
   *
   * @return true if healthy, false otherwise.
   */
  default boolean isHealthy() {
    // Default to true for backward compatibility or simple mocks.
    return true;
  }

  /**
   * Returns the profile of this provider, including its name and capabilities.
   *
   * @return a {@link ProviderProfile} describing this client.
   */
  ProviderProfile profile();

  /**
   * Clears any per-thread context retained by the client (e.g., last token usage).
   *
   * <p>Callers should invoke this in a {@code finally} block after {@link #generateTest} to avoid
   * ThreadLocal leaks in pooled threads.
   */
  default void clearContext() {
    // No-op by default for backward compatibility.
  }
}
