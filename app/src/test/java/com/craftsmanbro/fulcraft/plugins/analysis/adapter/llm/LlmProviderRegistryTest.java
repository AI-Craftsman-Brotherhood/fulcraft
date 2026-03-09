package com.craftsmanbro.fulcraft.plugins.analysis.adapter.llm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmProviderRegistryTest {

  @BeforeEach
  void setUp() {
    LlmProviderRegistry.reinitialize();
  }

  @AfterEach
  void tearDown() {
    LlmProviderRegistry.reinitialize();
  }

  @Test
  void findProvider_wrapsInfrastructureProvider() {
    InfraProvider infraProvider = new InfraProvider();
    LlmProviderRegistry.registerProvider(infraProvider);

    Optional<LlmClientProviderFacade> provider = LlmProviderRegistry.findProvider("infra-test");

    assertTrue(provider.isPresent());
    LlmClientProviderFacade facade = provider.get();
    assertNotSame(infraProvider, facade);
    assertEquals("infra-test", facade.getProviderName());
    assertArrayEquals(new String[] {"infra-alias"}, facade.getAliases());
    assertEquals(42, facade.getPriority());
    assertFalse(facade.isExternalProvider());

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("infra-test");
    assertFalse(facade.supports(config));

    LlmClientPort client = facade.create(config);
    assertNotSame(infraProvider.client, client);
    assertEquals("infra-test", client.profile().providerName());
  }

  @Test
  void findProvider_returnsSameInstanceForFacadeProvider() {
    FacadeProvider facadeProvider = new FacadeProvider();
    LlmProviderRegistry.registerProvider(facadeProvider);

    LlmClientProviderFacade found = LlmProviderRegistry.getProvider("facade-test");

    assertSame(facadeProvider, found);
  }

  @Test
  void findProviderForConfig_prefersDirectLookupEvenWhenSupportsIsFalse() {
    InfraProvider infraProvider = new InfraProvider();
    LlmProviderRegistry.registerProvider(infraProvider);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("infra-test");

    Optional<LlmClientProviderFacade> found = LlmProviderRegistry.findProviderForConfig(config);

    assertTrue(found.isPresent());
    assertEquals("infra-test", found.orElseThrow().getProviderName());
  }

  @Test
  void findProviderForConfig_usesHighestPrioritySupportingProvider() {
    LlmProviderRegistry.registerProvider(new PrioritizedInfraProvider("support-low", true, 10));
    LlmProviderRegistry.registerProvider(new PrioritizedInfraProvider("support-high", true, 99));

    Config.LlmConfig config = new Config.LlmConfig();

    Optional<LlmClientProviderFacade> found = LlmProviderRegistry.findProviderForConfig(config);

    assertTrue(found.isPresent());
    assertEquals("support-high", found.orElseThrow().getProviderName());
  }

  @Test
  void unregisterProvider_byAliasRemovesProviderAndAliases() {
    InfraProvider infraProvider = new InfraProvider();
    LlmProviderRegistry.registerProvider(infraProvider);

    LlmClientProviderFacade removed = LlmProviderRegistry.unregisterProvider("infra-alias");

    assertEquals("infra-test", removed.getProviderName());
    assertTrue(LlmProviderRegistry.findProvider("infra-test").isEmpty());
    assertTrue(LlmProviderRegistry.findProvider("infra-alias").isEmpty());
  }

  @Test
  void unregisterProvider_withNullNameReturnsNull() {
    assertNull(LlmProviderRegistry.unregisterProvider(null));
  }

  @Test
  void getProviderNames_returnsSortedUnmodifiableList() {
    LlmProviderRegistry.registerProvider(new PrioritizedInfraProvider("z-provider", true, 1));
    LlmProviderRegistry.registerProvider(new PrioritizedInfraProvider("a-provider", true, 1));

    List<String> names = LlmProviderRegistry.getProviderNames();
    List<String> sorted = new ArrayList<>(names);
    sorted.sort(String::compareTo);

    assertEquals(sorted, names);
    assertThrows(UnsupportedOperationException.class, () -> names.add("new-provider"));
  }

  @Test
  void clear_dropsCustomProvidersAndKeepsBuiltinReinitialization() {
    LlmProviderRegistry.registerProvider(new PrioritizedInfraProvider("clear-test", true, 1));
    assertTrue(LlmProviderRegistry.findProvider("clear-test").isPresent());

    LlmProviderRegistry.clear();

    assertTrue(LlmProviderRegistry.findProvider("clear-test").isEmpty());
    assertTrue(LlmProviderRegistry.findProvider("openai").isPresent());
  }

  private static final class InfraProvider
      implements com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientProvider {
    private final com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort client =
        new InfrastructureStubClient("infra-test");

    @Override
    public String getProviderName() {
      return "infra-test";
    }

    @Override
    public String[] getAliases() {
      return new String[] {"infra-alias"};
    }

    @Override
    public com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort create(
        Config.LlmConfig config) {
      return client;
    }

    @Override
    public boolean supports(Config.LlmConfig config) {
      return false;
    }

    @Override
    public int getPriority() {
      return 42;
    }

    @Override
    public boolean isExternalProvider() {
      return false;
    }
  }

  private static final class FacadeProvider implements LlmClientProviderFacade {
    private final LlmClientPort client = new StubClient("facade-test");

    @Override
    public String getProviderName() {
      return "facade-test";
    }

    @Override
    public LlmClientPort create(Config.LlmConfig config) {
      return client;
    }
  }

  private static final class PrioritizedInfraProvider
      implements com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientProvider {
    private final String providerName;
    private final boolean supports;
    private final int priority;

    private PrioritizedInfraProvider(String providerName, boolean supports, int priority) {
      this.providerName = providerName;
      this.supports = supports;
      this.priority = priority;
    }

    @Override
    public String getProviderName() {
      return providerName;
    }

    @Override
    public com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort create(
        Config.LlmConfig config) {
      return new InfrastructureStubClient(providerName);
    }

    @Override
    public boolean supports(Config.LlmConfig config) {
      return supports;
    }

    @Override
    public int getPriority() {
      return priority;
    }
  }

  private static final class StubClient implements LlmClientPort {
    private final String providerName;

    private StubClient(String providerName) {
      this.providerName = providerName;
    }

    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      return "generated";
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile(providerName, Set.of(), Optional.empty());
    }
  }

  private static final class InfrastructureStubClient
      implements com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort {
    private final String providerName;

    private InfrastructureStubClient(String providerName) {
      this.providerName = providerName;
    }

    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      return "generated";
    }

    @Override
    public com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile profile() {
      return new com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile(
          providerName, Set.of(), Optional.empty());
    }
  }
}
