package com.craftsmanbro.fulcraft.ui.cli.wiring;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultMerger;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultVerifier;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.contract.BuildToolPort;
import java.nio.file.Path;

/** Factory for wiring application services. */
public interface ServiceFactory {
  /**
   * Creates an analysis port based on the specified type.
   *
   * @param engineType The type of the analysis engine (e.g., "javaparser", "spoon"). If null or
   *     unknown, a default implementation is returned.
   * @return An instance of {@link AnalysisPort}.
   */
  AnalysisPort createAnalysisPort(String engineType);

  /**
   * Creates a result merger for combining analysis results.
   *
   * @return An instance of {@link ResultMerger}.
   */
  ResultMerger createResultMerger();

  /**
   * Creates a result verifier for comparing analysis results.
   *
   * @return An instance of {@link ResultVerifier}.
   */
  ResultVerifier createResultVerifier();

  /**
   * Creates an LLM client based on configuration.
   *
   * @param config The application configuration.
   * @return An instance of {@link LlmClientPort}.
   */
  LlmClientPort createLlmClient(Config config);

  /**
   * Creates a decorated LLM client with usage tracking, quota enforcement, and audit logging.
   *
   * @param config The application configuration.
   * @param projectRoot The project root for usage/audit storage.
   * @return An instance of {@link LlmClientPort}.
   */
  LlmClientPort createDecoratedLlmClient(Config config, Path projectRoot);

  /**
   * Creates a build tool interface (runner) appropriate for the project.
   *
   * @return An instance of {@link BuildToolPort}.
   */
  BuildToolPort createBuildTool();
}
