package com.craftsmanbro.fulcraft.infrastructure.validation.contract;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.validation.model.PreFlightCheckInput;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/** Contract for validating project prerequisites before command execution. */
public interface PreFlightCheckPort {

  void check(PreFlightCheckInput input);

  default void check(final Path projectRoot) {
    check(PreFlightCheckInput.of(projectRoot));
  }

  default void check(final Path projectRoot, final Config config) {
    check(PreFlightCheckInput.of(projectRoot, config));
  }

  default void check(final Path projectRoot, final Config config, final LlmClientPort llmClient) {
    // Keep null to indicate no LLM health probe is available.
    final BooleanSupplier llmHealthCheck = llmClient == null ? null : llmClient::isHealthy;
    check(projectRoot, config, llmHealthCheck);
  }

  default void check(
      final Path projectRoot, final Config config, final BooleanSupplier llmHealthCheck) {
    check(PreFlightCheckInput.of(projectRoot, config, llmHealthCheck));
  }

  default void check(final Path projectRoot, final LlmClientPort llmClient) {
    check(projectRoot, null, llmClient);
  }

  default void check(final Path projectRoot, final BooleanSupplier llmHealthCheck) {
    check(projectRoot, null, llmHealthCheck);
  }
}
