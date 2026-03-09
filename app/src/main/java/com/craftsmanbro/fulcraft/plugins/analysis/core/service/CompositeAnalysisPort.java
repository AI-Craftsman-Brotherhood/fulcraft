package com.craftsmanbro.fulcraft.plugins.analysis.core.service;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultMerger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Composite analysis port that executes multiple analyzers and merges their results. */
public class CompositeAnalysisPort implements AnalysisPort {

  private static final String ENGINE_NAME = "composite";

  private final List<AnalysisPort> analyzers;

  private final ResultMerger merger;

  public CompositeAnalysisPort(final List<AnalysisPort> analyzers, final ResultMerger merger) {
    if (analyzers == null || analyzers.isEmpty()) {
      throw new IllegalArgumentException(
          MessageSource.getMessage("analysis.composite_port.error.at_least_one_analyzer"));
    }
    if (analyzers.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(
          MessageSource.getMessage("analysis.composite_port.error.null_elements"));
    }
    this.analyzers = List.copyOf(analyzers);
    this.merger =
        Objects.requireNonNull(
            merger,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "analysis.common.error.argument_null", "merger must not be null"));
  }

  @Override
  public AnalysisResult analyze(final Path projectRoot, final Config config) throws IOException {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "config must not be null"));
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "projectRoot must not be null"));
    Logger.debug(MessageSource.getMessage("analysis.composite_port.log.start", analyzers.size()));
    final var iterator = analyzers.iterator();
    var analyzer = iterator.next();
    var result = analyzer.analyze(projectRoot, config);
    if (result == null) {
      throw new IllegalStateException(
          MessageSource.getMessage(
              "analysis.composite_port.error.analyzer_returned_null",
              analyzer.getClass().getSimpleName()));
    }
    while (iterator.hasNext()) {
      analyzer = iterator.next();
      final AnalysisResult nextResult;
      try (var ignored = Logger.suppressProgressOutput()) {
        nextResult = analyzer.analyze(projectRoot, config);
      }
      if (nextResult == null) {
        throw new IllegalStateException(
            MessageSource.getMessage(
                "analysis.composite_port.error.analyzer_returned_null",
                analyzer.getClass().getSimpleName()));
      }
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
    if (projectRoot == null || !Files.isDirectory(projectRoot)) {
      return false;
    }
    // Composite supports if any child analyzer supports
    return analyzers.stream().anyMatch(a -> a.supports(projectRoot));
  }
}
