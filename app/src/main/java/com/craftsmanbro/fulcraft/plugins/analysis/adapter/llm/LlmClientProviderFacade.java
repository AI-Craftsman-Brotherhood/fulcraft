package com.craftsmanbro.fulcraft.plugins.analysis.adapter.llm;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import java.util.Locale;

/**
 * Analysis-layer provider SPI that stays stable even when infrastructure contracts evolve.
 *
 * <p>Implementations are adapted to/from infrastructure providers inside {@link
 * LlmProviderRegistry}.
 */
public interface LlmClientProviderFacade {

  String getProviderName();

  default String[] getAliases() {
    return new String[0];
  }

  LlmClientPort create(Config.LlmConfig config);

  default boolean supports(final Config.LlmConfig config) {
    if (config == null || config.getProvider() == null) {
      return false;
    }
    final String provider = normalizeKey(config.getProvider());
    if (provider.equals(normalizeKey(getProviderName()))) {
      return true;
    }
    for (final String alias : getAliases()) {
      if (alias == null) {
        continue;
      }
      if (provider.equals(normalizeKey(alias))) {
        return true;
      }
    }
    return false;
  }

  default int getPriority() {
    return 0;
  }

  default boolean isExternalProvider() {
    return true;
  }

  private static String normalizeKey(final String name) {
    return name.toLowerCase(Locale.ROOT).replace("_", "-");
  }
}
