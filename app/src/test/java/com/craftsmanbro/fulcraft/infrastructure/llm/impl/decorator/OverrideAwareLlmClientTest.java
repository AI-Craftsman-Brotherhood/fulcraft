package com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientProvider;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OverrideAwareLlmClientTest {

  private CountingProvider provider;

  @BeforeEach
  void setUp() {
    LlmProviderRegistry.reinitialize();
    provider = new CountingProvider();
    LlmProviderRegistry.registerProvider(provider);
  }

  @AfterEach
  void tearDown() {
    LlmProviderRegistry.unregisterProvider(provider.getProviderName());
  }

  @Test
  void generateTest_usesBaseConfigWhenOverrideNull() {
    Config.LlmConfig baseConfig = new Config.LlmConfig();
    baseConfig.setProvider(provider.getProviderName());
    baseConfig.setModelName("base");

    OverrideAwareLlmClient client = new OverrideAwareLlmClient(baseConfig);

    String result = client.generateTest("prompt", (Config.LlmConfig) null);

    assertEquals("response-base", result);
    assertEquals(1, provider.getCreateCount());
    assertTrue(client.getLastUsage().isPresent());
    assertEquals("provider-base", client.profile().providerName());
  }

  @Test
  void profile_beforeGenerate_usesBaseClientProfile() {
    Config.LlmConfig baseConfig = new Config.LlmConfig();
    baseConfig.setProvider(provider.getProviderName());
    baseConfig.setModelName("base");

    OverrideAwareLlmClient client = new OverrideAwareLlmClient(baseConfig);

    assertEquals("provider-base", client.profile().providerName());
  }

  @Test
  void generateTest_usesOverrideConfigAndCachesByKey() {
    Config.LlmConfig baseConfig = new Config.LlmConfig();
    baseConfig.setProvider(provider.getProviderName());
    baseConfig.setModelName("base");

    OverrideAwareLlmClient client = new OverrideAwareLlmClient(baseConfig);

    Config.LlmConfig overrideOne = new Config.LlmConfig();
    overrideOne.setProvider(provider.getProviderName());
    overrideOne.setModelName("override");

    String first = client.generateTest("prompt", overrideOne);

    Config.LlmConfig overrideTwo = new Config.LlmConfig();
    overrideTwo.setProvider(provider.getProviderName());
    overrideTwo.setModelName("override");

    String second = client.generateTest("prompt", overrideTwo);

    assertEquals("response-override", first);
    assertEquals("response-override", second);
    assertEquals(2, provider.getCreateCount());
  }

  @Test
  void generateTest_cachesOverrideWhenHeaderOrderDiffers() {
    Config.LlmConfig baseConfig = new Config.LlmConfig();
    baseConfig.setProvider(provider.getProviderName());
    baseConfig.setModelName("base");
    OverrideAwareLlmClient client = new OverrideAwareLlmClient(baseConfig);

    Config.LlmConfig overrideOne = new Config.LlmConfig();
    overrideOne.setProvider(provider.getProviderName());
    overrideOne.setModelName("override");
    Map<String, String> firstHeaders = new LinkedHashMap<>();
    firstHeaders.put("x-b", "2");
    firstHeaders.put("x-a", "1");
    overrideOne.setCustomHeaders(firstHeaders);

    Config.LlmConfig overrideTwo = new Config.LlmConfig();
    overrideTwo.setProvider(provider.getProviderName());
    overrideTwo.setModelName("override");
    Map<String, String> secondHeaders = new LinkedHashMap<>();
    secondHeaders.put("x-a", "1");
    secondHeaders.put("x-b", "2");
    overrideTwo.setCustomHeaders(secondHeaders);

    client.generateTest("prompt-1", overrideOne);
    client.generateTest("prompt-2", overrideTwo);

    assertEquals(2, provider.getCreateCount());
  }

  @Test
  void generateTest_nonUsageAwareDelegate_keepsLastUsageEmpty() {
    Config.LlmConfig baseConfig = new Config.LlmConfig();
    baseConfig.setProvider(provider.getProviderName());
    baseConfig.setModelName("plain-base");
    OverrideAwareLlmClient client = new OverrideAwareLlmClient(baseConfig);

    Config.LlmConfig overrideConfig = new Config.LlmConfig();
    overrideConfig.setProvider(provider.getProviderName());
    overrideConfig.setModelName("plain-override");

    String result = client.generateTest("prompt", overrideConfig);

    assertEquals("basic-response-plain-override", result);
    assertTrue(client.getLastUsage().isEmpty());
  }

  @Test
  void isHealthy_usesBaseClientStatus() {
    Config.LlmConfig baseConfig = new Config.LlmConfig();
    baseConfig.setProvider(provider.getProviderName());
    baseConfig.setModelName("base");
    OverrideAwareLlmClient client = new OverrideAwareLlmClient(baseConfig);

    assertTrue(client.isHealthy());
  }

  @Test
  void clearContext_clearsUsageAndDelegatesToLastClient() {
    Config.LlmConfig baseConfig = new Config.LlmConfig();
    baseConfig.setProvider(provider.getProviderName());
    baseConfig.setModelName("base");

    OverrideAwareLlmClient client = new OverrideAwareLlmClient(baseConfig);

    Config.LlmConfig overrideConfig = new Config.LlmConfig();
    overrideConfig.setProvider(provider.getProviderName());
    overrideConfig.setModelName("override");

    client.generateTest("prompt", overrideConfig);

    TestLlmClient overrideClient = provider.getClient("override");
    assertNotNull(overrideClient);

    client.clearContext();

    assertTrue(overrideClient.isClearCalled());
    assertTrue(client.getLastUsage().isEmpty());
  }

  @Test
  void clearContext_withoutLastDelegate_isSafe() {
    Config.LlmConfig baseConfig = new Config.LlmConfig();
    baseConfig.setProvider(provider.getProviderName());
    baseConfig.setModelName("base");
    OverrideAwareLlmClient client = new OverrideAwareLlmClient(baseConfig);

    client.clearContext();

    assertTrue(client.getLastUsage().isEmpty());
    assertEquals("provider-base", client.profile().providerName());
  }

  private static final class CountingProvider implements LlmClientProvider {
    private final AtomicInteger createCount = new AtomicInteger();
    private final Map<String, LlmClientPort> clients = new ConcurrentHashMap<>();

    @Override
    public String getProviderName() {
      return "test-provider";
    }

    @Override
    public boolean isExternalProvider() {
      return false;
    }

    @Override
    public LlmClientPort create(Config.LlmConfig config) {
      createCount.incrementAndGet();
      String modelName = config.getModelName() != null ? config.getModelName() : "";
      if (modelName.startsWith("plain")) {
        BasicLlmClient client = new BasicLlmClient(modelName);
        clients.put(modelName, client);
        return client;
      }
      TestLlmClient client = new TestLlmClient(modelName);
      clients.put(modelName, client);
      return client;
    }

    int getCreateCount() {
      return createCount.get();
    }

    TestLlmClient getClient(String modelName) {
      LlmClientPort client = clients.get(modelName);
      if (client instanceof TestLlmClient testClient) {
        return testClient;
      }
      return null;
    }
  }

  private static final class TestLlmClient implements LlmClientPort, TokenUsageAware {
    private final String id;
    private boolean clearCalled;
    private TokenUsage lastUsage;

    private TestLlmClient(String id) {
      this.id = id;
    }

    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      lastUsage = new TokenUsage(1, 2, 3);
      return "response-" + id;
    }

    @Override
    public boolean isHealthy() {
      return true;
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile("provider-" + id, Set.of(Capability.SEED), Optional.empty());
    }

    @Override
    public void clearContext() {
      clearCalled = true;
    }

    @Override
    public Optional<TokenUsage> getLastUsage() {
      return Optional.ofNullable(lastUsage);
    }

    boolean isClearCalled() {
      return clearCalled;
    }
  }

  private static final class BasicLlmClient implements LlmClientPort {
    private final String id;

    private BasicLlmClient(String id) {
      this.id = id;
    }

    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      return "basic-response-" + id;
    }

    @Override
    public boolean isHealthy() {
      return true;
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile("basic-provider-" + id, Set.of(Capability.SEED), Optional.empty());
    }
  }
}
