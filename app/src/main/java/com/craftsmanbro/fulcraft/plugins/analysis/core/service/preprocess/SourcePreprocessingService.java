package com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import com.craftsmanbro.fulcraft.plugins.analysis.config.AnalysisConfig;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.index.ProjectSymbolIndex;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.index.ProjectSymbolIndexBuilder;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ExternalConfigValueLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service responsible for source code preprocessing.
 *
 * <p>This service handles:
 *
 * <ul>
 *   <li>Source code preprocessing (delombok, APT)
 *   <li>Source path resolution
 *   <li>Project symbol index building
 *   <li>External configuration value loading
 * </ul>
 */
public class SourcePreprocessingService {

  private final SourcePreprocessor preprocessor;

  private final SourcePathResolver sourcePathResolver;

  public SourcePreprocessingService() {
    this.preprocessor = new SourcePreprocessor();
    this.sourcePathResolver = new SourcePathResolver();
  }

  /** Constructor for testing with mock dependencies. */
  SourcePreprocessingService(
      final SourcePreprocessor preprocessor, final SourcePathResolver sourcePathResolver) {
    this.preprocessor = Objects.requireNonNull(preprocessor);
    this.sourcePathResolver = Objects.requireNonNull(sourcePathResolver);
  }

  /**
   * Preprocesses source code based on configuration.
   *
   * @param projectRoot the project root directory
   * @param config the configuration
   * @return the preprocessing result
   */
  public SourcePreprocessor.Result preprocess(
      final Path projectRoot, final Config config, final Path outputDir) {
    Objects.requireNonNull(
        projectRoot,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "projectRoot must not be null"));
    Objects.requireNonNull(
        config,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "config must not be null"));
    final List<Path> originalSourceRoots = resolveMainSourceRoots(projectRoot, config);
    return preprocessor.preprocess(projectRoot, originalSourceRoots, config, outputDir);
  }

  /**
   * Checks if preprocessing failed in STRICT mode.
   *
   * @param result the preprocessing result
   * @return true if preprocessing failed in STRICT mode
   */
  public boolean isStrictModeFailure(final SourcePreprocessor.Result result) {
    return result.getStatus() == SourcePreprocessor.Status.FAILED;
  }

  /**
   * Gets the failure reason from preprocessing result.
   *
   * @param result the preprocessing result
   * @return the failure reason or null if not failed
   */
  public String getFailureReason(final SourcePreprocessor.Result result) {
    return result.getFailureReason();
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
    final SourcePathResolver.SourceDirectories sourceDirs =
        sourcePathResolver.resolve(projectRoot, config);
    final List<Path> roots = new ArrayList<>();
    if (preprocessResult != null && preprocessResult.shouldUsePreprocessed()) {
      roots.addAll(preprocessResult.getSourceRootsAfter());
    } else {
      sourceDirs.mainSource().ifPresent(roots::add);
    }
    sourceDirs.testSource().ifPresent(roots::add);
    return new ProjectSymbolIndexBuilder().build(roots);
  }

  /**
   * Loads external configuration values for dynamic resolution.
   *
   * @param projectRoot the project root directory
   * @param config the configuration
   * @return map of configuration key-value pairs
   */
  public Map<String, String> loadExternalConfigValues(final Path projectRoot, final Config config) {
    if (config == null) {
      return Map.of();
    }
    final AnalysisConfig analysisConfig = config.getAnalysis();
    if (analysisConfig == null
        || !Boolean.TRUE.equals(analysisConfig.getExternalConfigResolution())) {
      return Map.of();
    }
    final ExternalConfigValueLoader loader = new ExternalConfigValueLoader();
    final Map<String, String> values = new LinkedHashMap<>();
    values.putAll(loader.load(projectRoot.resolve("src/main/resources")));
    values.putAll(loader.load(projectRoot.resolve("resources")));
    return values;
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
    return sourcePathResolver.resolve(projectRoot, config);
  }

  private List<Path> resolveMainSourceRoots(final Path projectRoot, final Config config) {
    final List<Path> roots = new ArrayList<>();
    addConfiguredSourceRoots(roots, projectRoot, config.getAnalysis());
    if (roots.isEmpty()) {
      addResolvedMainSourceRoot(roots, projectRoot, config);
    }
    if (roots.isEmpty()) {
      roots.add(projectRoot.resolve("src/main/java"));
    }
    return roots;
  }

  private void addConfiguredSourceRoots(
      final List<Path> roots, final Path projectRoot, final AnalysisConfig analysisConfig) {
    if (analysisConfig == null) {
      return;
    }
    final List<String> configuredRoots = analysisConfig.getSourceRootPaths();
    if (configuredRoots == null) {
      return;
    }
    for (final String pathStr : configuredRoots) {
      if (pathStr == null || pathStr.isBlank()) {
        continue;
      }
      final Path candidate = projectRoot.resolve(pathStr);
      if (Files.isDirectory(candidate)) {
        roots.add(candidate);
      }
    }
  }

  private void addResolvedMainSourceRoot(
      final List<Path> roots, final Path projectRoot, final Config config) {
    try {
      final SourcePathResolver.SourceDirectories sourceDirs =
          sourcePathResolver.resolve(projectRoot, config);
      sourceDirs.mainSource().ifPresent(roots::add);
    } catch (final IllegalStateException e) {
      Logger.debug(
          MessageSource.getMessage(
              "analysis.preprocess.source_root_resolution_fallback_failed", e.getMessage()));
    }
  }
}
