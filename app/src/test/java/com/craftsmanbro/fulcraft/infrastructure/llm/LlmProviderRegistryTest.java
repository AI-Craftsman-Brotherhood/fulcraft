package com.craftsmanbro.fulcraft.infrastructure.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientProvider;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.MockLlmClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link LlmProviderRegistry}. */
class LlmProviderRegistryTest {

  @BeforeEach
  void setUp() {
    // Ensure registry is initialized with built-in providers
    LlmProviderRegistry.reinitialize();
  }

  @AfterEach
  void tearDown() {
    // Reset registry to clean state
    LlmProviderRegistry.reinitialize();
  }

  @ParameterizedTest
  @ValueSource(strings = {"openai", "gemini", "anthropic", "local"})
  void findProvider_returnsKnownProvider(String providerName) {
    Optional<LlmClientProvider> provider = LlmProviderRegistry.findProvider(providerName);
    assertTrue(provider.isPresent());
    assertEquals(providerName, provider.get().getProviderName());
  }

  @Test
  void findProvider_returnsEmptyForUnknown() {
    Optional<LlmClientProvider> provider = LlmProviderRegistry.findProvider("unknown-provider");
    assertFalse(provider.isPresent());
  }

  @Test
  void findProvider_returnsEmptyForNullName() {
    Optional<LlmClientProvider> provider = LlmProviderRegistry.findProvider(null);
    assertTrue(provider.isEmpty());
  }

  @Test
  void findProvider_caseInsensitive() {
    Optional<LlmClientProvider> upper = LlmProviderRegistry.findProvider("OPENAI");
    Optional<LlmClientProvider> lower = LlmProviderRegistry.findProvider("openai");
    Optional<LlmClientProvider> mixed = LlmProviderRegistry.findProvider("OpenAI");

    assertTrue(upper.isPresent());
    assertTrue(lower.isPresent());
    assertTrue(mixed.isPresent());
    assertEquals(upper.get().getProviderName(), lower.get().getProviderName());
    assertEquals(lower.get().getProviderName(), mixed.get().getProviderName());
  }

  @Test
  void findProvider_supportsAliases() {
    // OpenAI aliases
    assertTrue(LlmProviderRegistry.findProvider("openai-compatible").isPresent());
    assertTrue(LlmProviderRegistry.findProvider("openai_compatible").isPresent());

    // Azure aliases
    assertTrue(LlmProviderRegistry.findProvider("azure-openai").isPresent());
    assertTrue(LlmProviderRegistry.findProvider("azure_openai").isPresent());

    // Vertex aliases
    assertTrue(LlmProviderRegistry.findProvider("vertex").isPresent());
    assertTrue(LlmProviderRegistry.findProvider("vertex-ai").isPresent());
    assertTrue(LlmProviderRegistry.findProvider("vertex_ai").isPresent());

    // Local aliases
    assertTrue(LlmProviderRegistry.findProvider("local").isPresent());
    assertTrue(LlmProviderRegistry.findProvider("ollama").isPresent());
  }

  @Test
  void getProvider_throwsForUnknown() {
    assertThrows(
        IllegalStateException.class, () -> LlmProviderRegistry.getProvider("unknown-provider"));
  }

  @Test
  void getProviderNames_containsAllBuiltinProviders() {
    List<String> names = LlmProviderRegistry.getProviderNames();

    // Check main provider names and aliases are registered
    assertTrue(names.contains("openai"));
    assertTrue(names.contains("gemini"));
    assertTrue(names.contains("anthropic"));
    assertTrue(names.contains("azure-openai"));
    assertTrue(names.contains("vertex"));
    assertTrue(names.contains("bedrock"));
    assertTrue(names.contains("local"));
    assertTrue(names.contains("mock"));
  }

  @Test
  void findProviderForConfig_matchesByProviderName() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("gemini");

    Optional<LlmClientProvider> provider = LlmProviderRegistry.findProviderForConfig(config);

