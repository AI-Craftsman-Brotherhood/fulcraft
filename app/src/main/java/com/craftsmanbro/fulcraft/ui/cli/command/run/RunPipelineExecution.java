package com.craftsmanbro.fulcraft.ui.cli.command.run;

import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineRunner;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Executes pipeline run and emits diagnostics via callback. */
public final class RunPipelineExecution {

  private final PipelineRunner runner;

  public RunPipelineExecution(final PipelineRunner runner) {
    this.runner = Objects.requireNonNull(runner, "runner is required");
  }

  public int execute(
      final RunContext context,
      final List<String> specificNodeIds,
      final String fromNodeId,
      final String toNodeId,
      final Consumer<RunContext> diagnosticsReporter) {
    Objects.requireNonNull(context, "context is required");
    final int exitCode =
        runner.runNodes(context, normalizeSpecificNodeIds(specificNodeIds), fromNodeId, toNodeId);
    if (diagnosticsReporter != null) {
      diagnosticsReporter.accept(context);
    }
    return exitCode;
  }

  public int executeNodes(
      final RunContext context,
      final List<String> nodeIds,
      final String fromNodeId,
      final String toNodeId,
      final Consumer<RunContext> diagnosticsReporter) {
    return execute(context, nodeIds, fromNodeId, toNodeId, diagnosticsReporter);
  }

  private List<String> normalizeSpecificNodeIds(final List<String> specificNodeIds) {
    if (specificNodeIds == null || specificNodeIds.isEmpty()) {
      return specificNodeIds;
    }
    final LinkedHashSet<String> remaining = new LinkedHashSet<>(specificNodeIds);
    final List<String> ordered = new ArrayList<>(specificNodeIds.size());
    for (final String nodeId : runner.getPipeline().getStageNodes().keySet()) {
      if (remaining.remove(nodeId)) {
        ordered.add(nodeId);
      }
    }
    ordered.addAll(remaining);
    return List.copyOf(ordered);
  }
}
