package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Helper for post-processing analysis results across parser engines. */
public final class CommonPostProcessor {

  private static final int MAX_UNRESOLVED_WARNINGS = 50;

  private static final int MIN_DUPLICATE_GROUP_SIZE = 2;

  private CommonPostProcessor() {
    // Utility class
  }

  /**
   * Finalizes post-processing including cycle detection, dead code marking, and duplicate
   * detection.
   */
  public static void finalizePostProcessing(
      final AnalysisResult result, final AnalysisContext context) {
    Objects.requireNonNull(
        result,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "result must not be null"));
    Objects.requireNonNull(
        context,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "context must not be null"));
    final String source = context.getCallGraphSource();
    final boolean resolved = context.isCallGraphResolved();
    final AtomicInteger unresolvedWarnings = new AtomicInteger(0);
    for (final Map.Entry<String, MethodInfo> entry : context.getMethodInfos().entrySet()) {
      final Set<String> calls = context.getCallGraph().get(entry.getKey());
      final var statuses = context.getCallStatuses(entry.getKey());
      final var argumentLiterals = context.getCallArgumentLiterals(entry.getKey());
      final List<com.craftsmanbro.fulcraft.infrastructure.parser.model.CalledMethodRef> refs =
          calls == null || calls.isEmpty()
              ? List.of()
              : PostProcessingUtils.buildCalledMethodRefs(
                  new ArrayList<>(calls), source, statuses, resolved, argumentLiterals);
      entry.getValue().setCalledMethodRefs(refs);
      if (resolved) {
        PostProcessingUtils.logUnresolvedCalls(
            entry.getKey(), refs, unresolvedWarnings, MAX_UNRESOLVED_WARNINGS);
      }
    }
    GraphAnalyzer.markCycles(context);
    final var deadCodeDetector = new DeadCodeDetector();
    deadCodeDetector.markDeadCode(context);
    PostProcessingUtils.markDuplicates(context, MIN_DUPLICATE_GROUP_SIZE);
    deadCodeDetector.markDeadClasses(result);
  }
}
