package com.craftsmanbro.fulcraft.infrastructure.validation.model;

import com.craftsmanbro.fulcraft.config.Config;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Input model for pre-flight validation. */
public record PreFlightCheckInput(Path projectRoot, Config config, BooleanSupplier llmHealthCheck) {

  public PreFlightCheckInput {
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "Project root must not be null"));
  }

  public static PreFlightCheckInput of(final Path projectRoot) {
    return new PreFlightCheckInput(projectRoot, null, null);
  }

  public static PreFlightCheckInput of(final Path projectRoot, final Config config) {
    return new PreFlightCheckInput(projectRoot, config, null);
  }

  public static PreFlightCheckInput of(
      final Path projectRoot, final Config config, final BooleanSupplier llmHealthCheck) {
    return new PreFlightCheckInput(projectRoot, config, llmHealthCheck);
  }

  public Path normalizedProjectRoot() {
    return projectRoot.toAbsolutePath().normalize();
  }
}
