package com.craftsmanbro.fulcraft.ui.cli.wiring;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.InvalidConfigurationException;
import com.craftsmanbro.fulcraft.config.plugin.PluginConfigLoader;
import com.craftsmanbro.fulcraft.kernel.pipeline.Pipeline;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.PhaseInterceptorLoader;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPluginException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ArtifactStore;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginConfig;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ServiceRegistry;
import com.craftsmanbro.fulcraft.kernel.plugin.runtime.PluginRegistry;
import com.craftsmanbro.fulcraft.kernel.plugin.runtime.PluginRegistryLoader;
import com.craftsmanbro.fulcraft.kernel.workflow.WorkflowConfigurationException;
import com.craftsmanbro.fulcraft.kernel.workflow.WorkflowLoader;
import com.craftsmanbro.fulcraft.kernel.workflow.WorkflowPlanResolver;
import com.craftsmanbro.fulcraft.kernel.workflow.model.ResolvedWorkflowNode;
import com.craftsmanbro.fulcraft.kernel.workflow.model.ResolvedWorkflowPlan;
import com.craftsmanbro.fulcraft.kernel.workflow.model.WorkflowDefinition;
import com.craftsmanbro.fulcraft.kernel.workflow.model.WorkflowFailurePolicy;
import com.craftsmanbro.fulcraft.plugins.analysis.AnalyzePlugin;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.stage.AnalyzeStage;
import com.craftsmanbro.fulcraft.plugins.document.DocumentPlugin;
import com.craftsmanbro.fulcraft.plugins.document.flow.DocumentFlow;
import com.craftsmanbro.fulcraft.plugins.exploration.ExplorePlugin;
import com.craftsmanbro.fulcraft.plugins.reporting.ReportPlugin;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * 設定済みの {@link Pipeline} インスタンスを作成するファクトリクラス。
 *
 * <p>このファクトリは、必要な依存関係が注入されたパイプラインを作成するための 配線（Wiring）ロジックをカプセル化します。 テスト用と本番用で異なる構成をサポートします。
 */
public class PipelineFactory {

  private final Config config;

  private final AnalysisPort analysisPort;

  private final ServiceFactory serviceFactory;

  private final Function<RunContext, ArtifactStore> artifactStoreFactory;

  private final String pluginClasspath;

  // Optional override for testing
  private PluginRegistry pluginRegistry;

  /**
   * 新しいパイプラインファクトリを作成します。
   *
   * @param config アプリケーション設定
   * @param analysisPort 使用する解析ポート
   * @param serviceFactory コンポーネント作成用のサービスファクトリ
   */
  public PipelineFactory(
      final Config config, final AnalysisPort analysisPort, final ServiceFactory serviceFactory) {
    this(config, analysisPort, serviceFactory, defaultArtifactStoreFactory(), null);
  }

  /**
   * 新しいパイプラインファクトリを作成します。
   *
   * @param config アプリケーション設定
   * @param analysisPort 使用する解析ポート
   * @param serviceFactory コンポーネント作成用のサービスファクトリ
   * @param artifactStoreFactory ArtifactStore 生成関数
   */
  public PipelineFactory(
      final Config config,
      final AnalysisPort analysisPort,
      final ServiceFactory serviceFactory,
      final Function<RunContext, ArtifactStore> artifactStoreFactory) {
    this(config, analysisPort, serviceFactory, artifactStoreFactory, null);
  }

  PipelineFactory(
      final Config config,
      final AnalysisPort analysisPort,
      final ServiceFactory serviceFactory,
      final Function<RunContext, ArtifactStore> artifactStoreFactory,
      final String pluginClasspath) {
    this.config = Objects.requireNonNull(config, "Config cannot be null");
    this.analysisPort = Objects.requireNonNull(analysisPort, "AnalysisPort cannot be null");
    this.serviceFactory = Objects.requireNonNull(serviceFactory, "ServiceFactory cannot be null");
    this.artifactStoreFactory =
        Objects.requireNonNull(artifactStoreFactory, "artifactStoreFactory cannot be null");
    this.pluginClasspath = pluginClasspath;
  }

