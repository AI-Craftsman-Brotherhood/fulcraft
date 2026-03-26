package com.craftsmanbro.fulcraft.plugins.analysis.contract;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Contract for code analysis engines.
 *
 * <p>Implementations provide static analysis for a project root and can be selected by engine
 * support checks.
 */
public interface AnalysisPort {

  /**
   * Analyzes source code in the given project.
   *
   * @param projectRoot the root directory of the project
   * @param config the application configuration
   * @return the analysis result containing class and method information
   * @throws IOException if analysis fails
   */
  AnalysisResult analyze(Path projectRoot, Config config) throws IOException;

  /**
   * Convenience overload for callers that provide configuration first.
   *
   * @param config the application configuration
   * @param projectRoot the root directory of the project
   * @return the analysis result containing class and method information
   * @throws IOException if analysis fails
   */
  default AnalysisResult analyze(final Config config, final Path projectRoot) throws IOException {
    return analyze(projectRoot, config);
  }

  /**
   * Returns the analysis engine name.
   *
   * @return stable engine identifier such as {@code jdt}, {@code spoon}, or {@code javaparser}
   */
  String getEngineName();

  /**
   * Returns whether this engine supports the given project.
   *
   * @param projectRoot the root directory of the project
   * @return {@code true} if this engine can analyze the project; otherwise {@code false}
   */
  boolean supports(Path projectRoot);
}
