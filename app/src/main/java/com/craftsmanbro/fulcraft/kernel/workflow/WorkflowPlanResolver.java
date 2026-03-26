package com.craftsmanbro.fulcraft.kernel.workflow;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginKind;
import com.craftsmanbro.fulcraft.kernel.plugin.runtime.PluginRegistry;
import com.craftsmanbro.fulcraft.kernel.workflow.model.ResolvedWorkflowNode;
import com.craftsmanbro.fulcraft.kernel.workflow.model.ResolvedWorkflowPlan;
import com.craftsmanbro.fulcraft.kernel.workflow.model.WorkflowDefinition;
import com.craftsmanbro.fulcraft.kernel.workflow.model.WorkflowNodeDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.TreeSet;

/** Resolves workflow nodes into a deterministic execution plan. */
public class WorkflowPlanResolver {

  /**
   * Resolves a workflow definition into topological order.
   *
   * @param definition workflow definition
   * @param pluginRegistry registry of available plugins
   * @return resolved workflow plan
   */
  public ResolvedWorkflowPlan resolve(
      final WorkflowDefinition definition, final PluginRegistry pluginRegistry) {
    Objects.requireNonNull(definition, msg("kernel.workflow.resolver.error.definition_null"));
    Objects.requireNonNull(
        pluginRegistry, msg("kernel.workflow.resolver.error.plugin_registry_null"));
    final Map<String, WorkflowNodeDefinition> enabledNodesById = indexEnabledNodesById(definition);
    validateDependencies(enabledNodesById);
    final Map<String, ActionPlugin> pluginsByNodeId =
        resolvePlugins(enabledNodesById, pluginRegistry);
    final List<String> orderedNodeIds = sortTopologically(enabledNodesById);
    final List<ResolvedWorkflowNode> resolvedNodes = new ArrayList<>(orderedNodeIds.size());
    for (final String nodeId : orderedNodeIds) {
      final WorkflowNodeDefinition nodeDefinition = enabledNodesById.get(nodeId);
      resolvedNodes.add(
          new ResolvedWorkflowNode(
              nodeId,
              nodeDefinition.getPluginId(),
              pluginsByNodeId.get(nodeId),
              nodeDefinition.getDependsOn(),
              nodeDefinition.getWith(),
              nodeDefinition.getOnFailurePolicy(),
              nodeDefinition.getRetry(),
              nodeDefinition.getTimeoutSec()));
    }
    return new ResolvedWorkflowPlan(resolvedNodes);
  }

  private Map<String, WorkflowNodeDefinition> indexEnabledNodesById(
      final WorkflowDefinition definition) {
    final Map<String, WorkflowNodeDefinition> nodesById = new LinkedHashMap<>();
    for (final WorkflowNodeDefinition nodeDefinition : definition.getNodes()) {
      if (nodeDefinition == null || !nodeDefinition.isEnabled()) {
        continue;
      }
      validateNodeShape(nodeDefinition);
      final String nodeId = nodeDefinition.getId();
      if (nodesById.putIfAbsent(nodeId, nodeDefinition) != null) {
        throw new WorkflowConfigurationException(
            msg("kernel.workflow.resolver.error.duplicate_node_id", nodeId));
      }
    }
    if (nodesById.isEmpty()) {
      throw new WorkflowConfigurationException(
          msg("kernel.workflow.resolver.error.no_enabled_nodes"));
    }
    return nodesById;
  }

  private void validateNodeShape(final WorkflowNodeDefinition node) {
    final String nodeId = node.getId();
    if (nodeId == null || nodeId.isBlank()) {
      throw new WorkflowConfigurationException(msg("kernel.workflow.resolver.error.node_id_blank"));
    }
    final String pluginId = node.getPluginId();
    if (pluginId == null || pluginId.isBlank()) {
      throw new WorkflowConfigurationException(
          msg("kernel.workflow.resolver.error.plugin_missing_in_node", nodeId));
    }
  }

  private void validateDependencies(final Map<String, WorkflowNodeDefinition> nodesById) {
    for (final WorkflowNodeDefinition nodeDefinition : nodesById.values()) {
      for (final String dependencyId : nodeDefinition.getDependsOn()) {
        if (!nodesById.containsKey(dependencyId)) {
          throw new WorkflowConfigurationException(
              msg(
                  "kernel.workflow.resolver.error.dependency_missing",
                  nodeDefinition.getId(),
                  dependencyId));
        }
      }
    }
  }

