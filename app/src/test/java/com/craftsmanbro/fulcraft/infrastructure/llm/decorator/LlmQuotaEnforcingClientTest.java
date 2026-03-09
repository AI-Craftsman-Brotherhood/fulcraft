package com.craftsmanbro.fulcraft.infrastructure.llm.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmQuotaEnforcingClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.LocalFileUsageStore;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageRecord;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageScope;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LlmQuotaEnforcingClientTest {

  private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

  private LlmClientPort delegate;
  private LocalFileUsageStore usageStore;
  private Clock clock;

  @BeforeEach
  void setUp() {
    delegate = mock(LlmClientPort.class);
    when(delegate.generateTest(Mockito.anyString(), Mockito.any(Config.LlmConfig.class)))
        .thenReturn("ok");
    usageStore = mock(LocalFileUsageStore.class);
    clock = Clock.fixed(Instant.parse("2025-01-15T00:00:00Z"), ZoneOffset.UTC);
  }

  @Test
  void generateTest_allowsWhenQuotaConfigIsNull() {
    LlmQuotaEnforcingClient client = new LlmQuotaEnforcingClient(delegate, usageStore, null, clock);

    String result = client.generateTest("prompt", new Config.LlmConfig());

    assertEquals("ok", result);
    verify(delegate).generateTest(Mockito.eq("prompt"), Mockito.any(Config.LlmConfig.class));
    verifyNoInteractions(usageStore);
  }

  @Test
  void generateTest_allowsWhenMaxLlmCallsIsNull() {
    Config.QuotaConfig quotaConfig = new Config.QuotaConfig();
    quotaConfig.setMaxLlmCalls(null);
    LlmQuotaEnforcingClient client =
        new LlmQuotaEnforcingClient(delegate, usageStore, quotaConfig, clock);

    String result = client.generateTest("prompt", new Config.LlmConfig());

    assertEquals("ok", result);
    verify(delegate).generateTest(Mockito.eq("prompt"), Mockito.any(Config.LlmConfig.class));
    verifyNoInteractions(usageStore);
  }

  @Test
  void generateTest_allowsWhenWithinQuota() {
    Config.QuotaConfig quotaConfig = new Config.QuotaConfig();
    quotaConfig.setMaxLlmCalls(2);
    when(usageStore.getSnapshot()).thenReturn(snapshotWithMonthlyCount(1));

    LlmQuotaEnforcingClient client =
        new LlmQuotaEnforcingClient(delegate, usageStore, quotaConfig, clock);

    String result = client.generateTest("prompt", new Config.LlmConfig());

    assertEquals("ok", result);
    verify(delegate).generateTest(Mockito.eq("prompt"), Mockito.any(Config.LlmConfig.class));
    verify(usageStore).getSnapshot();
  }

  @Test
  void generateTest_blocksWhenExceededAndOnExceedBlock() {
    Config.QuotaConfig quotaConfig = new Config.QuotaConfig();
    quotaConfig.setMaxLlmCalls(1);
    quotaConfig.setOnExceed("block");
    when(usageStore.getSnapshot()).thenReturn(snapshotWithMonthlyCount(1));

    LlmQuotaEnforcingClient client =
        new LlmQuotaEnforcingClient(delegate, usageStore, quotaConfig, clock);

    assertThrows(
        IllegalStateException.class, () -> client.generateTest("prompt", new Config.LlmConfig()));

    verify(delegate, never())
        .generateTest(Mockito.anyString(), Mockito.any(Config.LlmConfig.class));
    verify(usageStore).getSnapshot();
  }

  @Test
  void generateTest_warnsWhenExceededAndOnExceedWarn() {
    Config.QuotaConfig quotaConfig = new Config.QuotaConfig();
    quotaConfig.setMaxLlmCalls(0);
    quotaConfig.setOnExceed("warn");
    when(usageStore.getSnapshot()).thenReturn(snapshotWithMonthlyCount(0));

    LlmQuotaEnforcingClient client =
        new LlmQuotaEnforcingClient(delegate, usageStore, quotaConfig, clock);

    String result = client.generateTest("prompt", new Config.LlmConfig());

    assertEquals("ok", result);
    verify(delegate).generateTest(Mockito.eq("prompt"), Mockito.any(Config.LlmConfig.class));
    verify(usageStore).getSnapshot();
  }

  @Test
  void generateTest_warnsWhenExceededAndOnExceedInvalidValue() {
    Config.QuotaConfig quotaConfig = new Config.QuotaConfig();
    quotaConfig.setMaxLlmCalls(0);
    quotaConfig.setOnExceed("invalid");
    when(usageStore.getSnapshot()).thenReturn(snapshotWithMonthlyCount(0));

    LlmQuotaEnforcingClient client =
        new LlmQuotaEnforcingClient(delegate, usageStore, quotaConfig, clock);

    String result = client.generateTest("prompt", new Config.LlmConfig());

    assertEquals("ok", result);
    verify(delegate).generateTest(Mockito.eq("prompt"), Mockito.any(Config.LlmConfig.class));
    verify(usageStore).getSnapshot();
  }

  @Test
  void generateTest_allowsWhenSnapshotMissing() {
    Config.QuotaConfig quotaConfig = new Config.QuotaConfig();
    quotaConfig.setMaxLlmCalls(1);
    when(usageStore.getSnapshot()).thenReturn(null);

    LlmQuotaEnforcingClient client =
        new LlmQuotaEnforcingClient(delegate, usageStore, quotaConfig, clock);

    String result = client.generateTest("prompt", new Config.LlmConfig());

    assertEquals("ok", result);
    verify(delegate).generateTest(Mockito.eq("prompt"), Mockito.any(Config.LlmConfig.class));
    verify(usageStore).getSnapshot();
  }

  @Test
  void generateTest_clampsNegativeMonthlyCountToZero() {
    Config.QuotaConfig quotaConfig = new Config.QuotaConfig();
    quotaConfig.setMaxLlmCalls(1);
    when(usageStore.getSnapshot()).thenReturn(snapshotWithMonthlyCount(-5));

    LlmQuotaEnforcingClient client =
        new LlmQuotaEnforcingClient(delegate, usageStore, quotaConfig, clock);

    String result = client.generateTest("prompt", new Config.LlmConfig());

    assertEquals("ok", result);
    verify(delegate).generateTest(Mockito.eq("prompt"), Mockito.any(Config.LlmConfig.class));
    verify(usageStore).getSnapshot();
  }

  @Test
  void getLastUsage_delegatesWhenUsageAware() {
    LlmClientPort usageAware =
        mock(LlmClientPort.class, Mockito.withSettings().extraInterfaces(TokenUsageAware.class));
    TokenUsage usage = new TokenUsage(1, 2, 3);
    when(((TokenUsageAware) usageAware).getLastUsage()).thenReturn(Optional.of(usage));

    LlmQuotaEnforcingClient client =
        new LlmQuotaEnforcingClient(usageAware, usageStore, null, clock);

    assertSame(usage, client.getLastUsage().orElseThrow());
  }

  @Test
  void getLastUsage_returnsEmptyWhenDelegateIsNotUsageAware() {
    LlmQuotaEnforcingClient client = new LlmQuotaEnforcingClient(delegate, usageStore, null, clock);

    assertTrue(client.getLastUsage().isEmpty());
  }

  @Test
  void delegatesHealthProfileAndClearContext() {
    ProviderProfile profile = new ProviderProfile("quota", Set.of(), Optional.empty());
    when(delegate.isHealthy()).thenReturn(false);
    when(delegate.profile()).thenReturn(profile);
    LlmQuotaEnforcingClient client = new LlmQuotaEnforcingClient(delegate, usageStore, null, clock);

    assertFalse(client.isHealthy());
    assertSame(profile, client.profile());
    client.clearContext();
    verify(delegate).clearContext();
  }

  private UsageSnapshot snapshotWithMonthlyCount(long requestCount) {
    UsageSnapshot snapshot = new UsageSnapshot();
    String monthKey = MONTH_FORMAT.format(LocalDate.now(clock));
    UsageSnapshot.ScopeUsage scope = snapshot.getOrCreateScope(UsageScope.PROJECT.key());
    UsageRecord record = scope.getOrCreateMonth(monthKey);
    record.setRequestCount(requestCount);
    return snapshot;
  }
}
