package com.craftsmanbro.fulcraft.kernel.workflow.model;

import java.util.List;
import java.util.Objects;

/**
 * Topologically resolved workflow plan.
 *
 * <p>The stored node list represents execution order and is copied defensively on construction.
 */
public record ResolvedWorkflowPlan(List<ResolvedWorkflowNode> nodes) {

  public ResolvedWorkflowPlan {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
  }

  /**
   * Returns node ids in execution order.
   *
   * <p>The null filter is defensive because callers can instantiate the record directly.
   */
  public List<String> orderedNodeIds() {
    return nodes.stream().filter(Objects::nonNull).map(ResolvedWorkflowNode::nodeId).toList();
  }
}