  /**
   * PipelineFactory 用のビルダーを作成します。
   *
   * @param config アプリケーション設定
   * @return 新しい Builder インスタンス
   */
  public static Builder builder(final Config config) {
    return new Builder(config);
  }

  /**
   * Set a custom plugin registry (for testing).
   *
   * @param pluginRegistry The plugin registry to use
   * @return This factory for chaining
   */
  public PipelineFactory withPluginRegistry(final PluginRegistry pluginRegistry) {
    this.pluginRegistry = pluginRegistry;
    return this;
  }

  /**
   * 完全に構成されたパイプラインを作成します。
   *
   * <p>ステージ登録は workflow 定義から行い、{@code pipeline.stages} はノードフィルタとして扱います。
   *
   * @return workflow 駆動でステージ登録された新しい Pipeline インスタンス
   */
  public Pipeline create() {
    final Pipeline pipeline = new Pipeline(new PhaseInterceptorLoader(), config);
    final ServiceRegistry serviceRegistry = buildServiceRegistry();
    final PluginRegistry registry = resolvePluginRegistry();
    final WorkflowDefinition workflow = loadWorkflow();
    final ResolvedWorkflowPlan plan = resolveWorkflowPlan(workflow, registry);
    final Set<String> enabledPhases = resolveEnabledPhases();
    final Set<String> includedNodeIds = resolveIncludedNodeIds(plan, enabledPhases);
    final Map<String, PluginConfig> pluginConfigCache = new HashMap<>();
    for (final ResolvedWorkflowNode node : plan.nodes()) {
      if (!includedNodeIds.contains(node.nodeId())) {
        continue;
      }
      final List<String> dependencies =
          node.dependencies().stream().filter(includedNodeIds::contains).toList();
      pipeline.registerStage(
          node.nodeId(),
          new WorkflowNodeStage(node, artifactStoreFactory, serviceRegistry, pluginConfigCache),
          dependencies);
    }
    return pipeline;
  }

  /**
   * Create a lightweight pipeline for analysis only.
   *
   * @return A Pipeline with only the ANALYZE stage
   */
  public Pipeline createAnalysisOnlyPipeline() {
    final Pipeline pipeline = new Pipeline(new PhaseInterceptorLoader(), config);
    final Stage analyze = new AnalyzeStage(analysisPort);
    pipeline.registerStage(PipelineNodeIds.ANALYZE, analyze, List.of());
    return pipeline;
  }

  /** PipelineFactory インスタンスを作成するためのビルダー。 */
  public static class Builder {

    private final Config config;

    private AnalysisPort analysisPort;

    private ServiceFactory serviceFactory;

    private PluginRegistry pluginRegistry;

    private Function<RunContext, ArtifactStore> artifactStoreFactory;

    private String pluginClasspath;

    Builder(final Config config) {
      this.config = Objects.requireNonNull(config, "Config cannot be null");
    }

    public Builder analysisPort(final AnalysisPort port) {
      this.analysisPort = port;
      return this;
    }

    public Builder serviceFactory(final ServiceFactory serviceFactory) {
      this.serviceFactory = serviceFactory;
      return this;
    }

    public Builder pluginRegistry(final PluginRegistry pluginRegistry) {
      this.pluginRegistry = pluginRegistry;
      return this;
    }

    public Builder artifactStoreFactory(
        final Function<RunContext, ArtifactStore> artifactStoreFactory) {
      this.artifactStoreFactory = artifactStoreFactory;
      return this;
    }

    public Builder pluginClasspath(final String pluginClasspath) {
      this.pluginClasspath = pluginClasspath;
      return this;
    }

    public PipelineFactory build() {
      Objects.requireNonNull(analysisPort, "AnalysisPort must be set");
      Objects.requireNonNull(serviceFactory, "ServiceFactory must be set");
      final Function<RunContext, ArtifactStore> resolvedFactory =
          artifactStoreFactory != null ? artifactStoreFactory : defaultArtifactStoreFactory();
      final PipelineFactory factory =
          new PipelineFactory(
              config, analysisPort, serviceFactory, resolvedFactory, pluginClasspath);
      if (pluginRegistry != null) {
        factory.withPluginRegistry(pluginRegistry);
      }
      return factory;
    }
  }

