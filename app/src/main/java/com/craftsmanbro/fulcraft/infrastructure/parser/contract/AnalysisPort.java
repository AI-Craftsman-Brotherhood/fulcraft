package com.craftsmanbro.fulcraft.infrastructure.parser.contract;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Infrastructure-level parser contract.
 *
 * <p>Parser implementations analyze a project root into infrastructure analysis models. Adapters
 * convert these results into plugin contracts at layer boundaries.
 */
public interface AnalysisPort {

  /**
   * Analyzes the given project root with this parser engine.
   *
   * @param projectRoot the project root to analyze
   * @param config the active application configuration
   * @return the infrastructure analysis result produced by this engine
   * @throws IOException if reading sources or parser metadata fails
   */
  AnalysisResult analyze(Path projectRoot, Config config) throws IOException;

  /**
   * Convenience overload for callers that already provide configuration first.
   *
   * @param config the active application configuration
   * @param projectRoot the project root to analyze
   * @return the infrastructure analysis result produced by this engine
   * @throws IOException if reading sources or parser metadata fails
   */
  default AnalysisResult analyze(final Config config, final Path projectRoot) throws IOException {
    return analyze(projectRoot, config);
  }

  /**
   * Returns the parser engine name used for diagnostics and selection.
   *
   * @return stable engine identifier such as {@code spoon} or {@code javaparser}
   */
  String getEngineName();

  /**
   * Returns whether this parser engine can analyze the given project root.
   *
   * @param projectRoot the project root to inspect
   * @return {@code true} when this engine supports the project layout and requirements
   */
  boolean supports(Path projectRoot);
}
