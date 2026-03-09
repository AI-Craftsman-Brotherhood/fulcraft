package com.craftsmanbro.fulcraft.infrastructure.llm.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.audit.impl.AuditLogger;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmAuditLoggingClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmQuotaEnforcingClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmUsageTrackingClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.MockLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.usage.contract.UsageTrackerPort;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.LocalFileUsageStore;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.TokenUsageEstimator;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link LlmClientFactory.Builder}. */
class LlmClientFactoryBuilderTest {

  private Config.LlmConfig mockLlmConfig;

  @BeforeEach
  void setUp() {
    LlmProviderRegistry.reinitialize();

    mockLlmConfig = new Config.LlmConfig();
    mockLlmConfig.setProvider("mock");
  }

  @Test
  void build_createsBaseMockClient() {
    LlmClientPort client =
        LlmClientFactory.builder()
            .llmConfig(mockLlmConfig)
            .enableUsageTracking(false)
            .enableQuotaEnforcement(false)
            .enableAuditLogging(false)
            .build();

    assertNotNull(client);
    assertInstanceOf(MockLlmClient.class, client);
  }

  @Test
  void build_appliesUsageTrackingDecorator() {
    UsageTrackerPort tracker = mock(UsageTrackerPort.class);
    TokenUsageEstimator estimator = new TokenUsageEstimator();

    LlmClientPort client =
        LlmClientFactory.builder()
            .llmConfig(mockLlmConfig)
            .usageTracker(tracker)
            .tokenEstimator(estimator)
            .enableQuotaEnforcement(false)
            .enableAuditLogging(false)
            .build();

    assertNotNull(client);
    assertInstanceOf(LlmUsageTrackingClient.class, client);
  }

  @Test
  void build_appliesQuotaEnforcementDecorator() {
    LocalFileUsageStore store = mock(LocalFileUsageStore.class);
    Config.QuotaConfig quotaConfig = new Config.QuotaConfig();

    LlmClientPort client =
        LlmClientFactory.builder()
            .llmConfig(mockLlmConfig)
            .usageStore(store)
            .quotaConfig(quotaConfig)
            .enableUsageTracking(false)
            .enableAuditLogging(false)
            .build();

    assertNotNull(client);
    assertInstanceOf(LlmQuotaEnforcingClient.class, client);
  }

  @Test
  void build_appliesAuditLoggingDecorator() {
    AuditLogger logger = mock(AuditLogger.class);

    LlmClientPort client =
        LlmClientFactory.builder()
            .llmConfig(mockLlmConfig)
            .auditLogger(logger)
            .enableUsageTracking(false)
            .enableQuotaEnforcement(false)
            .build();

    assertNotNull(client);
    assertInstanceOf(LlmAuditLoggingClient.class, client);
  }

  @Test
  void build_appliesAllDecoratorsInCorrectOrder() {
    UsageTrackerPort tracker = mock(UsageTrackerPort.class);
    LocalFileUsageStore store = mock(LocalFileUsageStore.class);
    AuditLogger logger = mock(AuditLogger.class);

    LlmClientPort client =
        LlmClientFactory.builder()
            .llmConfig(mockLlmConfig)
            .usageTracker(tracker)
            .usageStore(store)
            .auditLogger(logger)
            .build();

    // Outermost decorator should be audit logging
    assertInstanceOf(LlmAuditLoggingClient.class, client);
  }

  @Test
  void build_appliesDecoratorsInExpectedNestedOrder() throws Exception {
    UsageTrackerPort tracker = mock(UsageTrackerPort.class);
    LocalFileUsageStore store = mock(LocalFileUsageStore.class);
    AuditLogger logger = mock(AuditLogger.class);

    LlmClientPort client =
        LlmClientFactory.builder()
            .llmConfig(mockLlmConfig)
            .usageTracker(tracker)
            .usageStore(store)
            .auditLogger(logger)
            .build();

    Object quotaLayer = delegateOf(client);
    Object trackingLayer = delegateOf(quotaLayer);
    Object baseClient = delegateOf(trackingLayer);

    assertInstanceOf(LlmAuditLoggingClient.class, client);
    assertInstanceOf(LlmQuotaEnforcingClient.class, quotaLayer);
    assertInstanceOf(LlmUsageTrackingClient.class, trackingLayer);
    assertInstanceOf(MockLlmClient.class, baseClient);
  }

  @Test
  void build_throwsWhenLlmConfigIsNull() {
    var builder = LlmClientFactory.builder();
    assertThrows(NullPointerException.class, builder::build);
  }

  @Test
  void build_skipsDecoratorWhenDependencyNotProvided() {
    // No tracker provided, usage tracking should be skipped
    LlmClientPort client =
        LlmClientFactory.builder()
            .llmConfig(mockLlmConfig)
            .enableUsageTracking(true) // enabled but no tracker
            .enableQuotaEnforcement(false)
            .enableAuditLogging(false)
            .build();

    // Should return base client since tracker is null
    assertInstanceOf(MockLlmClient.class, client);
  }

  @Test
  void build_createsDefaultTokenEstimator() {
    UsageTrackerPort tracker = mock(UsageTrackerPort.class);

    // No estimator provided explicitly
    LlmClientPort client =
        LlmClientFactory.builder()
            .llmConfig(mockLlmConfig)
            .usageTracker(tracker)
            .enableQuotaEnforcement(false)
            .enableAuditLogging(false)
            .build();

    // Should still create the decorator with default estimator
    assertInstanceOf(LlmUsageTrackingClient.class, client);
  }

  @Test
  void build_usesUsageStoreAsTrackerWhenExplicitTrackerIsMissing() {
    LocalFileUsageStore store = mock(LocalFileUsageStore.class);

    LlmClientPort client =
        LlmClientFactory.builder()
            .llmConfig(mockLlmConfig)
            .usageStore(store)
            .enableQuotaEnforcement(false)
            .enableAuditLogging(false)
            .build();

    assertInstanceOf(LlmUsageTrackingClient.class, client);
  }

  @Test
  void build_skipsUsageTrackingWhenDisabledEvenIfTrackerExists() {
    UsageTrackerPort tracker = mock(UsageTrackerPort.class);

    LlmClientPort client =
        LlmClientFactory.builder()
            .llmConfig(mockLlmConfig)
            .usageTracker(tracker)
            .enableUsageTracking(false)
            .enableQuotaEnforcement(false)
            .enableAuditLogging(false)
            .build();

    assertInstanceOf(MockLlmClient.class, client);
  }

  @Test
  void builder_methodsReturnThis() {
    LlmClientFactory.Builder builder = LlmClientFactory.builder();

    assertSame(builder, builder.llmConfig(mockLlmConfig));
    assertSame(builder, builder.enableUsageTracking(true));
    assertSame(builder, builder.enableQuotaEnforcement(true));
    assertSame(builder, builder.enableAuditLogging(true));
    assertSame(builder, builder.usageTracker(mock(UsageTrackerPort.class)));
    assertSame(builder, builder.tokenEstimator(new TokenUsageEstimator()));
    assertSame(builder, builder.usageStore(mock(LocalFileUsageStore.class)));
    assertSame(builder, builder.quotaConfig(new Config.QuotaConfig()));
    assertSame(builder, builder.auditLogger(mock(AuditLogger.class)));
  }

  private static Object delegateOf(Object decorator) throws Exception {
    Field field = decorator.getClass().getDeclaredField("delegate");
    field.setAccessible(true);
    return field.get(decorator);
  }
}
