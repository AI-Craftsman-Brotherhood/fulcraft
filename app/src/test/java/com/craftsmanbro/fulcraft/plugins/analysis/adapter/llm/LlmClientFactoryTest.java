package com.craftsmanbro.fulcraft.plugins.analysis.adapter.llm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.audit.impl.AuditLogger;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.usage.contract.UsageTrackerPort;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.LocalFileUsageStore;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.TokenUsageEstimator;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmClientFactoryTest {

  @BeforeEach
  void setUp() {
    LlmProviderRegistry.reinitialize();
  }

  @AfterEach
  void tearDown() {
    LlmProviderRegistry.reinitialize();
  }

  @Test
  void create_delegatesToInfrastructureFactory() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("mock");

    LlmClientPort client = LlmClientFactory.create(config);

    assertInstanceOf(TokenUsageAwareFacade.class, client);
    assertTrue(
        client
            .profile()
            .supports(com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability.SEED));
  }

  @Test
  void builder_buildDelegatesToInfrastructureFactory() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("mock");

    LlmClientPort client =
        LlmClientFactory.builder()
            .llmConfig(config)
            .enableUsageTracking(false)
            .enableQuotaEnforcement(false)
            .enableAuditLogging(false)
            .build();

    assertInstanceOf(TokenUsageAwareFacade.class, client);
    assertTrue(
        client
            .profile()
            .supports(com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability.SEED));
  }

  @Test
  void builder_methodsReturnThis() {
    LlmClientFactory.Builder builder = LlmClientFactory.builder();
    Config.LlmConfig config = new Config.LlmConfig();
    Config.QuotaConfig quotaConfig = new Config.QuotaConfig();
    UsageTrackerPort usageTracker = (scope, requestCount, tokenCount) -> {};
    TokenUsageEstimator tokenUsageEstimator = new TokenUsageEstimator();
    LocalFileUsageStore usageStore = new LocalFileUsageStore(Path.of("."));
    AuditLogger auditLogger = new AuditLogger(new Config(), Path.of("."));

    assertSame(builder, builder.llmConfig(config));
    assertSame(builder, builder.quotaConfig(quotaConfig));
    assertSame(builder, builder.usageTracker(usageTracker));
    assertSame(builder, builder.tokenEstimator(tokenUsageEstimator));
    assertSame(builder, builder.usageStore(usageStore));
    assertSame(builder, builder.auditLogger(auditLogger));
    assertSame(builder, builder.enableUsageTracking(false));
    assertSame(builder, builder.enableQuotaEnforcement(false));
    assertSame(builder, builder.enableAuditLogging(false));
  }

  @Test
  void create_throwsWhenConfigIsNull() {
    assertThrows(NullPointerException.class, () -> LlmClientFactory.create(null));
  }

  @Test
  void isProviderAvailable_reflectsRegistry() {
    TestProvider provider = new TestProvider();
    LlmProviderRegistry.registerProvider(provider);

    assertTrue(LlmClientFactory.isProviderAvailable("factory-test"));
  }

  @Test
  void isProviderAvailable_returnsFalseForUnknownProvider() {
    assertFalse(LlmClientFactory.isProviderAvailable("definitely-missing"));
  }

  @Test
  void getAvailableProviders_includesRegisteredProvider() {
    TestProvider provider = new TestProvider();
    LlmProviderRegistry.registerProvider(provider);

    assertTrue(LlmClientFactory.getAvailableProviders().contains("factory-test"));
  }

  private static final class TestProvider implements LlmClientProviderFacade {
    private final LlmClientPort client = new StubClient();

    @Override
    public String getProviderName() {
      return "factory-test";
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
    public com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile profile() {
      return new com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile(
          "factory-test", java.util.Set.of(), java.util.Optional.empty());
    }
  }
}
