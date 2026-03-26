package com.craftsmanbro.fulcraft.kernel.workflow.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Workflow node resolved with a concrete plugin instance and execution metadata. */
public record ResolvedWorkflowNode(
    String nodeId,
    String pluginId,
    ActionPlugin plugin,
    List<String> dependencies,
    Map<String, Object> nodeConfig,
    WorkflowFailurePolicy failurePolicy,
    WorkflowRetryPolicy retryPolicy,
    Integer timeoutSec) {

  public ResolvedWorkflowNode {
    nodeId =
        Objects.requireNonNull(
            nodeId, MessageSource.getMessage("kernel.workflow.resolved_node.error.node_id_null"));
    pluginId =
        Objects.requireNonNull(
            pluginId,
            MessageSource.getMessage("kernel.workflow.resolved_node.error.plugin_id_null"));
    plugin =
        Objects.requireNonNull(
            plugin, MessageSource.getMessage("kernel.workflow.resolved_node.error.plugin_null"));

    // Normalize optional inputs to immutable defaults so downstream workflow execution can skip
    // null checks.
    List<String> resolvedDependencies =
        dependencies == null ? List.of() : List.copyOf(dependencies);
    Map<String, Object> resolvedNodeConfig = nodeConfig == null ? Map.of() : Map.copyOf(nodeConfig);
    WorkflowFailurePolicy resolvedFailurePolicy =
        failurePolicy == null ? WorkflowFailurePolicy.STOP : Objects.requireNonNull(failurePolicy);
    WorkflowRetryPolicy resolvedRetryPolicy =
        retryPolicy == null ? WorkflowRetryPolicy.none() : retryPolicy;

    dependencies = resolvedDependencies;
    nodeConfig = resolvedNodeConfig;
    failurePolicy = resolvedFailurePolicy;
    retryPolicy = resolvedRetryPolicy;
  }
}
