package com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.usage.contract.UsageTrackerPort;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.TokenUsageEstimator;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageScope;
import java.util.Objects;
import java.util.Optional;

public class LlmUsageTrackingClient implements LlmClientPort, TokenUsageAware {

  private final LlmClientPort delegate;

  private final UsageTrackerPort usageTracker;

  private final TokenUsageEstimator estimator;

  private final ThreadLocal<TokenUsage> lastUsage = new ThreadLocal<>();

  public LlmUsageTrackingClient(
      final LlmClientPort delegate,
      final UsageTrackerPort usageTracker,
      final TokenUsageEstimator estimator) {
    this.delegate =
        Objects.requireNonNull(
            delegate,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "delegate must not be null"));
    this.usageTracker =
        Objects.requireNonNull(
            usageTracker,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "usageTracker must not be null"));
    this.estimator =
        Objects.requireNonNull(
            estimator,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "estimator must not be null"));
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
    final String response = delegate.generateTest(prompt, llmConfig);
    recordUsage(prompt, response);
    return response;
  }

  @Override
  public boolean isHealthy() {
    return delegate.isHealthy();
  }

  @Override
  public ProviderProfile profile() {
    return delegate.profile();
  }

  @Override
  public void clearContext() {
    delegate.clearContext();
    lastUsage.remove();
  }

  @Override
  public Optional<TokenUsage> getLastUsage() {
    return Optional.ofNullable(lastUsage.get());
  }

  private void recordUsage(final String prompt, final String response) {
    final TokenUsage resolvedUsage = resolveTokenUsage(prompt, response);
    final long totalTokens = resolvedUsage != null ? resolvedUsage.effectiveTotal() : 0L;
    usageTracker.recordUsage(UsageScope.PROJECT, 1L, totalTokens);
    usageTracker.recordUsage(UsageScope.USER, 1L, totalTokens);
    if (resolvedUsage == null) {
      lastUsage.remove();
      return;
    }
    lastUsage.set(resolvedUsage);
  }

  private TokenUsage resolveTokenUsage(final String prompt, final String response) {
    if (delegate instanceof TokenUsageAware aware) {
      final Optional<TokenUsage> delegateUsage = aware.getLastUsage();
      if (delegateUsage.isPresent()) {
        return delegateUsage.get();
      }
    }

    final long promptTokens = estimator.estimateTokens(prompt);
    final long completionTokens = estimator.estimateTokens(response);
    final long totalTokens = Math.max(0L, promptTokens + completionTokens);
    return new TokenUsage(promptTokens, completionTokens, totalTokens);
  }
}
