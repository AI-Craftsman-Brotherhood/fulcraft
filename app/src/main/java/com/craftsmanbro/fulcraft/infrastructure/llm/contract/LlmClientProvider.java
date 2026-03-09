package com.craftsmanbro.fulcraft.infrastructure.llm.contract;

import com.craftsmanbro.fulcraft.config.Config;
import java.util.Locale;

/**
 * Service Provider Interface for LLM client providers.
 *
 * <p>This interface enables a plugin-based architecture for LLM clients. Each provider (OpenAI,
 * Gemini, Anthropic, Local, etc.) implements this interface and registers itself via ServiceLoader
 * or the provider registry.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // Get a provider by name
 * LlmClientProvider provider = LlmProviderRegistry.getProvider("openai");
 * LlmClientPort client = provider.create(config);
 * }</pre>
 *
 * @see LlmProviderRegistry
 * @see LlmClientFactory
 */
public interface LlmClientProvider {

  /**
   * Returns the unique identifier for this provider.
   *
   * <p>This name is used for matching against the {@code llm.provider} configuration value.
   * Provider names should be lowercase and may include aliases (e.g., "openai", "azure-openai").
   *
   * @return The provider identifier (e.g., "openai", "gemini", "anthropic", "local")
   */
  String getProviderName();

  /**
   * Returns any aliases for this provider.
   *
   * <p>Aliases allow flexible configuration. For example, the local provider might accept "local",
   * "ollama", or "vllm".
   *
   * @return An array of alias names (may be empty, never null)
   */
  default String[] getAliases() {
    return new String[0];
  }

  /**
   * Creates an LlmClientPort instance using the given configuration.
   *
   * <p>The implementation should:
   *
   * <ul>
   *   <li>Validate required configuration (e.g., API keys)
   *   <li>Create and configure the underlying HTTP client
   *   <li>Return a ready-to-use LlmClientPort instance
   * </ul>
   *
   * @param config The LLM configuration
   * @return A fully configured LlmClientPort instance
   * @throws IllegalStateException If required configuration is missing or invalid
   */
  LlmClientPort create(Config.LlmConfig config);

  /**
   * Checks if this provider supports the given configuration.
   *
   * <p>This method can be used to validate configuration before attempting to create a client.
   *
   * @param config The LLM configuration to check
   * @return true if the configuration is valid for this provider
   */
  default boolean supports(final Config.LlmConfig config) {
    if (config == null || config.getProvider() == null) {
      return false;
    }
    final String provider = normalizeKey(config.getProvider());
    if (provider.equals(normalizeKey(getProviderName()))) {
      return true;
    }
    for (final String alias : getAliases()) {
      if (alias == null) {
        continue;
      }
      if (provider.equals(normalizeKey(alias))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the priority of this provider for auto-detection.
   *
   * <p>Higher priority providers are preferred when multiple providers could handle a request.
   * Default is 0.
   *
   * @return The priority value (higher = more preferred)
   */
  default int getPriority() {
    return 0;
  }

  /**
   * Checks if this provider requires external network access.
   *
   * <p>This is used by governance policies to enforce external transmission restrictions.
   *
   * @return true if this provider requires external API calls
   */
  default boolean isExternalProvider() {
    return true;
  }

  private static String normalizeKey(final String name) {
    return name.toLowerCase(Locale.ROOT).replace("_", "-");
  }
}