  private Map<String, ActionPlugin> resolvePlugins(
      final Map<String, WorkflowNodeDefinition> nodesById, final PluginRegistry pluginRegistry) {
    final Map<String, ActionPlugin> pluginsByNodeId = new HashMap<>();
    for (final WorkflowNodeDefinition nodeDefinition : nodesById.values()) {
      final ActionPlugin plugin =
          pluginRegistry
              .findById(nodeDefinition.getPluginId())
              .orElseThrow(
                  () ->
                      new WorkflowConfigurationException(
                          msg(
                              "kernel.workflow.resolver.error.unknown_plugin",
                              nodeDefinition.getId(),
                              nodeDefinition.getPluginId())));
      validatePluginKind(nodeDefinition, plugin);
      pluginsByNodeId.put(nodeDefinition.getId(), plugin);
    }
    return pluginsByNodeId;
  }

  private void validatePluginKind(
      final WorkflowNodeDefinition nodeDefinition, final ActionPlugin plugin) {
    try {
      PluginKind.normalizeRequired(plugin.kind(), "plugin.kind");
    } catch (RuntimeException e) {
      throw new WorkflowConfigurationException(
          msg(
              "kernel.workflow.resolver.error.plugin_kind_unresolved",
              nodeDefinition.getId(),
              nodeDefinition.getPluginId()),
          e);
    }
  }

  private List<String> sortTopologically(final Map<String, WorkflowNodeDefinition> nodesById) {
    final Map<String, Integer> remainingDependenciesByNodeId = new HashMap<>();
    final Map<String, List<String>> dependentNodeIdsByDependencyId = new HashMap<>();
    for (final String nodeId : nodesById.keySet()) {
      remainingDependenciesByNodeId.put(nodeId, 0);
      dependentNodeIdsByDependencyId.put(nodeId, new ArrayList<>());
    }
    for (final WorkflowNodeDefinition nodeDefinition : nodesById.values()) {
      for (final String dependencyId : nodeDefinition.getDependsOn()) {
        dependentNodeIdsByDependencyId.get(dependencyId).add(nodeDefinition.getId());
        remainingDependenciesByNodeId.put(
            nodeDefinition.getId(), remainingDependenciesByNodeId.get(nodeDefinition.getId()) + 1);
      }
    }
    // Use natural-order tie breaking so plan order stays deterministic when multiple nodes are
    // ready.
    final PriorityQueue<String> readyNodeIds = new PriorityQueue<>();
    for (final Map.Entry<String, Integer> dependencyCountByNode :
        remainingDependenciesByNodeId.entrySet()) {
      if (dependencyCountByNode.getValue() == 0) {
        readyNodeIds.add(dependencyCountByNode.getKey());
      }
    }
    final List<String> orderedNodeIds = new ArrayList<>(nodesById.size());
    while (!readyNodeIds.isEmpty()) {
      final String nodeId = readyNodeIds.poll();
      orderedNodeIds.add(nodeId);
      for (final String dependentNodeId : dependentNodeIdsByDependencyId.get(nodeId)) {
        final int remainingDependencyCount = remainingDependenciesByNodeId.get(dependentNodeId) - 1;
        remainingDependenciesByNodeId.put(dependentNodeId, remainingDependencyCount);
        if (remainingDependencyCount == 0) {
          readyNodeIds.add(dependentNodeId);
        }
      }
    }
    if (orderedNodeIds.size() != nodesById.size()) {
      final TreeSet<String> unresolvedNodeIds = new TreeSet<>();
      for (final Map.Entry<String, Integer> dependencyCountByNode :
          remainingDependenciesByNodeId.entrySet()) {
        if (dependencyCountByNode.getValue() > 0) {
          unresolvedNodeIds.add(dependencyCountByNode.getKey());
        }
      }
      throw new WorkflowConfigurationException(
          msg(
              "kernel.workflow.resolver.error.dependency_cycle",
              String.join(", ", unresolvedNodeIds)));
    }
    return orderedNodeIds;
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