  private static Function<RunContext, ArtifactStore> defaultArtifactStoreFactory() {
    return context ->
        ArtifactStoreAdapters.fromRunRoot(
            Objects.requireNonNull(context, "context must not be null").getRunDirectory());
  }

  private ServiceRegistry buildServiceRegistry() {
    final ServiceRegistry registry = new ServiceRegistry();
    registry.register(AnalysisPort.class, analysisPort);
    registry.register(
        DocumentFlow.class, new DocumentFlow(serviceFactory::createDecoratedLlmClient));
    return registry;
  }

  private WorkflowDefinition loadWorkflow() {
    final Path projectRoot = resolveWorkflowProjectRoot();
    return new WorkflowLoader()
        .load(config, projectRoot)
        .orElseThrow(() -> new IllegalStateException("Workflow definition is not available."));
  }

  private ResolvedWorkflowPlan resolveWorkflowPlan(
      final WorkflowDefinition workflow, final PluginRegistry registry) {
    try {
      return new WorkflowPlanResolver().resolve(workflow, registry);
    } catch (WorkflowConfigurationException e) {
      throw new InvalidConfigurationException("Workflow configuration error: " + e.getMessage(), e);
    }
  }

  private Path resolveWorkflowProjectRoot() {
    final Config.ProjectConfig project = config.getProject();
    if (project != null && project.getRoot() != null && !project.getRoot().isBlank()) {
      return Path.of(project.getRoot()).toAbsolutePath().normalize();
    }
    return Path.of(".").toAbsolutePath().normalize();
  }

  private Set<String> resolveEnabledPhases() {
    final LinkedHashSet<String> enabled = new LinkedHashSet<>();
    for (final String stageName : config.getPipeline().getStages()) {
      final String mapped = mapStageNameToPhase(stageName);
      if (mapped != null) {
        enabled.add(mapped);
      }
    }
    return enabled;
  }

  private static String mapStageNameToPhase(final String stageName) {
    final String normalizedStageName = PipelineNodeIds.normalizeNullable(stageName);
    if (normalizedStageName == null) {
      return null;
    }
    return switch (normalizedStageName) {
      case PipelineNodeIds.ANALYZE -> PipelineNodeIds.ANALYZE;
      case PipelineNodeIds.GENERATE, "select", "brittle_check" -> PipelineNodeIds.GENERATE;
      case PipelineNodeIds.REPORT -> PipelineNodeIds.REPORT;
      case PipelineNodeIds.DOCUMENT -> PipelineNodeIds.DOCUMENT;
      case PipelineNodeIds.EXPLORE -> PipelineNodeIds.EXPLORE;
      default -> null;
    };
  }

  private Set<String> resolveIncludedNodeIds(
      final ResolvedWorkflowPlan plan, final Set<String> enabledPhases) {
    if (enabledPhases == null || enabledPhases.isEmpty()) {
      return plan.orderedNodeIds().stream().collect(java.util.stream.Collectors.toSet());
    }
    final Map<String, ResolvedWorkflowNode> nodesById = new HashMap<>();
    for (final ResolvedWorkflowNode node : plan.nodes()) {
      nodesById.put(node.nodeId(), node);
    }
    final LinkedHashSet<String> included = new LinkedHashSet<>();
    for (final ResolvedWorkflowNode node : plan.nodes()) {
      if (enabledPhases.contains(phaseForNode(node))) {
        collectDependenciesRecursively(node.nodeId(), nodesById, included);
      }
    }
    return included;
  }

  private void collectDependenciesRecursively(
      final String nodeId,
      final Map<String, ResolvedWorkflowNode> nodesById,
      final Set<String> includedNodeIds) {
    if (!includedNodeIds.add(nodeId)) {
      return;
    }
    final ResolvedWorkflowNode node = nodesById.get(nodeId);
    if (node == null) {
      return;
    }
    for (final String dependency : node.dependencies()) {
      collectDependenciesRecursively(dependency, nodesById, includedNodeIds);
    }
  }

