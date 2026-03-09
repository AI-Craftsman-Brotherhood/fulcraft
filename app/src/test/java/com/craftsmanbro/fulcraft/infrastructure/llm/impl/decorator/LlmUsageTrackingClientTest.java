package com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.usage.contract.UsageTrackerPort;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.TokenUsageEstimator;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageScope;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LlmUsageTrackingClientTest {

  @Test
  void generateTest_recordsUsageFromDelegate() {
    UsageTrackerPort tracker = mock(UsageTrackerPort.class);
    TokenUsageEstimator estimator = mock(TokenUsageEstimator.class);
    TokenUsage usage = new TokenUsage(2, 3, 5);
    UsageAwareClient delegate = new UsageAwareClient("response", usage);

    LlmUsageTrackingClient client = new LlmUsageTrackingClient(delegate, tracker, estimator);

    String result = client.generateTest("prompt", new Config.LlmConfig());

    assertEquals("response", result);
    verify(tracker).recordUsage(UsageScope.PROJECT, 1L, 5L);
    verify(tracker).recordUsage(UsageScope.USER, 1L, 5L);
    assertSame(usage, client.getLastUsage().orElseThrow());
  }

  @Test
  void generateTest_usesEffectiveTotalWhenDelegateUsageTotalIsZero() {
    UsageTrackerPort tracker = mock(UsageTrackerPort.class);
    TokenUsageEstimator estimator = mock(TokenUsageEstimator.class);
    TokenUsage usage = new TokenUsage(2, 3, 0);
    UsageAwareClient delegate = new UsageAwareClient("response", usage);

    LlmUsageTrackingClient client = new LlmUsageTrackingClient(delegate, tracker, estimator);

    client.generateTest("prompt", new Config.LlmConfig());

    verify(tracker).recordUsage(UsageScope.PROJECT, 1L, 5L);
    verify(tracker).recordUsage(UsageScope.USER, 1L, 5L);
  }

  @Test
  void generateTest_estimatesUsageWhenDelegateMissing() {
    UsageTrackerPort tracker = mock(UsageTrackerPort.class);
    TokenUsageEstimator estimator = mock(TokenUsageEstimator.class);
    when(estimator.estimateTokens("prompt")).thenReturn(5L);
    when(estimator.estimateTokens("response")).thenReturn(7L);

    BasicClient delegate = new BasicClient("response");

    LlmUsageTrackingClient client = new LlmUsageTrackingClient(delegate, tracker, estimator);

    client.generateTest("prompt", new Config.LlmConfig());

    verify(tracker).recordUsage(UsageScope.PROJECT, 1L, 12L);
    verify(tracker).recordUsage(UsageScope.USER, 1L, 12L);
    TokenUsage recorded = client.getLastUsage().orElseThrow();
    assertEquals(5L, recorded.getPromptTokens());
    assertEquals(7L, recorded.getCompletionTokens());
    assertEquals(12L, recorded.getTotalTokens());
  }

  @Test
  void generateTest_estimatesUsageWhenDelegateReturnsEmptyUsage() {
    UsageTrackerPort tracker = mock(UsageTrackerPort.class);
    TokenUsageEstimator estimator = mock(TokenUsageEstimator.class);
    when(estimator.estimateTokens("prompt")).thenReturn(4L);
    when(estimator.estimateTokens("response")).thenReturn(6L);
    UsageAwareClient delegate = new UsageAwareClient("response", null);

    LlmUsageTrackingClient client = new LlmUsageTrackingClient(delegate, tracker, estimator);

    client.generateTest("prompt", new Config.LlmConfig());

    verify(tracker).recordUsage(UsageScope.PROJECT, 1L, 10L);
    verify(tracker).recordUsage(UsageScope.USER, 1L, 10L);
    TokenUsage recorded = client.getLastUsage().orElseThrow();
    assertEquals(4L, recorded.getPromptTokens());
    assertEquals(6L, recorded.getCompletionTokens());
    assertEquals(10L, recorded.getTotalTokens());
  }

  @Test
  void clearContext_clearsLastUsageAndDelegates() {
    UsageTrackerPort tracker = mock(UsageTrackerPort.class);
    TokenUsageEstimator estimator = mock(TokenUsageEstimator.class);
    when(estimator.estimateTokens(Mockito.anyString())).thenReturn(1L);

    BasicClient delegate = new BasicClient("response");
    LlmUsageTrackingClient client = new LlmUsageTrackingClient(delegate, tracker, estimator);

    client.generateTest("prompt", new Config.LlmConfig());
    assertTrue(client.getLastUsage().isPresent());

    client.clearContext();

    assertTrue(delegate.isClearCalled());
    assertTrue(client.getLastUsage().isEmpty());
  }

  @Test
  void delegatesHealthAndProfile() {
    UsageTrackerPort tracker = mock(UsageTrackerPort.class);
    TokenUsageEstimator estimator = mock(TokenUsageEstimator.class);
    LlmClientPort delegate = mock(LlmClientPort.class);
    ProviderProfile profile = new ProviderProfile("mock", Set.of(), Optional.empty());
    when(delegate.isHealthy()).thenReturn(false);
    when(delegate.profile()).thenReturn(profile);

    LlmUsageTrackingClient client = new LlmUsageTrackingClient(delegate, tracker, estimator);

    assertFalse(client.isHealthy());
    assertSame(profile, client.profile());
  }

  private static final class UsageAwareClient implements LlmClientPort, TokenUsageAware {
    private final String response;
    private final TokenUsage usage;

    private UsageAwareClient(String response, TokenUsage usage) {
      this.response = response;
      this.usage = usage;
    }

    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      return response;
    }

    @Override
    public boolean isHealthy() {
      return true;
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile("usage-aware", Set.of(), Optional.empty());
    }

    @Override
    public void clearContext() {}

    @Override
    public Optional<TokenUsage> getLastUsage() {
      return Optional.ofNullable(usage);
    }
  }

  private static final class BasicClient implements LlmClientPort {
    private final String response;
    private boolean clearCalled;

    private BasicClient(String response) {
      this.response = response;
    }

    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      return response;
    }

    @Override
    public boolean isHealthy() {
      return true;
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile("basic", Set.of(), Optional.empty());
    }

    @Override
    public void clearContext() {
      clearCalled = true;
    }

    private boolean isClearCalled() {
      return clearCalled;
    }
  }
}
