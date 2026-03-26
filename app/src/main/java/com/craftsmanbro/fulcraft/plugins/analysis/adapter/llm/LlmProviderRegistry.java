package com.craftsmanbro.fulcraft.plugins.analysis.adapter.llm;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientProvider;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Analysis-layer registry that adapts infrastructure providers to feature contracts. */
public final class LlmProviderRegistry {

  private LlmProviderRegistry() {
    // Utility class
  }

  public static Optional<LlmClientProviderFacade> findProvider(final String providerName) {
    return com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry.findProvider(
            providerName)
        .map(LlmProviderRegistry::wrap);
  }

  public static LlmClientProviderFacade getProvider(final String providerName) {
    return wrap(
        com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry.getProvider(
            providerName));
  }

  public static Optional<LlmClientProviderFacade> findProviderForConfig(
      final Config.LlmConfig config) {
    return com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry
        .findProviderForConfig(config)
        .map(LlmProviderRegistry::wrap);
  }

  public static void registerProvider(final LlmClientProviderFacade provider) {
    Objects.requireNonNull(
        provider,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "provider must not be null"));
    com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry.registerProvider(
        new FacadeBackedProvider(provider));
  }

  public static void registerProvider(final LlmClientProvider provider) {
    com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry.registerProvider(
        provider);
  }

  public static LlmClientProviderFacade unregisterProvider(final String providerName) {
    final LlmClientProvider removedProvider =
        com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry.unregisterProvider(
            providerName);
    if (removedProvider == null) {
      return null;
    }
    return wrap(removedProvider);
  }

  public static List<String> getProviderNames() {
    return com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry.getProviderNames();
  }

  public static void clear() {
    com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry.clear();
  }

  public static void reinitialize() {
    com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry.reinitialize();
  }

  private static LlmClientProviderFacade wrap(final LlmClientProvider provider) {
    if (provider instanceof FacadeBackedProvider backedProvider) {
      return backedProvider.delegate;
    }
    return new ProviderFacade(provider);
  }

  private static final class FacadeBackedProvider implements LlmClientProvider {

    private final LlmClientProviderFacade delegate;

    private FacadeBackedProvider(final LlmClientProviderFacade delegate) {
      this.delegate =
          Objects.requireNonNull(
              delegate,
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "analysis.common.error.argument_null", "delegate must not be null"));
    }

    @Override
    public String getProviderName() {
      return delegate.getProviderName();
    }

    @Override
    public String[] getAliases() {
      return delegate.getAliases();
    }

    @Override
    public LlmClientPort create(final Config.LlmConfig config) {
      return LlmContractAdapter.toInfrastructure(delegate.create(config));
    }

    @Override
    public boolean supports(final Config.LlmConfig config) {
      return delegate.supports(config);
    }

    @Override
    public int getPriority() {
      return delegate.getPriority();
    }

    @Override
    public boolean isExternalProvider() {
      return delegate.isExternalProvider();
    }
  }

  private static final class ProviderFacade implements LlmClientProviderFacade {

    private final LlmClientProvider delegate;

    private ProviderFacade(final LlmClientProvider delegate) {
      this.delegate =
          Objects.requireNonNull(
              delegate,
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "analysis.common.error.argument_null", "provider must not be null"));
    }

    @Override
    public String getProviderName() {
      return delegate.getProviderName();
    }

    @Override
    public String[] getAliases() {
      return delegate.getAliases();
    }

    @Override
    public LlmClientPort create(final Config.LlmConfig config) {
      return LlmContractAdapter.toFeature(delegate.create(config));
    }

    @Override
    public boolean supports(final Config.LlmConfig config) {
      return delegate.supports(config);
    }

    @Override
    public int getPriority() {
      return delegate.getPriority();
    }

    @Override
    public boolean isExternalProvider() {
      return delegate.isExternalProvider();
    }
  }
}
