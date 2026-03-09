package com.craftsmanbro.fulcraft.plugins.analysis.adapter.llm;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Converts LLM contracts between infrastructure and feature layers. */
public final class LlmContractAdapter {

  private LlmContractAdapter() {
    // Utility class
  }

  public static LlmClientPort toFeature(final LlmClientPort infrastructureClient) {
    Objects.requireNonNull(
        infrastructureClient,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "infrastructureClient must not be null"));
    if (infrastructureClient instanceof InfrastructureView infrastructureView) {
      return infrastructureView.delegate;
    }
    return new FeatureView(infrastructureClient);
  }

  public static LlmClientPort toInfrastructure(final LlmClientPort featureClient) {
    Objects.requireNonNull(
        featureClient,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "featureClient must not be null"));
    if (featureClient instanceof FeatureView featureView) {
      return featureView.delegate;
    }
    return new InfrastructureView(featureClient);
  }

  private static ProviderProfile toFeatureProfile(final ProviderProfile infrastructureProfile) {
    if (infrastructureProfile == null) {
      return null;
    }
    final Set<Capability> capabilities =
        infrastructureProfile.capabilities().stream()
            .map(capability -> Capability.valueOf(capability.name()))
            .collect(Collectors.toUnmodifiableSet());
    return new ProviderProfile(
        infrastructureProfile.providerName(), capabilities, infrastructureProfile.notes());
  }

  private static ProviderProfile toInfrastructureProfile(final ProviderProfile featureProfile) {
    if (featureProfile == null) {
      return null;
    }
    final Set<Capability> capabilities =
        featureProfile.capabilities().stream()
            .map(capability -> Capability.valueOf(capability.name()))
            .collect(Collectors.toUnmodifiableSet());
    return new ProviderProfile(
        featureProfile.providerName(), capabilities, featureProfile.notes());
  }

  private static final class FeatureView implements LlmClientPort, TokenUsageAwareFacade {

    private final LlmClientPort delegate;

    private FeatureView(final LlmClientPort delegate) {
      this.delegate =
          Objects.requireNonNull(
              delegate,
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "analysis.common.error.argument_null", "delegate must not be null"));
    }

    @Override
    public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
      return delegate.generateTest(prompt, llmConfig);
    }

    @Override
    public ProviderProfile profile() {
      return toFeatureProfile(delegate.profile());
    }

    @Override
    public boolean isHealthy() {
      return delegate.isHealthy();
    }

    @Override
    public void clearContext() {
      delegate.clearContext();
    }

    @Override
    public Optional<TokenUsage> getLastUsage() {
      if (!(delegate instanceof TokenUsageAware aware)) {
        return Optional.empty();
      }
      return aware.getLastUsage();
    }
  }

  private static final class InfrastructureView
      implements LlmClientPort, TokenUsageAware {

    private final LlmClientPort delegate;

    private InfrastructureView(final LlmClientPort delegate) {
      this.delegate =
          Objects.requireNonNull(
              delegate,
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "analysis.common.error.argument_null", "delegate must not be null"));
    }

    @Override
    public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
      return delegate.generateTest(prompt, llmConfig);
    }

    @Override
    public ProviderProfile profile() {
      return toInfrastructureProfile(delegate.profile());
    }

    @Override
    public boolean isHealthy() {
      return delegate.isHealthy();
    }

    @Override
    public void clearContext() {
      delegate.clearContext();
    }

    @Override
    public Optional<TokenUsage> getLastUsage() {
      if (!(delegate instanceof TokenUsageAware aware)) {
        return Optional.empty();
      }
      return aware.getLastUsage();
    }
  }
}
