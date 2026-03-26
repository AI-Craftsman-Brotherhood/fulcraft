package com.craftsmanbro.fulcraft.plugins.analysis.adapter.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmClientProviderFacadeTest {

  @Test
  void supports_usesProviderNameAndAliases() {
    LlmClientProviderFacade provider = new TestProvider();
    Config.LlmConfig config = new Config.LlmConfig();

    config.setProvider("demo");
    assertThat(provider.supports(config)).isTrue();

    config.setProvider("demo-alias");
    assertThat(provider.supports(config)).isTrue();

    config.setProvider("unknown");
    assertThat(provider.supports(config)).isFalse();
  }

  @Test
  void create_returnsClientWithProfile() {
    LlmClientProviderFacade provider = new TestProvider();

    LlmClientPort client = provider.create(new Config.LlmConfig());

    assertThat(client.profile().providerName()).isEqualTo("demo");
  }

  private static final class TestProvider implements LlmClientProviderFacade {
    private final LlmClientPort client = new StubClient();

    @Override
    public String getProviderName() {
      return "demo";
    }

    @Override
    public String[] getAliases() {
      return new String[] {"demo-alias"};
    }

    @Override
    public LlmClientPort create(Config.LlmConfig config) {
      return client;
    }
  }

  private static final class StubClient implements LlmClientPort {
    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      return "generated";
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile("demo", Set.of(), Optional.empty());
    }
  }
}
