package com.craftsmanbro.fulcraft.infrastructure.config.validator.llm;

import com.craftsmanbro.fulcraft.config.Config;
import java.util.List;

/** Interface for validating LLM provider configurations. */
public interface LlmConfigValidator {

  /**
   * Validates the configuration object for a specific LLM provider.
   *
   * @param config The configuration object for the LLM section.
   * @param errors A list to collect validation error messages.
   */
  void validate(Config.LlmConfig config, List<String> errors);
}