  private static String phaseForNode(final ResolvedWorkflowNode node) {
    final String pluginId = node.pluginId();
    if (AnalyzePlugin.PLUGIN_ID.equals(pluginId) || PipelineNodeIds.ANALYZE.equals(node.nodeId())) {
      return PipelineNodeIds.ANALYZE;
    }
    if (ReportPlugin.PLUGIN_ID.equals(pluginId) || PipelineNodeIds.REPORT.equals(node.nodeId())) {
      return PipelineNodeIds.REPORT;
    }
    if (DocumentPlugin.PLUGIN_ID.equals(pluginId)
        || PipelineNodeIds.DOCUMENT.equals(node.nodeId())) {
      return PipelineNodeIds.DOCUMENT;
    }
    if (ExplorePlugin.PLUGIN_ID.equals(pluginId) || PipelineNodeIds.EXPLORE.equals(node.nodeId())) {
      return PipelineNodeIds.EXPLORE;
    }
    return PipelineNodeIds.GENERATE;
  }

  private PluginRegistry resolvePluginRegistry() {
    return pluginRegistry != null
        ? pluginRegistry
        : new PluginRegistryLoader(pluginClasspath).load();
  }

  private static final class WorkflowNodeStage implements Stage {

    private static final String SKIP_DOWNSTREAM_ROOTS_KEY = "workflow.skipDownstreamRoots";

    private final ResolvedWorkflowNode node;

    private final Function<RunContext, ArtifactStore> artifactStoreFactory;

    private final ServiceRegistry serviceRegistry;

    private final Map<String, PluginConfig> pluginConfigCache;

    private WorkflowNodeStage(
        final ResolvedWorkflowNode node,
        final Function<RunContext, ArtifactStore> artifactStoreFactory,
        final ServiceRegistry serviceRegistry,
        final Map<String, PluginConfig> pluginConfigCache) {
      this.node = Objects.requireNonNull(node, "node");
      this.artifactStoreFactory =
          Objects.requireNonNull(artifactStoreFactory, "artifactStoreFactory");
      this.serviceRegistry = Objects.requireNonNull(serviceRegistry, "serviceRegistry");
      this.pluginConfigCache = Objects.requireNonNull(pluginConfigCache, "pluginConfigCache");
    }

    @Override
    public String getNodeId() {
      return node.nodeId();
    }

    @Override
    public String getName() {
      return "Workflow[" + node.nodeId() + "]";
    }

    @Override
    public boolean shouldSkip(final RunContext context) {
      Objects.requireNonNull(context, "context");
      final Set<String> skipRoots = getSkipDownstreamRoots(context);
      final String blockedDependency =
          node.dependencies().stream().filter(skipRoots::contains).findFirst().orElse(null);
      if (blockedDependency == null) {
        return false;
      }
      final String message =
          "Skipping workflow node '"
              + node.nodeId()
              + "' because dependency '"
              + blockedDependency
              + "' failed with SKIP_DOWNSTREAM policy.";
      context.addWarning(message);
      skipRoots.add(node.nodeId());
      storeSkipDownstreamRoots(context, skipRoots);
      return true;
    }

    @Override
    public void execute(final RunContext context) throws StageException {
      Objects.requireNonNull(context, "context");
      final PluginContext pluginContext = createPluginContext(context);
      executeWithRetry(context, pluginContext);
    }

    private PluginContext createPluginContext(final RunContext context) throws StageException {
      final PluginConfigLoader configLoader = new PluginConfigLoader(context.getProjectRoot());
      final PluginConfig pluginConfig;
      try {
        pluginConfig = pluginConfigCache.computeIfAbsent(node.pluginId(), configLoader::load);
      } catch (InvalidConfigurationException e) {
        throw new StageException(
            node.nodeId(),
            "Workflow node '" + node.nodeId() + "' plugin config error: " + e.getMessage(),
            e);
      }
      final ArtifactStore baseStore = artifactStoreFactory.apply(context);
      final ArtifactStore nodeStore = new NodeScopedArtifactStore(baseStore, node.nodeId());
      return new PluginContext(
          context, nodeStore, pluginConfig, node.nodeId(), node.nodeConfig(), serviceRegistry);
    }

