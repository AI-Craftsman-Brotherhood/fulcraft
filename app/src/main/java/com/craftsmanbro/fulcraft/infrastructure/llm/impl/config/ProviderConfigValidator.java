package com.craftsmanbro.fulcraft.infrastructure.llm.impl.config;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates the compatibility between user configuration and the LLM provider's capabilities.
 *
 * <p>This validator performs pre-flight checks to warn or fail early when the config expects
 * features that the selected provider does not support.
 */
public final class ProviderConfigValidator {

  private ProviderConfigValidator() {
    // Utility class
  }

  /** Validation message levels. */
  public enum Level {
    WARN,
    ERROR
  }

  /**
   * A single validation result.
   *
   * @param level The severity level (WARN or ERROR).
   * @param message A human-readable description of the issue.
   */
  public record ValidationMessage(Level level, String message) {}

  /**
   * Validates the given configuration against the provider's capabilities.
   *
   * @param config The application configuration.
   * @param profile The provider profile describing its capabilities.
   * @return A list of validation messages (empty if no issues).
   */
  public static List<ValidationMessage> validate(
      final Config config, final ProviderProfile profile) {
    final List<ValidationMessage> messages = new ArrayList<>();
    if (config == null || profile == null) {
      return messages;
    }
    final Config.LlmConfig llmConfig = config.getLlm();
    if (llmConfig == null) {
      return messages;
    }
    // Check A: deterministic mode requires SEED capability
    validateDeterministicMode(llmConfig, profile, messages);
    // Check B: system message support
    validateSystemMessage(llmConfig, profile, messages);
    // Check C: JSON_OUTPUT / TOOL_CALLING (no current config flags for these,
    // skipping)
    // Future: if config.getLlm().getJsonModeEnabled() etc., check capability
    return messages;
  }

  private static void validateDeterministicMode(
      final Config.LlmConfig llmConfig,
      final ProviderProfile profile,
      final List<ValidationMessage> messages) {
    if (!Boolean.TRUE.equals(llmConfig.getDeterministic())) {
      return;
    }
    final Double temperature = llmConfig.getTemperature();
    if (temperature != null && Double.compare(temperature, 0.0) != 0) {
      messages.add(
          new ValidationMessage(
              Level.WARN,
              "Deterministic mode is enabled, so temperature is forced to 0.0. "
                  + "The configured temperature will be ignored."));
    }
    if (!profile.supports(Capability.SEED)) {
      messages.add(
          new ValidationMessage(
              Level.WARN,
              String.format(
                  "Deterministic mode is enabled, but provider '%s' does not support SEED. "
                      + "Output may not be reproducible.",
                  profile.providerName())));
    }
  }

  private static void validateSystemMessage(
      final Config.LlmConfig llmConfig,
      final ProviderProfile profile,
      final List<ValidationMessage> messages) {
    final String systemMessage = llmConfig.getSystemMessage();
    if (systemMessage == null || systemMessage.isBlank()) {
      return;
    }
    if (profile.supports(Capability.SYSTEM_MESSAGE)) {
      return;
    }
    messages.add(
        new ValidationMessage(
            Level.WARN,
            String.format(
                "System message is configured, but provider '%s' does not support SYSTEM_MESSAGE. "
                    + "It will be ignored.",
                profile.providerName())));
  }
}
