package com.craftsmanbro.fulcraft.infrastructure.llm.impl.config;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.request.LlmRequest;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;

/**
 * Utility to check if request parameters are supported by the provider and emit warnings when they
 * are not.
 *
 * <p>This helps users understand why reproducibility might be affected without breaking execution.
 */
public final class RequestParamWarner {

  private RequestParamWarner() {
    // Utility class
  }

  /**
   * Check if any request parameters are unsupported by the provider and emit warnings (once per
   * provider+parameter combination).
   *
   * <p>The warning is only emitted once per unique key to prevent log spam.
   *
   * @param profile the provider profile with capabilities
   * @param request the LLM request with generation parameters
   */
  public static void warnIfUnsupported(final ProviderProfile profile, final LlmRequest request) {
    if (profile == null || request == null) {
      return;
    }
    warnIfUnsupported(profile, request.getSeed());
  }

  /**
   * Check if the seed parameter is unsupported by the provider and emit a warning (once per
   * provider+parameter combination).
   *
   * @param profile the provider profile with capabilities
   * @param seed the seed value to validate
   */
  public static void warnIfUnsupported(final ProviderProfile profile, final Integer seed) {
    if (profile == null || seed == null) {
      return;
    }
    // Check SEED capability
    if (profile.supports(Capability.SEED)) {
      return;
    }
    final String providerName = profile.providerName();
    final String key = providerName + ":SEED";
    final String message =
        String.format(
            "Seed is set (seed=%d) but provider '%s' does not support SEED. "
                + "Output may not be reproducible.",
            seed, providerName);
    Logger.warnOnce(key, message);
    // Future: Add checks for other capabilities (TOP_P, JSON_OUTPUT, etc.)
  }
}
