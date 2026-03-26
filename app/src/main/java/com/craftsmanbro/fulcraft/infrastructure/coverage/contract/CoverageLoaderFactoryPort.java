package com.craftsmanbro.fulcraft.infrastructure.coverage.contract;

import com.craftsmanbro.fulcraft.config.Config;
import java.nio.file.Path;

/**
 * Contract for creating coverage loader instances from a project root and application
 * configuration.
 */
@FunctionalInterface
public interface CoverageLoaderFactoryPort {

  /**
   * Creates a coverage loader for the given project root and application configuration.
   *
   * @param projectRoot root directory of the target project
   * @param config application configuration used during resolution
   * @return the coverage loader, or {@code null} if no compatible coverage loader can be resolved
   */
  CoverageLoaderPort createCoverageLoader(Path projectRoot, Config config);
}
