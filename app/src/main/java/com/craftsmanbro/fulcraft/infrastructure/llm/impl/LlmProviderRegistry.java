package com.craftsmanbro.fulcraft.infrastructure.llm.impl;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientProvider;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.AnthropicLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.AzureOpenAiLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.BedrockLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.GeminiLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.LocalLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.MockLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.OpenAiLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.VertexAiLlmClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for LLM client providers.
 *
 * <p>This class manages the registration and lookup of LLM providers using a plugin-based
 * architecture. Providers can be registered:
 *
 * <ul>
 *   <li>Automatically via ServiceLoader
 *   <li>Programmatically via {@link #registerProvider(LlmClientProvider)}
 *   <li>Using built-in defaults
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Get provider by name
 * LlmClientProvider provider = LlmProviderRegistry.getProvider("openai");
 *
 * // Create client
 * LlmClientPort client = provider.create(config);
 *
 * // Or use the factory which applies decorators
 * LlmClientPort client = LlmClientFactory.create(config);
 * }</pre>
 *
 * @see LlmClientProvider
 * @see LlmClientFactory
 */
public final class LlmProviderRegistry {

  private static final Map<String, LlmClientProvider> PROVIDERS = new ConcurrentHashMap<>();

  private static volatile boolean initialized;

  private LlmProviderRegistry() {
    // Utility class
  }

