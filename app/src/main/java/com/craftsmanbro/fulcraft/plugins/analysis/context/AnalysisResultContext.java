package com.craftsmanbro.fulcraft.plugins.analysis.context;

import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.util.Objects;
import java.util.Optional;

/** Stores and retrieves analysis results from RunContext metadata. */
public final class AnalysisResultContext {

  public static final String METADATA_KEY = "analysis.result";

  private static final String CONTEXT_PARAM = "context";

  private AnalysisResultContext() {}

  public static Optional<AnalysisResult> get(final RunContext context) {
    Objects.requireNonNull(context, CONTEXT_PARAM);
    return context.getMetadata(METADATA_KEY, AnalysisResult.class);
  }

  public static void set(final RunContext context, final AnalysisResult result) {
    Objects.requireNonNull(context, CONTEXT_PARAM);
    Objects.requireNonNull(
        result,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "result"));
    context.putMetadata(METADATA_KEY, result);
  }

  public static void clear(final RunContext context) {
    Objects.requireNonNull(context, CONTEXT_PARAM);
    context.removeMetadata(METADATA_KEY);
  }
}
