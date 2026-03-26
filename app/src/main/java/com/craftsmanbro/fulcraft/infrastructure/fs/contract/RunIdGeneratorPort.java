package com.craftsmanbro.fulcraft.infrastructure.fs.contract;

/** Contract for generating run identifiers. */
@FunctionalInterface
public interface RunIdGeneratorPort {

  /**
   * Generates a run identifier.
   *
   * @return generated run identifier
   */
  String generateRunId();
}