    private void executeWithRetry(final RunContext context, final PluginContext pluginContext)
        throws StageException {
      final int maxRetries = node.retryPolicy().getMax();
      final int backoffMs = node.retryPolicy().getBackoffMs();
      int attempt = 0;
      while (true) {
        try {
          final PluginResult result = executePlugin(pluginContext);
          ensureNodeSuccess(result);
          return;
        } catch (Exception e) {
          if (ExceptionUtils.indexOfThrowable(e, InterruptedException.class) != -1) {
            Thread.currentThread().interrupt();
          }
          if (attempt < maxRetries && !Thread.currentThread().isInterrupted()) {
            attempt++;
            waitForRetry(backoffMs);
            continue;
          }
          handleFailure(context, e);
          return;
        }
      }
    }

    private PluginResult executePlugin(final PluginContext pluginContext) throws Exception {
      final Integer timeoutSec = node.timeoutSec();
      if (timeoutSec == null || timeoutSec <= 0) {
        return node.plugin().execute(pluginContext);
      }
      final ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        final Future<PluginResult> future =
            executor.submit(() -> node.plugin().execute(pluginContext));
        return future.get(timeoutSec.longValue(), TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        executor.shutdownNow();
        throw new ActionPluginException("Plugin execution timed out after " + timeoutSec + "s", e);
      } catch (ExecutionException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof Exception exception) {
          throw exception;
        }
        throw new ActionPluginException("Plugin execution failed", e);
      } finally {
        executor.shutdownNow();
      }
    }

    private void ensureNodeSuccess(final PluginResult result) throws ActionPluginException {
      if (result != null && !result.isSuccess()) {
        final String message =
            result.getMessage() != null ? result.getMessage() : "Plugin returned failure";
        throw new ActionPluginException("Workflow node '" + node.nodeId() + "' failed: " + message);
      }
    }

    private void waitForRetry(final int backoffMs) throws StageException {
      if (backoffMs <= 0) {
        return;
      }
      try {
        Thread.sleep(backoffMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new StageException(
            node.nodeId(), "Workflow node '" + node.nodeId() + "' retry interrupted", e);
      }
    }

    private void handleFailure(final RunContext context, final Exception exception)
        throws StageException {
      final String detail =
          exception.getMessage() != null
              ? exception.getMessage()
              : exception.getClass().getSimpleName();
      final String baseMessage =
          "Workflow node '"
              + node.nodeId()
              + "' (plugin '"
              + node.pluginId()
              + "') failed: "
              + detail;
      if (ExceptionUtils.indexOfThrowable(exception, InterruptedException.class) != -1) {
        Thread.currentThread().interrupt();
        throw new StageException(node.nodeId(), baseMessage, exception);
      }
      final WorkflowFailurePolicy policy = node.failurePolicy();
      if (policy == WorkflowFailurePolicy.CONTINUE) {
        context.addWarning(baseMessage);
        return;
      }
      if (policy == WorkflowFailurePolicy.SKIP_DOWNSTREAM) {
        context.addWarning(baseMessage + " Downstream nodes will be skipped.");
        final Set<String> skipRoots = getSkipDownstreamRoots(context);
        skipRoots.add(node.nodeId());
        storeSkipDownstreamRoots(context, skipRoots);
        return;
      }
      throw new StageException(node.nodeId(), baseMessage, exception);
    }

    private Set<String> getSkipDownstreamRoots(final RunContext context) {
      final Set<String> existing =
          context.getMetadataSet(SKIP_DOWNSTREAM_ROOTS_KEY, String.class).orElse(Set.of());
      return new LinkedHashSet<>(existing);
    }

    private void storeSkipDownstreamRoots(final RunContext context, final Set<String> roots) {
      context.putMetadata(SKIP_DOWNSTREAM_ROOTS_KEY, Set.copyOf(roots));
    }
  }

  private static final class NodeScopedArtifactStore implements ArtifactStore {

    private final ArtifactStore delegate;

    private final String nodeId;

    private NodeScopedArtifactStore(final ArtifactStore delegate, final String nodeId) {
      this.delegate = Objects.requireNonNull(delegate, "delegate");
      this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
    }

    @Override
    public Path runRoot() {
      return delegate.runRoot();
    }

    @Override
    public Path actionsRoot() throws IOException {
      return delegate.actionsRoot();
    }

    @Override
    public Path actions(final String pluginId) throws IOException {
      return delegate.actions(pluginId, nodeId);
    }
  }
}
