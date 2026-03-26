package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.workflow.WorkflowLoader;
import com.craftsmanbro.fulcraft.kernel.workflow.model.WorkflowDefinition;
import com.craftsmanbro.fulcraft.kernel.workflow.model.WorkflowNodeDefinition;
import com.craftsmanbro.fulcraft.plugins.analysis.AnalyzePlugin;
import com.craftsmanbro.fulcraft.plugins.document.DocumentPlugin;
import com.craftsmanbro.fulcraft.plugins.exploration.ExplorePlugin;
import com.craftsmanbro.fulcraft.plugins.reporting.ReportPlugin;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import picocli.CommandLine.Command;

/**
 * 利用可能な workflow ノードを一覧表示するCLIコマンド。
 *
 * <p>使用例:
 *
 * <pre>
 *   ful steps
 * </pre>
 */
@Command(
    name = "steps",
    description = "${command.steps.description}",
    footer = "${command.steps.footer}",
    resourceBundle = "messages",
    mixinStandardHelpOptions = true)
@Category("other")
public class StepsCommand extends BaseCliCommand {

  private static final String NODE_FORMAT = "  %-20s plugin=%-24s depends_on=%s";

  @Override
  protected Integer doCall(final Config config, final Path projectRoot) {
    final Path workflowRoot = resolveWorkflowRoot(config, projectRoot);
    final WorkflowDefinition workflow =
        new WorkflowLoader()
            .load(config, workflowRoot)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        MessageSource.getMessage("steps.workflow_not_available")));

    final List<WorkflowNodeDefinition> nodesToPrint = resolveIncludedNodes(config, workflow);

    print(MessageSource.getMessage("steps.configured_nodes"));
    print("");
    for (final WorkflowNodeDefinition node : nodesToPrint) {
      final String dependsOn =
          node.getDependsOn().isEmpty() ? "-" : String.join(",", node.getDependsOn());
      final String pluginId =
          node.getPluginId() == null || node.getPluginId().isBlank() ? "-" : node.getPluginId();
      print(String.format(NODE_FORMAT, safeNodeId(node.getId()), pluginId, dependsOn));
    }
    print("");
    print(MessageSource.getMessage("steps.usage_examples"));
    return 0;
  }

  private Path resolveWorkflowRoot(final Config config, final Path projectRoot) {
    if (projectRoot != null) {
      return projectRoot;
    }
    final Config.ProjectConfig project = config != null ? config.getProject() : null;
    if (project != null && project.getRoot() != null && !project.getRoot().isBlank()) {
      return Path.of(project.getRoot()).toAbsolutePath().normalize();
    }
    return Path.of(".").toAbsolutePath().normalize();
  }

  private List<WorkflowNodeDefinition> resolveIncludedNodes(
      final Config config, final WorkflowDefinition workflow) {
    final List<WorkflowNodeDefinition> enabledNodes = new ArrayList<>();
    final Map<String, WorkflowNodeDefinition> nodesById = new HashMap<>();
    for (final WorkflowNodeDefinition node : workflow.getNodes()) {
      if (node == null || !node.isEnabled()) {
        continue;
      }
      enabledNodes.add(node);
      final String nodeId = safeNodeId(node.getId());
      if (!nodeId.isBlank()) {
        nodesById.put(nodeId, node);
      }
    }

    final Set<String> enabledStages = resolveEnabledStages(config);
    if (enabledStages.isEmpty()) {
      return List.copyOf(enabledNodes);
    }

    final LinkedHashSet<String> includedNodeIds = new LinkedHashSet<>();
    for (final WorkflowNodeDefinition node : enabledNodes) {
      final String stage = classifyStage(node);
      if (enabledStages.contains(stage)) {
        collectDependenciesRecursively(safeNodeId(node.getId()), nodesById, includedNodeIds);
      }
    }

    final List<WorkflowNodeDefinition> filtered = new ArrayList<>();
    for (final WorkflowNodeDefinition node : enabledNodes) {
      if (includedNodeIds.contains(safeNodeId(node.getId()))) {
        filtered.add(node);
      }
    }
    return List.copyOf(filtered);
  }

  private Set<String> resolveEnabledStages(final Config config) {
    if (config == null || config.getPipeline() == null) {
      return Set.of();
    }
    final LinkedHashSet<String> result = new LinkedHashSet<>();
    for (final String stage : config.getPipeline().getStages()) {
      if (stage == null || stage.isBlank()) {
        continue;
      }
      result.add(stage.trim().toLowerCase(Locale.ROOT));
    }
    return result;
  }

  private String classifyStage(final WorkflowNodeDefinition node) {
    final String nodeId = safeNodeId(node.getId());
    final String pluginId = Optional.ofNullable(node.getPluginId()).orElse("");
    if (AnalyzePlugin.PLUGIN_ID.equals(pluginId) || "analyze".equals(nodeId)) {
      return "analyze";
    }
    if (ReportPlugin.PLUGIN_ID.equals(pluginId) || "report".equals(nodeId)) {
      return "report";
    }
    if (DocumentPlugin.PLUGIN_ID.equals(pluginId) || "document".equals(nodeId)) {
      return "document";
    }
    if (ExplorePlugin.PLUGIN_ID.equals(pluginId) || "explore".equals(nodeId)) {
      return "explore";
    }
    return "generate";
  }

  private void collectDependenciesRecursively(
      final String nodeId,
      final Map<String, WorkflowNodeDefinition> nodesById,
      final Set<String> includedNodeIds) {
    if (nodeId == null || nodeId.isBlank() || !includedNodeIds.add(nodeId)) {
      return;
    }
    final WorkflowNodeDefinition node = nodesById.get(nodeId);
    if (node == null) {
      return;
    }
    for (final String dependency : node.getDependsOn()) {
      collectDependenciesRecursively(safeNodeId(dependency), nodesById, includedNodeIds);
    }
  }

  private String safeNodeId(final String nodeId) {
    if (nodeId == null) {
      return "";
    }
    return nodeId.trim();
  }

  @Override
  protected boolean shouldValidateProjectRoot() {
    return false;
  }

  /**
   * Prints a message to stdout.
   *
   * <p>Extracted for testability so that tests can override this method to capture output.
   *
   * @param message the message to print
   */
  protected void print(final String message) {
    if (message == null) {
      UiLogger.stdout("");
    } else {
      UiLogger.stdout(message);
    }
  }
}