    assertTrue(provider.isPresent());
    assertEquals("gemini", provider.get().getProviderName());
  }

  @Test
  void findProviderForConfig_returnsEmptyForNullConfig() {
    Optional<LlmClientProvider> provider = LlmProviderRegistry.findProviderForConfig(null);
    assertTrue(provider.isEmpty());
  }

  @Test
  void findProviderForConfig_prefersDirectProviderNameOverPriorityFallback() {
    LlmClientProvider highPriorityFallback =
        new LlmClientProvider() {
          @Override
          public String getProviderName() {
            return "priority-fallback";
          }

          @Override
          public LlmClientPort create(Config.LlmConfig config) {
            return new MockLlmClient();
          }

          @Override
          public boolean supports(Config.LlmConfig config) {
            return true;
          }

          @Override
          public int getPriority() {
            return 1_000;
          }
        };
    LlmProviderRegistry.registerProvider(highPriorityFallback);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("openai");

    Optional<LlmClientProvider> provider = LlmProviderRegistry.findProviderForConfig(config);

    assertTrue(provider.isPresent());
    assertEquals("openai", provider.get().getProviderName());
  }

  @Test
  void findProviderForConfig_usesHighestPrioritySupportingProviderWhenNoDirectMatch() {
    LlmClientProvider lowPriority =
        new LlmClientProvider() {
          @Override
          public String getProviderName() {
            return "fallback-low";
          }

          @Override
          public LlmClientPort create(Config.LlmConfig config) {
            return new MockLlmClient();
          }

          @Override
          public boolean supports(Config.LlmConfig config) {
            return true;
          }

          @Override
          public int getPriority() {
            return 10;
          }
        };
    LlmClientProvider highPriority =
        new LlmClientProvider() {
          @Override
          public String getProviderName() {
            return "fallback-high";
          }

          @Override
          public LlmClientPort create(Config.LlmConfig config) {
            return new MockLlmClient();
          }

          @Override
          public boolean supports(Config.LlmConfig config) {
            return true;
          }

          @Override
          public int getPriority() {
            return 100;
          }
        };
    LlmProviderRegistry.registerProvider(lowPriority);
    LlmProviderRegistry.registerProvider(highPriority);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("not-registered-provider");

    Optional<LlmClientProvider> provider = LlmProviderRegistry.findProviderForConfig(config);

    assertTrue(provider.isPresent());
    assertEquals("fallback-high", provider.get().getProviderName());
  }

  @Test
  void registerProvider_addsCustomProvider() {
    LlmClientProvider customProvider =
        new LlmClientProvider() {
          @Override
          public String getProviderName() {
            return "custom-test";
          }

          @Override
          public LlmClientPort create(Config.LlmConfig config) {
            return new MockLlmClient();
          }
        };

    LlmProviderRegistry.registerProvider(customProvider);

    Optional<LlmClientProvider> found = LlmProviderRegistry.findProvider("custom-test");
    assertTrue(found.isPresent());
    assertEquals("custom-test", found.get().getProviderName());
  }

  @Test
  void unregisterProvider_removesProvider() {
    // First register a custom provider
    LlmClientProvider customProvider =
        new LlmClientProvider() {
          @Override
          public String getProviderName() {
            return "temp-provider";
          }

          @Override
          public LlmClientPort create(Config.LlmConfig config) {
            return new MockLlmClient();
          }
        };
    LlmProviderRegistry.registerProvider(customProvider);
    assertTrue(LlmProviderRegistry.findProvider("temp-provider").isPresent());

    // Now unregister
    LlmClientProvider removed = LlmProviderRegistry.unregisterProvider("temp-provider");
    assertNotNull(removed);
    assertFalse(LlmProviderRegistry.findProvider("temp-provider").isPresent());
  }

  @Test
  void unregisterProvider_returnsNullForNullName() {
    LlmClientProvider removed = LlmProviderRegistry.unregisterProvider(null);
    assertNull(removed);
  }

  @Test
  void unregisterProvider_removesAliases() {
    assertTrue(LlmProviderRegistry.findProvider("openai").isPresent());
    assertTrue(LlmProviderRegistry.findProvider("openai-compatible").isPresent());

    LlmClientProvider removed = LlmProviderRegistry.unregisterProvider("openai-compatible");
    assertNotNull(removed);
    assertFalse(LlmProviderRegistry.findProvider("openai").isPresent());
    assertFalse(LlmProviderRegistry.findProvider("openai-compatible").isPresent());
    assertFalse(LlmProviderRegistry.findProvider("openai_compatible").isPresent());
  }

  @Test
  void mockProvider_isNotExternalProvider() {
    Optional<LlmClientProvider> provider = LlmProviderRegistry.findProvider("mock");
    assertTrue(provider.isPresent());
    assertFalse(provider.get().isExternalProvider());
  }

  @Test
  void externalProviders_areMarkedCorrectly() {
    // External providers
    assertTrue(LlmProviderRegistry.getProvider("openai").isExternalProvider());
    assertTrue(LlmProviderRegistry.getProvider("gemini").isExternalProvider());
    assertTrue(LlmProviderRegistry.getProvider("anthropic").isExternalProvider());
    assertTrue(LlmProviderRegistry.getProvider("azure-openai").isExternalProvider());

    // Local providers
    assertFalse(LlmProviderRegistry.getProvider("local").isExternalProvider());
    assertFalse(LlmProviderRegistry.getProvider("mock").isExternalProvider());
  }

  @Test
  void clear_andReinitialize_worksCorrectly() {
    // Get initial provider count
    int initialCount = LlmProviderRegistry.getProviderNames().size();
    assertTrue(initialCount > 0, "Should have built-in providers");

    // Register a custom provider
    LlmClientProvider customProvider =
        new LlmClientProvider() {
          @Override
          public String getProviderName() {
            return "clear-test-provider";
          }

          @Override
          public LlmClientPort create(Config.LlmConfig config) {
            return new MockLlmClient();
          }
        };
    LlmProviderRegistry.registerProvider(customProvider);
    assertTrue(LlmProviderRegistry.findProvider("clear-test-provider").isPresent());

    // Reinitialize should restore to default state (without custom provider)
    LlmProviderRegistry.reinitialize();

    // Custom provider should be gone
    assertFalse(LlmProviderRegistry.findProvider("clear-test-provider").isPresent());

    // Built-in providers should still exist
    assertTrue(LlmProviderRegistry.findProvider("openai").isPresent());
    assertTrue(LlmProviderRegistry.findProvider("gemini").isPresent());
  }

  @Test
  void reinitialize_restoresBuiltinProviders() {
    // Register a custom provider that overrides a built-in
    LlmClientProvider customMock =
        new LlmClientProvider() {
          @Override
          public String getProviderName() {
            return "mock"; // Same name as built-in
          }

          @Override
          public LlmClientPort create(Config.LlmConfig config) {
            return new MockLlmClient();
          }

          @Override
          public int getPriority() {
            return 999; // Higher priority
          }
        };
    LlmProviderRegistry.registerProvider(customMock);
    assertEquals(999, LlmProviderRegistry.getProvider("mock").getPriority());

    // Reinitialize should restore the built-in mock provider
    LlmProviderRegistry.reinitialize();
    assertEquals(-100, LlmProviderRegistry.getProvider("mock").getPriority());
  }
}
