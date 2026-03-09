package com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientProvider;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.ExternalTransmissionDeniedException;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmProviderRegistry;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.util.Objects;

/**
 * Decorator that enforces governance rules for external LLM transmission.
 *
 * <p>When {@code governance.external_transmission} is set to {@code deny}, this decorator blocks
 * calls to external LLM providers and throws {@link ExternalTransmissionDeniedException}.
 */
public class LlmGovernanceEnforcingClient implements LlmClientPort {

  private static final String ARGUMENT_NULL_MESSAGE_KEY = "infra.common.error.argument_null";

  private final LlmClientPort delegate;

  private final Config.GovernanceConfig governanceConfig;

  /**
   * Creates a governance-enforcing LLM client.
   *
   * @param delegate The underlying LLM client to delegate to
   * @param governanceConfig Governance configuration (may be null to allow all)
   */
  public LlmGovernanceEnforcingClient(
      final LlmClientPort delegate, final Config.GovernanceConfig governanceConfig) {
    this.delegate = requireNonNullArgument(delegate, "delegate must not be null");
    this.governanceConfig = governanceConfig;
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
    requireNonNullArgument(prompt, "Prompt must not be null");
    requireNonNullArgument(llmConfig, "LlmConfig must not be null");
    checkGovernancePolicy(llmConfig);
    return delegate.generateTest(prompt, llmConfig);
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
  }

  private void checkGovernancePolicy(final Config.LlmConfig llmConfig) {
    if (governanceConfig == null || !governanceConfig.isExternalTransmissionDenied()) {
      return;
    }
    final String providerName = llmConfig.getProvider();
    if (!isExternalProvider(providerName)) {
      return;
    }
    Logger.error(
        "External LLM transmission blocked by governance policy (provider: " + providerName + ")");
    throw new ExternalTransmissionDeniedException(
        String.format(
            "governance.external_transmission=deny により外部LLM送信は禁止されています。"
                + " Provider: %s. 外部送信を許可するには governance.external_transmission を 'allow' に設定してください。",
            providerName));
  }

  private static <T> T requireNonNullArgument(final T value, final String argumentDescription) {
    return Objects.requireNonNull(value, argumentNullMessage(argumentDescription));
  }

  private static String argumentNullMessage(final String argumentDescription) {
    return MessageSource.getMessage(ARGUMENT_NULL_MESSAGE_KEY, argumentDescription);
  }

  private boolean isExternalProvider(final String providerName) {
    if (providerName == null) {
      return true;
    }
    return LlmProviderRegistry.findProvider(providerName)
        .map(LlmClientProvider::isExternalProvider)
        .orElse(true);
  }
}
