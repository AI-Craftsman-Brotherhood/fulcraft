package com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/** Converts parser result models from infrastructure contract to feature contract. */
final class AnalysisResultAdapter {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private AnalysisResultAdapter() {
    // Utility class
  }

  static AnalysisResult toFeature(
      final com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult
          infrastructureResult) {
    Objects.requireNonNull(
        infrastructureResult,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "infrastructureResult must not be null"));
    final AnalysisResult mapped =
        OBJECT_MAPPER.convertValue(infrastructureResult, AnalysisResult.class);
    mapped.setClasses(Collections.unmodifiableList(new ArrayList<>(mapped.getClasses())));
    mapped.setAnalysisErrors(
        Collections.unmodifiableList(new ArrayList<>(mapped.getAnalysisErrors())));
    return mapped;
  }
}
