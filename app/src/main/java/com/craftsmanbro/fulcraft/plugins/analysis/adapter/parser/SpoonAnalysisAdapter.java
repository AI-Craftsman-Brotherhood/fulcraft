package com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon.SpoonAnalyzer;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Spoon-based implementation of AnalysisPort.
 *
 * <p>This adapter wraps infrastructure parser contract and converts it into feature models.
 */
public class SpoonAnalysisAdapter implements AnalysisPort {

  private static final String ENGINE_NAME = "spoon";

  private final com.craftsmanbro.fulcraft.infrastructure.parser.contract.AnalysisPort delegate;

  /** Creates a SpoonAnalysisAdapter with default configuration. */
  public SpoonAnalysisAdapter() {
    this.delegate = new SpoonAnalyzer();
  }

  /**
   * Creates a SpoonAnalysisAdapter with a pre-configured analyzer. Primarily for testing.
   *
   * @param delegate the infrastructure parser port to delegate to
   */
  SpoonAnalysisAdapter(
      final com.craftsmanbro.fulcraft.infrastructure.parser.contract.AnalysisPort delegate) {
    this.delegate =
        Objects.requireNonNull(
            delegate,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "analysis.common.error.argument_null", "delegate must not be null"));
  }

  @Override
  public AnalysisResult analyze(final Path projectRoot, final Config config) throws IOException {
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "projectRoot must not be null"));
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "config must not be null"));
    return AnalysisResultAdapter.toFeature(delegate.analyze(config, projectRoot));
  }

  @Override
  public String getEngineName() {
    return ENGINE_NAME;
  }

  @Override
  public boolean supports(final Path projectRoot) {
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "projectRoot must not be null"));
    if (!Files.isDirectory(projectRoot)) {
      return false;
    }
    // Check for standard Java project structure
    return Files.exists(projectRoot.resolve("src/main/java"))
        || Files.exists(projectRoot.resolve("app/src/main/java"))
        || Files.exists(projectRoot.resolve("src"))
        || containsJavaFiles(projectRoot);
  }

  private boolean containsJavaFiles(final Path directory) {
    if (!Files.isDirectory(directory)) {
      return false;
    }
    try (var stream = Files.walk(directory, 3)) {
      return stream.anyMatch(p -> p.toString().endsWith(".java"));
    } catch (IOException e) {
      return false;
    }
  }
}
