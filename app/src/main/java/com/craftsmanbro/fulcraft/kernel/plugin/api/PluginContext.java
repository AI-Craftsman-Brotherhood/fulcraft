package com.craftsmanbro.fulcraft.kernel.plugin.api;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Execution context for plugins. */
public final class PluginContext {

  private final RunContext runContext;

  private final ArtifactStore artifactStore;

  private final PluginConfig pluginConfig;

  private final String nodeId;

  private final Map<String, Object> nodeConfig;

  private final ServiceRegistry serviceRegistry;

  public PluginContext(final RunContext runContext, final ArtifactStore artifactStore) {
    this(runContext, artifactStore, PluginConfig.empty(), null, Map.of(), new ServiceRegistry());
  }

  public PluginContext(
      final RunContext runContext,
      final ArtifactStore artifactStore,
      final PluginConfig pluginConfig) {
    this(runContext, artifactStore, pluginConfig, null, Map.of(), new ServiceRegistry());
  }

  public PluginContext(
      final RunContext runContext,
      final ArtifactStore artifactStore,
      final PluginConfig pluginConfig,
      final String nodeId,
      final Map<String, Object> nodeConfig) {
    this(runContext, artifactStore, pluginConfig, nodeId, nodeConfig, new ServiceRegistry());
  }

  public PluginContext(
      final RunContext runContext,
      final ArtifactStore artifactStore,
      final PluginConfig pluginConfig,
      final String nodeId,
      final Map<String, Object> nodeConfig,
      final ServiceRegistry serviceRegistry) {
    this.runContext =
        Objects.requireNonNull(
            runContext,
            MessageSource.getMessage("kernel.common.error.argument_null", "runContext"));
    this.artifactStore =
        Objects.requireNonNull(
            artifactStore,
            MessageSource.getMessage("kernel.common.error.argument_null", "artifactStore"));
    this.pluginConfig =
        Objects.requireNonNull(
            pluginConfig,
            MessageSource.getMessage("kernel.common.error.argument_null", "pluginConfig"));
    this.serviceRegistry =
        Objects.requireNonNull(
            serviceRegistry,
            MessageSource.getMessage("kernel.common.error.argument_null", "serviceRegistry"));
    this.nodeId = nodeId;
    if (nodeConfig == null || nodeConfig.isEmpty()) {
      this.nodeConfig = Map.of();
    } else {
      this.nodeConfig = Map.copyOf(new LinkedHashMap<>(nodeConfig));
    }
  }

  public RunContext getRunContext() {
    return runContext;
  }

  public ArtifactStore getArtifactStore() {
    return artifactStore;
  }

  public PluginConfig getPluginConfig() {
    return pluginConfig;
  }

  public Optional<String> getNodeId() {
    return Optional.ofNullable(nodeId);
  }

  public Map<String, Object> getNodeConfig() {
    return nodeConfig;
  }

  public ServiceRegistry getServiceRegistry() {
    return serviceRegistry;
  }
}
