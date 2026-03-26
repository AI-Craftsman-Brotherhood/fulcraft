package com.craftsmanbro.fulcraft.infrastructure.llm.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientProvider;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.AnthropicLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.AzureOpenAiLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.BedrockLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.GeminiLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.LocalLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.MockLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.OpenAiLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.VertexAiLlmClient;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LlmClientFactoryTest {

  @BeforeEach
  void setUp() {
    LlmProviderRegistry.reinitialize();
  }

  @AfterEach
  void tearDown() {
    LlmProviderRegistry.reinitialize();
  }

  static Stream<Arguments> providerMappings() {
    return Stream.of(
        Arguments.of("local", LocalLlmClient.class),
        Arguments.of("LoCaL", LocalLlmClient.class),
        Arguments.of("ollama", LocalLlmClient.class),
        Arguments.of("mock", MockLlmClient.class),
        Arguments.of("openai", OpenAiLlmClient.class),
        Arguments.of("openai-compatible", OpenAiLlmClient.class),
        Arguments.of("openai_compatible", OpenAiLlmClient.class),
        Arguments.of("azure-openai", AzureOpenAiLlmClient.class),
        Arguments.of("azure_openai", AzureOpenAiLlmClient.class),
        Arguments.of("anthropic", AnthropicLlmClient.class),
        Arguments.of("vertex", VertexAiLlmClient.class),
        Arguments.of("vertex-ai", VertexAiLlmClient.class),
        Arguments.of("vertex_ai", VertexAiLlmClient.class),
        Arguments.of("bedrock", BedrockLlmClient.class));
  }

  @ParameterizedTest
  @MethodSource("providerMappings")
  void create_returnsExpectedClientForProvider(String provider, Class<?> expectedType) {
    Config.LlmConfig config = newConfigForProvider(provider);

    LlmClientPort client = LlmClientFactory.create(config);

    assertThat(client).isInstanceOf(expectedType);
  }

  @Test
  void create_whenProviderIsUnsupported_throws() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("unknown");

    assertThatThrownBy(() -> LlmClientFactory.create(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported llm.provider: unknown");
  }

  @Test
  void create_whenProviderIsNull_throws() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider(null);

    assertThatThrownBy(() -> LlmClientFactory.create(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported llm.provider: null");
  }

  @Test
  void create_whenConfigIsNull_throwsNullPointerException() {
    assertThatThrownBy(() -> LlmClientFactory.create(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void create_forGemini_withApiKey_returnsGeminiClient() {
    com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.setForTest(
        name -> {
          if ("GEMINI_API_KEY".equals(name)) {
            return "test-gemini-key";
          }
          return System.getenv(name); // fallback for other env vars
        });
    try {
      Config.LlmConfig config = new Config.LlmConfig();
      config.setProvider("gemini");

      assertThat(LlmClientFactory.create(config)).isInstanceOf(GeminiLlmClient.class);
    } finally {
      com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.reset();
    }
  }

  @Test
  void create_forGemini_withoutApiKey_throws() {
    com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.setForTest(name -> null);
    try {
      Config.LlmConfig config = new Config.LlmConfig();
      config.setProvider("gemini");

      assertThatThrownBy(() -> LlmClientFactory.create(config))
          .isInstanceOf(IllegalStateException.class);
    } finally {
      com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.reset();
    }
  }

  @Test
  void isProviderAvailable_reflectsRegistryState() {
    assertThat(LlmClientFactory.isProviderAvailable("factory-test-provider")).isFalse();

    LlmProviderRegistry.registerProvider(new FactoryTestProvider());

    assertThat(LlmClientFactory.isProviderAvailable("factory-test-provider")).isTrue();
  }

  @Test
  void getAvailableProviders_returnsUnmodifiableSortedNames() {
    LlmProviderRegistry.registerProvider(new FactoryTestProvider());

    java.util.List<String> providers = LlmClientFactory.getAvailableProviders();

    assertThat(providers).contains("factory-test-provider");
    assertThatThrownBy(() -> providers.add("another-provider"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(providers).isSorted();
  }

  private static Config.LlmConfig newConfigForProvider(String provider) {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider(provider);

    if (isOpenAiProvider(provider)) {
      config.setApiKey("test-openai-key");
      config.setModelName("gpt-test");
      config.setUrl("http://localhost/openai");
      return config;
    }

    if (isAzureProvider(provider)) {
      config.setUrl("http://localhost/azure");
      config.setAzureDeployment("test-deploy");
      config.setAzureApiVersion("2024-01-01");
      config.setApiKey("test-azure-key");
      return config;
    }

    if ("anthropic".equalsIgnoreCase(provider)) {
      config.setModelName("claude-test");
      config.setApiKey("test-anthropic-key");
      config.setUrl("http://localhost/anthropic");
      return config;
    }

    return config;
  }

  private static boolean isOpenAiProvider(String provider) {
    return "openai".equalsIgnoreCase(provider)
        || "openai-compatible".equalsIgnoreCase(provider)
        || "openai_compatible".equalsIgnoreCase(provider);
  }

  private static boolean isAzureProvider(String provider) {
    return "azure-openai".equalsIgnoreCase(provider) || "azure_openai".equalsIgnoreCase(provider);
  }

  private static final class FactoryTestProvider implements LlmClientProvider {
    @Override
    public String getProviderName() {
      return "factory-test-provider";
    }

    @Override
    public LlmClientPort create(Config.LlmConfig config) {
      return new MockLlmClient();
    }
  }
}
