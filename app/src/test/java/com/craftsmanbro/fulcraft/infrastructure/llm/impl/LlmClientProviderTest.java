package com.craftsmanbro.fulcraft.infrastructure.llm.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientProvider;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.MockLlmClient;
import org.junit.jupiter.api.Test;

class LlmClientProviderTest {

  @Test
  void supports_matchesProviderNameAndAliases() {
    LlmClientProvider provider = new TestProvider("openai-compatible", "openai_compatible", "alt");

    assertTrue(provider.supports(configWithProvider("openai-compatible")));
    assertTrue(provider.supports(configWithProvider("OPENAI_COMPATIBLE")));
    assertTrue(provider.supports(configWithProvider("alt")));
    assertFalse(provider.supports(configWithProvider("openai")));
  }

  @Test
  void supports_returnsFalseForNullConfigOrProvider() {
    LlmClientProvider provider = new TestProvider("openai", "alias");

    assertFalse(provider.supports(null));
    assertFalse(provider.supports(new Config.LlmConfig()));
  }

  @Test
  void defaults_areStable() {
    LlmClientProvider provider =
        new LlmClientProvider() {
          @Override
          public String getProviderName() {
            return "custom";
          }

          @Override
          public LlmClientPort create(Config.LlmConfig config) {
            return new MockLlmClient();
          }
        };

    assertEquals(0, provider.getPriority());
    assertTrue(provider.isExternalProvider());
    assertEquals(0, provider.getAliases().length);
  }

  private static Config.LlmConfig configWithProvider(String provider) {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider(provider);
    return config;
  }

  private static final class TestProvider implements LlmClientProvider {
    private final String name;
    private final String[] aliases;

    private TestProvider(String name, String... aliases) {
      this.name = name;
      this.aliases = aliases;
    }

    @Override
    public String getProviderName() {
      return name;
    }

    @Override
    public String[] getAliases() {
      return aliases;
    }

    @Override
    public LlmClientPort create(Config.LlmConfig config) {
      return new MockLlmClient();
    }
  }
}