  /**
   * Gets a provider by name or alias.
   *
   * @param providerName The provider name (case-insensitive)
   * @return The provider, or empty if not found
   */
  public static Optional<LlmClientProvider> findProvider(final String providerName) {
    ensureInitialized();
    if (providerName == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(PROVIDERS.get(normalizeKey(providerName)));
  }

  /**
   * Gets a provider by name or alias, throwing if not found.
   *
   * @param providerName The provider name (case-insensitive)
   * @return The provider
   * @throws IllegalStateException if the provider is not found
   */
  public static LlmClientProvider getProvider(final String providerName) {
    return findProvider(providerName)
        .orElseThrow(() -> new IllegalStateException("Unsupported llm.provider: " + providerName));
  }

  /**
   * Finds a provider that supports the given configuration.
   *
   * @param config The LLM configuration
   * @return The matching provider, or empty if none found
   */
  public static Optional<LlmClientProvider> findProviderForConfig(final Config.LlmConfig config) {
    ensureInitialized();
    if (config == null) {
      return Optional.empty();
    }
    // First try direct lookup by provider name
    final String providerName = config.getProvider();
    if (providerName != null) {
      final Optional<LlmClientProvider> directProvider = findProvider(providerName);
      if (directProvider.isPresent()) {
        return directProvider;
      }
    }
    // Fall back to checking all providers
    return PROVIDERS.values().stream()
        .filter(p -> p.supports(config))
        .max(Comparator.comparingInt(LlmClientProvider::getPriority));
  }

  /**
   * Registers a provider. If a provider with the same name or alias already exists, it will be
   * replaced.
   *
   * @param provider The provider to register
   */
  public static void registerProvider(final LlmClientProvider provider) {
    Objects.requireNonNull(
        provider,
        MessageSource.getMessage(
            "infra.common.error.argument_null", "provider must not be null"));
    // Register by main name
    PROVIDERS.put(normalizeKey(provider.getProviderName()), provider);
    // Register by aliases
    for (final String alias : provider.getAliases()) {
      PROVIDERS.put(normalizeKey(alias), provider);
    }
  }

  /**
   * Unregisters a provider by name or alias.
   *
   * @param providerName The provider name or alias to unregister
   * @return The removed provider, or null if not found
   */
  public static LlmClientProvider unregisterProvider(final String providerName) {
    if (providerName == null) {
      return null;
    }
    final LlmClientProvider removed = PROVIDERS.remove(normalizeKey(providerName));
    if (removed == null) {
      return null;
    }
    PROVIDERS.entrySet().removeIf(entry -> Objects.equals(entry.getValue(), removed));
    return removed;
  }

  /**
   * Returns an unmodifiable list of all registered provider names.
   *
   * @return List of provider names
   */
  public static List<String> getProviderNames() {
    ensureInitialized();
    final List<String> names = new ArrayList<>(PROVIDERS.keySet());
    Collections.sort(names);
    return Collections.unmodifiableList(names);
  }

  /** Clears all registered providers (useful for testing). */
  public static void clear() {
    PROVIDERS.clear();
    initialized = false;
  }

  /** Forces re-initialization of the registry. */
  public static void reinitialize() {
    clear();
    initialize();
  }

  private static void ensureInitialized() {
    if (!initialized) {
      synchronized (LlmProviderRegistry.class) {
        if (!initialized) {
          initialize();
        }
      }
    }
  }

  private static void initialize() {
    // Register built-in providers
    registerBuiltinProviders();
    // Discover providers via ServiceLoader
    discoverProviders();
    initialized = true;
  }

  private static void registerBuiltinProviders() {
    // Register all built-in providers
    registerProvider(new OpenAiClientProvider());
    registerProvider(new GeminiClientProvider());
    registerProvider(new AnthropicClientProvider());
    registerProvider(new AzureOpenAiClientProvider());
    registerProvider(new VertexAiClientProvider());
    registerProvider(new BedrockClientProvider());
    registerProvider(new LocalClientProvider());
    registerProvider(new MockClientProvider());
  }

  private static void discoverProviders() {
    try {
      final ServiceLoader<LlmClientProvider> loader = ServiceLoader.load(LlmClientProvider.class);
      for (final LlmClientProvider provider : loader) {
        registerProvider(provider);
      }
    } catch (final Exception e) {
      // Log but don't fail - built-in providers are still available
      Logger.warn(
          "Failed to discover LLM PROVIDERS via ServiceLoader: " + e.getMessage());
    }
  }

  private static String normalizeKey(final String name) {
    return name.toLowerCase(Locale.ROOT).replace("_", "-");
  }

  // ==================== Built-in Provider Implementations ====================
  /** OpenAI provider implementation. */
  static final class OpenAiClientProvider implements LlmClientProvider {

    @Override
    public String getProviderName() {
      return "openai";
    }

    @Override
    public String[] getAliases() {
      return new String[] {"openai-compatible", "openai_compatible"};
    }

    @Override
    public LlmClientPort create(final Config.LlmConfig config) {
      return new OpenAiLlmClient(config);
    }
  }

  /** Gemini provider implementation. */
  static final class GeminiClientProvider implements LlmClientProvider {

    @Override
    public String getProviderName() {
      return "gemini";
    }

    @Override
    public LlmClientPort create(final Config.LlmConfig config) {
      return new GeminiLlmClient(config);
    }
  }

  /** Anthropic provider implementation. */
  static final class AnthropicClientProvider implements LlmClientProvider {

    @Override
    public String getProviderName() {
      return "anthropic";
    }

    @Override
    public LlmClientPort create(final Config.LlmConfig config) {
      return new AnthropicLlmClient(config);
    }
  }

  /** Azure OpenAI provider implementation. */
  static final class AzureOpenAiClientProvider implements LlmClientProvider {

    @Override
    public String getProviderName() {
      return "azure-openai";
    }

    @Override
    public String[] getAliases() {
      return new String[] {"azure_openai"};
    }

    @Override
    public LlmClientPort create(final Config.LlmConfig config) {
      return new AzureOpenAiLlmClient(config);
    }
  }

  /** Vertex AI provider implementation. */
  static final class VertexAiClientProvider implements LlmClientProvider {

    @Override
    public String getProviderName() {
      return "vertex";
    }

    @Override
    public String[] getAliases() {
      return new String[] {"vertex-ai", "vertex_ai"};
    }

    @Override
    public LlmClientPort create(final Config.LlmConfig config) {
      return new VertexAiLlmClient(config);
    }
  }

  /** Bedrock provider implementation. */
  static final class BedrockClientProvider implements LlmClientProvider {

    @Override
    public String getProviderName() {
      return "bedrock";
    }

    @Override
    public LlmClientPort create(final Config.LlmConfig config) {
      return new BedrockLlmClient(config);
    }
  }

  /** Local (OpenAI-compatible) provider implementation. */
  static final class LocalClientProvider implements LlmClientProvider {

    @Override
    public String getProviderName() {
      return "local";
    }

    @Override
    public String[] getAliases() {
      return new String[] {"ollama", "vllm"};
    }

    @Override
    public LlmClientPort create(final Config.LlmConfig config) {
      return new LocalLlmClient(config);
    }

    @Override
    public boolean isExternalProvider() {
      return false;
    }
  }

  /** Mock provider implementation for testing. */
  static final class MockClientProvider implements LlmClientProvider {

    @Override
    public String getProviderName() {
      return "mock";
    }

    @Override
    public LlmClientPort create(final Config.LlmConfig config) {
      return new MockLlmClient();
    }

    @Override
    public boolean isExternalProvider() {
      return false;
    }

    @Override
    public int getPriority() {
      // Low priority - only use when explicitly requested
      return -100;
    }
  }
}
