package com.craftsmanbro.fulcraft.plugins.analysis.adapter.llm;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.audit.contract.AuditLogPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.usage.contract.UsageTrackerPort;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.LocalFileUsageStore;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.TokenUsageEstimator;

/** Analysis-layer facade over infrastructure LLM factory. */
public final class LlmClientFactory {

  private LlmClientFactory() {
    // Utility class
  }

  public static LlmClientPort create(final Config.LlmConfig config) {
    return LlmContractAdapter.toFeature(
        com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmClientFactory.create(config));
  }

  public static Builder builder() {
    return new Builder(
        com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmClientFactory.builder());
  }

  public static boolean isProviderAvailable(final String providerName) {
    return com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmClientFactory.isProviderAvailable(
        providerName);
  }

  public static java.util.List<String> getAvailableProviders() {
    return com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmClientFactory
        .getAvailableProviders();
  }

  public static final class Builder {

    private final com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmClientFactory.Builder
        delegate;

    private Builder(
        final com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmClientFactory.Builder delegate) {
      this.delegate = delegate;
    }

    public Builder llmConfig(final Config.LlmConfig config) {
      delegate.llmConfig(config);
      return this;
    }

    public Builder quotaConfig(final Config.QuotaConfig config) {
      delegate.quotaConfig(config);
      return this;
    }

    public Builder usageTracker(final UsageTrackerPort tracker) {
      delegate.usageTracker(tracker);
      return this;
    }

    public Builder tokenEstimator(final TokenUsageEstimator estimator) {
      delegate.tokenEstimator(estimator);
      return this;
    }

    public Builder usageStore(final LocalFileUsageStore store) {
      delegate.usageStore(store);
      return this;
    }

    public Builder auditLogger(final AuditLogPort logger) {
      delegate.auditLogger(logger);
      return this;
    }

    public Builder enableUsageTracking(final boolean enable) {
      delegate.enableUsageTracking(enable);
      return this;
    }

    public Builder enableQuotaEnforcement(final boolean enable) {
      delegate.enableQuotaEnforcement(enable);
      return this;
    }

    public Builder enableAuditLogging(final boolean enable) {
      delegate.enableAuditLogging(enable);
      return this;
    }

    public LlmClientPort build() {
      return LlmContractAdapter.toFeature(delegate.build());
    }
  }
}
