package com.craftsmanbro.fulcraft.plugins.analysis.flow;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.index.ProjectSymbolIndex;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess.SourcePreprocessingService;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess.SourcePreprocessor;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Flow responsible for source code preprocessing.
 *
 * <p>This flow delegates to {@link SourcePreprocessingService} and keeps the pipeline wiring
 * stable.
 */
public class SourcePreprocessingFlow {

  private final SourcePreprocessingService sourcePreprocessingService;

  public SourcePreprocessingFlow() {
    this.sourcePreprocessingService = new SourcePreprocessingService();
  }

  /** Constructor for testing with a custom service. */
  SourcePreprocessingFlow(final SourcePreprocessingService sourcePreprocessingService) {
    this.sourcePreprocessingService = Objects.requireNonNull(sourcePreprocessingService);
  }

  /**
   * Preprocesses source code based on configuration.
   *
   * @param projectRoot the project root directory
   * @param config the configuration
   * @param outputDir the output directory for preprocessing artifacts
   * @return the preprocessing result
   */
  public SourcePreprocessor.Result preprocess(
      final Path projectRoot, final Config config, final Path outputDir) {
    return sourcePreprocessingService.preprocess(projectRoot, config, outputDir);
  }

  /**
   * Checks if preprocessing failed in STRICT mode.
   *
   * @param result the preprocessing result
   * @return true if preprocessing failed in STRICT mode
   */
  public boolean isStrictModeFailure(final SourcePreprocessor.Result result) {
    return sourcePreprocessingService.isStrictModeFailure(result);
  }

  /**
   * Gets the failure reason from preprocessing result.
   *
   * @param result the preprocessing result
   * @return the failure reason or null if not failed
   */
  public String getFailureReason(final SourcePreprocessor.Result result) {
    return sourcePreprocessingService.getFailureReason(result);
  }

  /**
   * Builds a project symbol index for type resolution.
   *
   * @param projectRoot the project root directory
   * @param config the configuration
   * @param preprocessResult the preprocessing result
   * @return the project symbol index
   */
  public ProjectSymbolIndex buildProjectSymbolIndex(
      final Path projectRoot,
      final Config config,
      final SourcePreprocessor.Result preprocessResult) {
    return sourcePreprocessingService.buildProjectSymbolIndex(
        projectRoot, config, preprocessResult);
  }

  /**
   * Loads external configuration values for dynamic resolution.
   *
   * @param projectRoot the project root directory
   * @param config the configuration
   * @return map of configuration key-value pairs
   */
  public Map<String, String> loadExternalConfigValues(final Path projectRoot, final Config config) {
    return sourcePreprocessingService.loadExternalConfigValues(projectRoot, config);
  }

  /**
   * Resolves source directories for the project.
   *
   * @param projectRoot the project root directory
   * @param config the configuration
   * @return the resolved source directories
   */
  public SourcePathResolver.SourceDirectories resolveSourceDirectories(
      final Path projectRoot, final Config config) {
    return sourcePreprocessingService.resolveSourceDirectories(projectRoot, config);
  }
}
