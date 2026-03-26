package com.craftsmanbro.fulcraft.infrastructure.buildtool.contract;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.ClasspathResolutionResult;
import java.nio.file.Path;

/** Contract for resolving compile classpath entries from build tool metadata. */
@FunctionalInterface
public interface ClasspathResolverPort {

  /**
   * Resolves the compile classpath for the given project root.
   *
   * @param projectRoot root directory of the target project
   * @param config application configuration used during resolution
   */
  ClasspathResolutionResult resolveCompileClasspath(Path projectRoot, Config config);
}
