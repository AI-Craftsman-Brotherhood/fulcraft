package com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultMerger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Composite implementation of AnalysisPort.
 *
 * <p>This adapter runs multiple analysis engines and merges their results.
 */
public class CompositeAnalysisAdapter implements AnalysisPort {

  private static final String ENGINE_NAME = "composite";

  private final List<AnalysisPort> analyzers;

  private final ResultMerger merger;

  /**
   * Creates a CompositeAnalysisAdapter with the specified analyzers and merger.
   *
   * @param analyzers the analyzers to use
   * @param merger the result merger
   */
  public CompositeAnalysisAdapter(final List<AnalysisPort> analyzers, final ResultMerger merger) {
    if (analyzers == null || analyzers.isEmpty()) {
      throw new IllegalArgumentException(
          MessageSource.getMessage("analysis.composite_adapter.error.at_least_one_analyzer"));
    }
    if (analyzers.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(
          MessageSource.getMessage("analysis.composite_adapter.error.null_entries"));
    }
    this.analyzers = List.copyOf(analyzers);
    this.merger =
        Objects.requireNonNull(
            merger,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "analysis.common.error.argument_null", "merger must not be null"));
  }

  /**
   * Creates a default CompositeAnalysisAdapter with JavaParser and Spoon analyzers.
   *
   * @param merger the result merger
   * @return a new CompositeAnalysisAdapter
   */
  public static CompositeAnalysisAdapter createDefault(final ResultMerger merger) {
    return new CompositeAnalysisAdapter(
        List.of(new JavaParserAnalysisAdapter(), new SpoonAnalysisAdapter()), merger);
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
    final List<AnalysisPort> supportedAnalyzers =
        analyzers.stream().filter(analyzer -> analyzer.supports(projectRoot)).toList();
    if (supportedAnalyzers.isEmpty()) {
      throw new IllegalStateException(
          MessageSource.getMessage(
              "analysis.composite_adapter.error.no_compatible_analyzers", projectRoot));
    }
    final var iterator = supportedAnalyzers.iterator();
    // All AnalysisPort implementations now use (Path, Config) parameter order.
    AnalysisResult result = iterator.next().analyze(projectRoot, config);
    if (!iterator.hasNext()) {
      // Ensure derived metrics are computed even when a single analyzer runs.
      return merger.merge(result, null);
    }
    while (iterator.hasNext()) {
      final AnalysisResult nextResult = iterator.next().analyze(projectRoot, config);
      result = merger.merge(result, nextResult);
    }
    return result;
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
    // Composite supports if any of the underlying analyzers support
    return analyzers.stream().anyMatch(a -> a.supports(projectRoot));
  }
}
