package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Facade for source path resolution within the analysis package.
 *
 * <p>This class provides a stable API for resolving source paths, abstracting the underlying
 * implementation details.
 */
public final class SourcePaths {

  private static final SourcePathResolver RESOLVER = new SourcePathResolver();

  private SourcePaths() {
    // Utility class
  }

  /**
   * Resolves source directories for a project.
   *
   * @param projectRoot the project root directory
   * @param config the configuration
   * @return resolved source directories
   */
  public static SourcePathResolver.SourceDirectories resolve(
      final Path projectRoot, final Config config) {
    Objects.requireNonNull(
        projectRoot,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "projectRoot must not be null"));
    return RESOLVER.resolve(projectRoot, config);
  }
}
