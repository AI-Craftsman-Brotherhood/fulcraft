package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Utility for graph-related analysis that does not depend on AST implementations. */
public final class GraphAnalyzer {

  private GraphAnalyzer() {
    // Utility class
  }

  /**
   * Detects cycles in the call graph and marks methods involved in cycles. Uses Tarjan's strongly
   * connected components algorithm.
   *
   * @param context the analysis context containing the call graph
   */
  public static void markCycles(final AnalysisContext context) {
    Objects.requireNonNull(
        context,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "context must not be null"));
    final var detector = new CycleDetector(context.getCallGraph());
    final var sccs = detector.detect(context.getMethodInfos().keySet());
    for (final var component : sccs) {
      if (isCycle(component, context)) {
        markComponentAsCycle(component, context);
      }
    }
  }

  private static boolean isCycle(final List<String> component, final AnalysisContext context) {
    if (component.size() >= 2) {
      return true;
    }
    if (component.isEmpty()) {
      return false;
    }
    final var node = component.getFirst();
    return context.getCallGraph().getOrDefault(node, Set.of()).contains(node);
  }

  private static void markComponentAsCycle(
      final List<String> component, final AnalysisContext context) {
    for (final var key : component) {
      final var methodInfo = context.getMethodInfos().get(key);
      if (methodInfo != null) {
        methodInfo.setPartOfCycle(true);
      }
    }
  }

  /** Helper class to encapsulate Tarjan's algorithm state. */
  private static class CycleDetector {

    private final Map<String, Set<String>> graph;

    private final Map<String, Integer> indexMap = new HashMap<>();

    private final Map<String, Integer> lowlinkMap = new HashMap<>();

    private final Deque<String> stack = new ArrayDeque<>();

    private final Set<String> onStack = new HashSet<>();

    private final List<List<String>> sccs = new ArrayList<>();

    private int index;

    CycleDetector(final Map<String, Set<String>> graph) {
      this.graph = graph;
    }

    List<List<String>> detect(final Set<String> nodes) {
      for (final var node : nodes) {
        if (!indexMap.containsKey(node)) {
          strongConnect(node);
        }
      }
      return sccs;
    }

    private void strongConnect(final String node) {
      indexMap.put(node, index);
      lowlinkMap.put(node, index);
      index++;
      stack.push(node);
      onStack.add(node);
      for (final var neighbor : graph.getOrDefault(node, Set.of())) {
        if (indexMap.containsKey(neighbor)) {
          if (onStack.contains(neighbor)) {
            lowlinkMap.put(node, Math.min(lowlinkMap.get(node), indexMap.get(neighbor)));
          }
        } else {
          strongConnect(neighbor);
          lowlinkMap.put(node, Math.min(lowlinkMap.get(node), lowlinkMap.get(neighbor)));
        }
      }
      if (lowlinkMap.get(node).equals(indexMap.get(node))) {
        final var component = new ArrayList<String>();
        String v;
        do {
          v = stack.pop();
          onStack.remove(v);
          component.add(v);
        } while (!v.equals(node));
        sccs.add(component);
      }
    }
  }
}
