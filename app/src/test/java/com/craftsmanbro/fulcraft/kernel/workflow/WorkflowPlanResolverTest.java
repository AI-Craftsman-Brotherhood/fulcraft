package com.craftsmanbro.fulcraft.kernel.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;
import com.craftsmanbro.fulcraft.kernel.plugin.runtime.PluginRegistry;
import com.craftsmanbro.fulcraft.kernel.workflow.model.ResolvedWorkflowPlan;
import com.craftsmanbro.fulcraft.kernel.workflow.model.WorkflowDefinition;
import com.craftsmanbro.fulcraft.kernel.workflow.model.WorkflowNodeDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowPlanResolverTest {

  @Test
  void resolve_buildsTopologicalOrderWithDependencies() {
    final WorkflowDefinition definition =
        workflowOf(
            node("analyze", "plugin.analyze", List.of(), Map.of()),
            node("select", "plugin.select", List.of("analyze"), Map.of()),
            node("generate", "plugin.generate", List.of("select"), Map.of("model", "gpt-4")),
            node("report", "plugin.report", List.of("generate"), Map.of()));
    final PluginRegistry registry =
        new PluginRegistry(
            List.of(
                new StubPlugin("plugin.analyze"),
                new StubPlugin("plugin.select"),
                new StubPlugin("plugin.generate"),
                new StubPlugin("plugin.report")));
    final WorkflowPlanResolver resolver = new WorkflowPlanResolver();

    final ResolvedWorkflowPlan plan = resolver.resolve(definition, registry);

    assertThat(plan.orderedNodeIds()).containsExactly("analyze", "select", "generate", "report");
    assertThat(plan.nodes().get(2).nodeConfig()).containsEntry("model", "gpt-4");
    assertThat(plan.nodes().get(3).dependencies()).containsExactly("generate");
  }

  @Test
  void resolve_ordersIndependentNodesDeterministicallyByNodeId() {
    final WorkflowDefinition definition =
        workflowOf(
            node("z-last", "plugin.z", List.of(), Map.of()),
            node("a-first", "plugin.a", List.of(), Map.of()),
            node("m-middle", "plugin.m", List.of(), Map.of()));
    final PluginRegistry registry =
        new PluginRegistry(
            List.of(
                new StubPlugin("plugin.z"),
                new StubPlugin("plugin.a"),
                new StubPlugin("plugin.m")));
    final WorkflowPlanResolver resolver = new WorkflowPlanResolver();

    final ResolvedWorkflowPlan plan = resolver.resolve(definition, registry);

    assertThat(plan.orderedNodeIds()).containsExactly("a-first", "m-middle", "z-last");
  }

  @Test
  void resolve_rejectsUnknownPlugin() {
    final WorkflowDefinition definition =
        workflowOf(node("analyze", "plugin.unknown", List.of(), Map.of()));
    final PluginRegistry registry = new PluginRegistry(List.of(new StubPlugin("plugin.analyze")));
    final WorkflowPlanResolver resolver = new WorkflowPlanResolver();

    assertThatThrownBy(() -> resolver.resolve(definition, registry))
        .isInstanceOf(WorkflowConfigurationException.class)
        .hasMessageContaining(
            MessageSource.getMessage(
                "kernel.workflow.resolver.error.unknown_plugin", "analyze", "plugin.unknown"));
  }

  @Test
  void resolve_acceptsPluginRegardlessOfKindValue() {
    final WorkflowDefinition definition =
        workflowOf(node("analyze", "plugin.analyze", List.of(), Map.of()));
    final PluginRegistry registry =
        new PluginRegistry(List.of(new StubPlugin("plugin.analyze", "analyze")));
    final WorkflowPlanResolver resolver = new WorkflowPlanResolver();

    final ResolvedWorkflowPlan plan = resolver.resolve(definition, registry);

    assertThat(plan.orderedNodeIds()).containsExactly("analyze");
  }

  @Test
  void resolve_rejectsDependencyOnMissingNode() {
    final WorkflowDefinition definition =
        workflowOf(node("generate", "plugin.generate", List.of("select"), Map.of()));
    final PluginRegistry registry = new PluginRegistry(List.of(new StubPlugin("plugin.generate")));
    final WorkflowPlanResolver resolver = new WorkflowPlanResolver();

    assertThatThrownBy(() -> resolver.resolve(definition, registry))
        .isInstanceOf(WorkflowConfigurationException.class)
        .hasMessageContaining(
            MessageSource.getMessage(
                "kernel.workflow.resolver.error.dependency_missing", "generate", "select"));
  }

  @Test
  void resolve_rejectsCycle() {
    final WorkflowDefinition definition =
        workflowOf(
            node("a", "plugin.a", List.of("b"), Map.of()),
            node("b", "plugin.b", List.of("a"), Map.of()));
    final PluginRegistry registry =
        new PluginRegistry(List.of(new StubPlugin("plugin.a"), new StubPlugin("plugin.b")));
    final WorkflowPlanResolver resolver = new WorkflowPlanResolver();

    assertThatThrownBy(() -> resolver.resolve(definition, registry))
        .isInstanceOf(WorkflowConfigurationException.class)
        .hasMessageContaining(
            MessageSource.getMessage("kernel.workflow.resolver.error.dependency_cycle")
                .split("\\{0\\}", 2)[0]);
  }

  @Test
  void resolve_rejectsDuplicateNodeId() {
    final WorkflowDefinition definition =
        workflowOf(
            node("same", "plugin.a", List.of(), Map.of()),
            node("same", "plugin.b", List.of(), Map.of()));
    final PluginRegistry registry =
        new PluginRegistry(List.of(new StubPlugin("plugin.a"), new StubPlugin("plugin.b")));
    final WorkflowPlanResolver resolver = new WorkflowPlanResolver();

    assertThatThrownBy(() -> resolver.resolve(definition, registry))
        .isInstanceOf(WorkflowConfigurationException.class)
        .hasMessageContaining(
            MessageSource.getMessage("kernel.workflow.resolver.error.duplicate_node_id", "same"));
  }

  private static WorkflowDefinition workflowOf(final WorkflowNodeDefinition... nodes) {
    final WorkflowDefinition definition = new WorkflowDefinition();
    final List<WorkflowNodeDefinition> values = new ArrayList<>();
    for (final WorkflowNodeDefinition node : nodes) {
      values.add(node);
    }
    definition.setNodes(values);
    return definition;
  }

  private static WorkflowNodeDefinition node(
      final String id,
      final String pluginId,
      final List<String> dependsOn,
      final Map<String, Object> with) {
    final WorkflowNodeDefinition node = new WorkflowNodeDefinition();
    node.setId(id);
    node.setPluginId(pluginId);
    node.setDependsOn(dependsOn);
    node.setWith(with);
    return node;
  }

  private static final class StubPlugin implements ActionPlugin {
    private final String id;
    private final String kind;

    private StubPlugin(final String id) {
      this(id, "workflow");
    }

    private StubPlugin(final String id, final String kind) {
      this.id = id;
      this.kind = kind;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String kind() {
      return kind;
    }

    @Override
    public PluginResult execute(final PluginContext context) {
      return PluginResult.success(id, "ok");
    }
  }
}
