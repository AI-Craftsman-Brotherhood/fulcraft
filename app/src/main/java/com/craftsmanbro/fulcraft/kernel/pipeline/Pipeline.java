package com.craftsmanbro.fulcraft.kernel.pipeline;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.PhaseInterceptor;
import com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.PhaseInterceptorLoader;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.logging.LoggerPort;
import com.craftsmanbro.fulcraft.logging.LoggerPortProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Consumer;

/**
 * パイプラインステージの実行をオーケストレーション（調整・管理）するクラス。
 *
 * <p>内部実行は nodeId（文字列）+ dependency DAG を基準に行います。
 */
public class Pipeline {

  private static final LoggerPort LOG = LoggerPortProvider.getLogger(Pipeline.class);

  private static final String KEY_ERROR_STAGE_NULL = "kernel.pipeline.error.stage_null";
  private static final String KEY_ERROR_STAGE_NODE_ID_NULL =
      "kernel.pipeline.error.stage_node_id_null";
  private static final String KEY_ERROR_CONTEXT_NULL = "kernel.pipeline.error.context_null";
  private static final String KEY_ERROR_LISTENER_NULL = "kernel.pipeline.error.listener_null";
  private static final String KEY_ERROR_LISTENERS_NULL = "kernel.pipeline.error.listeners_null";
  private static final String KEY_ERROR_NODE_IDS_NULL = "kernel.pipeline.error.node_ids_null";
  private static final String KEY_ERROR_EXCEPTION_NULL = "kernel.pipeline.error.exception_null";
  private static final String KEY_ERROR_THROWABLE_NULL = "kernel.pipeline.error.throwable_null";

  private final Object lock = new Object();
  private final Map<String, StageNode> stageNodes;
  private final List<PipelineListener> listeners;
  private final Map<String, Map<Hook, List<PhaseInterceptor>>> interceptors;

  /** Create a new pipeline without interceptors. */
  public Pipeline() {
    this.stageNodes = new LinkedHashMap<>();
    this.listeners = new ArrayList<>();
    this.interceptors = new HashMap<>();
  }

  /**
   * Create a new pipeline with interceptor support.
   *
   * @param interceptorLoader the loader to discover interceptors
   * @param config the configuration for filtering interceptors
   */
  public Pipeline(final PhaseInterceptorLoader interceptorLoader, final Config config) {
    this.stageNodes = new LinkedHashMap<>();
    this.listeners = new ArrayList<>();
    if (interceptorLoader != null && config != null) {
      this.interceptors = interceptorLoader.loadAll(config);
      final int count = countInterceptors();
      if (count > 0) {
        LOG.debug(msg("kernel.pipeline.log.interceptors_loaded"), count);
      }
    } else {
      this.interceptors = new HashMap<>();
    }
  }

  private int countInterceptors() {
    int count = 0;
    for (final var stepMap : interceptors.values()) {
      for (final var hookList : stepMap.values()) {
        count += hookList.size();
      }
    }
    return count;
  }

  /** Register a stage with default node id and no dependencies. */
  public Pipeline registerStage(final Stage stage) {
    Objects.requireNonNull(stage, msg(KEY_ERROR_STAGE_NULL));
    final String nodeId =
        Objects.requireNonNull(stage.getNodeId(), msg(KEY_ERROR_STAGE_NODE_ID_NULL));
    return registerStage(nodeId, stage, List.of());
  }

  /** Register a stage with custom node id and no dependencies. */
  public Pipeline registerStage(final String nodeId, final Stage stage) {
    return registerStage(nodeId, stage, List.of());
  }

  /** Register a stage node with explicit dependencies. */
  public Pipeline registerStage(
      final String nodeId, final Stage stage, final List<String> dependsOnNodeIds) {
    Objects.requireNonNull(stage, msg(KEY_ERROR_STAGE_NULL));
    Objects.requireNonNull(stage.getNodeId(), msg(KEY_ERROR_STAGE_NODE_ID_NULL));
    final String normalizedNodeId = normalizeNodeId(nodeId);
    final List<String> normalizedDeps = normalizeDependencies(dependsOnNodeIds, normalizedNodeId);
    synchronized (lock) {
      stageNodes.put(normalizedNodeId, new StageNode(normalizedNodeId, stage, normalizedDeps));
    }
    return this;
  }

  private String normalizeNodeId(final String nodeId) {
    if (nodeId == null || nodeId.isBlank()) {
      throw new IllegalArgumentException(msg("kernel.pipeline.error.node_id_blank"));
    }
    return PipelineNodeIds.normalizeRequired(nodeId, "nodeId");
  }

  private List<String> normalizeDependencies(
      final List<String> dependsOnNodeIds, final String selfNodeId) {
    if (dependsOnNodeIds == null || dependsOnNodeIds.isEmpty()) {
      return List.of();
    }
    final LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (final String dependency : dependsOnNodeIds) {
      if (dependency == null || dependency.isBlank()) {
        continue;
      }
      final String normalized = normalizeNodeId(dependency);
      if (normalized.equals(selfNodeId)) {
        throw new IllegalArgumentException(
            msg("kernel.pipeline.error.node_depends_on_self", selfNodeId));
      }
      unique.add(normalized);
    }
    return List.copyOf(unique);
  }

  /**
   * Add a pipeline listener.
   *
   * @param listener The listener to add
   * @return This pipeline for chaining
   */
  public Pipeline addListener(final PipelineListener listener) {
    Objects.requireNonNull(listener, msg(KEY_ERROR_LISTENER_NULL));
    synchronized (lock) {
      listeners.add(listener);
    }
    return this;
  }

  /**
   * パイプラインの全ノードを実行します。
   *
   * @param context 実行コンテキスト
   * @return 終了コード (0: 成功, 非0: 失敗)
   */
  public int run(final RunContext context) {
    return runNodes(context, null, null, null);
  }

  /**
   * 特定ノードを指定してパイプラインを実行します。
   *
   * @param context 実行コンテキスト
   * @param specificNodeIds 実行する特定ノードリスト (nullの場合は通常フロー)
   * @param fromNodeId 開始ノード (inclusive, nullの場合は最初から)
   * @param toNodeId 終了ノード (inclusive, nullの場合は最後まで)
   * @return 終了コード (0: 成功, 非0: 失敗)
   */
  public int run(
      final RunContext context,
      final List<String> specificNodeIds,
      final String fromNodeId,
      final String toNodeId) {
    return runNodes(context, specificNodeIds, fromNodeId, toNodeId);
  }

  /**
   * Run pipeline by node id selection (DAG-native API).
   *
   * @param context run context
   * @param specificNodeIds explicit node ids to run (null/empty = range or all)
   * @param fromNodeId start node id (inclusive) in DAG order
   * @param toNodeId end node id (inclusive) in DAG order
   * @return exit code (0 success / non-zero failure)
   */
  public int runNodes(
      final RunContext context,
      final List<String> specificNodeIds,
      final String fromNodeId,
      final String toNodeId) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    final List<PipelineListener> snapshotListeners;
    final Map<String, StageNode> snapshotNodes;
    synchronized (lock) {
      snapshotListeners = List.copyOf(listeners);
      snapshotNodes = Collections.unmodifiableMap(new LinkedHashMap<>(stageNodes));
    }
    final List<String> dagOrder = resolveDagOrder(snapshotNodes);
    final List<String> nodeIdsToRun =
        List.copyOf(determineNodeIdsToRun(specificNodeIds, fromNodeId, toNodeId, dagOrder));
    LOG.debug(msg("kernel.pipeline.log.starting_nodes"), nodeIdsToRun);
    notifyPipelineStarted(context, snapshotListeners, nodeIdsToRun);
    int exitCode = 0;
    for (final String nodeId : nodeIdsToRun) {
      final StageNode node = snapshotNodes.get(nodeId);
      final StepStatus status =
          processSingleNode(nodeId, node, context, snapshotListeners, specificNodeIds);
      if (status == StepStatus.FAILURE_CONTINUE) {
        exitCode = 1;
      } else if (status == StepStatus.FAILURE_STOP) {
        exitCode = 1;
        break;
      }
    }
    notifyPipelineCompleted(context, snapshotListeners, exitCode);
    return exitCode;
  }

  private StepStatus processSingleNode(
      final String nodeId,
      final StageNode node,
      final RunContext context,
      final List<PipelineListener> listeners,
      final List<String> specificNodeIds) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    if (node == null || node.stage() == null) {
      return handleMissingNode(nodeId, context, specificNodeIds);
    }
    final Stage stage = node.stage();
    if (stage.shouldSkip(context)) {
      LOG.info(msg("kernel.pipeline.log.skip_stage_node"), nodeId, stage.getName());
      notifyStageSkipped(context, listeners, stage);
      return StepStatus.SUCCESS;
    }
    return executeStage(stage, context, listeners);
  }

  private StepStatus handleMissingNode(
      final String nodeId, final RunContext context, final List<String> specificNodeIds) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    final String warning = msg("kernel.pipeline.error.missing_stage_for_node", nodeId);
    LOG.warn(warning);
    if (specificNodeIds != null && !specificNodeIds.isEmpty()) {
      context.addError(warning);
      if (context.isFailFast()) {
        return StepStatus.FAILURE_STOP;
      }
      return StepStatus.FAILURE_CONTINUE;
    }
    context.addWarning(warning);
    return StepStatus.SUCCESS;
  }

  private StepStatus executeStage(
      final Stage stage, final RunContext context, final List<PipelineListener> listeners) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    final String phase = PipelineNodeIds.classifyPhase(stage.getNodeId());
    try {
      LOG.debug(msg("kernel.pipeline.log.stage_executing"), stage.getName());
      notifyStageStarted(context, listeners, stage);
      runInterceptors(phase, Hook.PRE, context);
      stage.execute(context);
      runInterceptors(phase, Hook.POST, context);
      LOG.debug(msg("kernel.pipeline.log.stage_completed"), stage.getName());
      notifyStageCompleted(context, listeners, stage);
      return StepStatus.SUCCESS;
    } catch (StageException e) {
      final String stageFailed =
          msg("kernel.pipeline.error.stage_failed", stage.getName(), e.getMessage());
      LOG.error(stageFailed, e);
      context.addError(stageFailed);
      notifyStageFailed(context, listeners, stage, e);
      if (!e.isRecoverable() || context.isFailFast()) {
        return StepStatus.FAILURE_STOP;
      }
      return StepStatus.FAILURE_CONTINUE;
    } catch (RuntimeException ex) {
      final String stageFailed =
          msg("kernel.pipeline.error.stage_failed", stage.getName(), ex.getMessage());
      LOG.error(stageFailed, ex);
      context.addError(stageFailed);
      notifyStageFailed(context, listeners, stage, ex);
      return StepStatus.FAILURE_STOP;
    }
  }

  private void runInterceptors(final String phase, final Hook hook, final RunContext context) {
    if (phase == null) {
      return;
    }
    final String normalizedPhase = normalizeNodeId(phase);
    final Map<Hook, List<PhaseInterceptor>> hookMap = interceptors.get(normalizedPhase);
    if (hookMap == null) {
      return;
    }
    final List<PhaseInterceptor> list = hookMap.get(hook);
    if (list == null || list.isEmpty()) {
      return;
    }
    for (final PhaseInterceptor interceptor : list) {
      try {
        LOG.debug(
            msg("kernel.pipeline.log.interceptor_running"),
            interceptor.id(),
            normalizedPhase,
            hook);
        interceptor.apply(context);
      } catch (RuntimeException e) {
        final String warning =
            msg(
                "kernel.pipeline.warn.interceptor_failed",
                interceptor.id(),
                normalizedPhase,
                hook,
                e.getMessage());
        LOG.warn(warning, e);
        context.addWarning(warning);
      }
    }
  }

  private List<String> determineNodeIdsToRun(
      final List<String> specificNodeIds,
      final String fromNodeId,
      final String toNodeId,
      final List<String> dagOrder) {
    if (specificNodeIds != null && !specificNodeIds.isEmpty()) {
      final LinkedHashSet<String> unique = new LinkedHashSet<>();
      for (final String nodeId : specificNodeIds) {
        if (nodeId == null || nodeId.isBlank()) {
          continue;
        }
        unique.add(normalizeNodeId(nodeId));
      }
      return List.copyOf(unique);
    }
    if (dagOrder.isEmpty()) {
      return List.of();
    }
    final String from = fromNodeId != null ? normalizeNodeId(fromNodeId) : dagOrder.get(0);
    final String to =
        toNodeId != null ? normalizeNodeId(toNodeId) : dagOrder.get(dagOrder.size() - 1);
    final int fromIndex = dagOrder.indexOf(from);
    final int toIndex = dagOrder.indexOf(to);
    if (fromIndex < 0 || toIndex < 0) {
      throw new IllegalArgumentException(
          msg("kernel.pipeline.error.unregistered_node_range", from, to));
    }
    if (fromIndex > toIndex) {
      throw new IllegalArgumentException(msg("kernel.pipeline.error.invalid_range_order"));
    }
    return new ArrayList<>(dagOrder.subList(fromIndex, toIndex + 1));
  }

  private List<String> resolveDagOrder(final Map<String, StageNode> snapshotNodes) {
    if (snapshotNodes.isEmpty()) {
      return List.of();
    }
    final Map<String, Integer> registrationIndex = new HashMap<>();
    int index = 0;
    for (final String nodeId : snapshotNodes.keySet()) {
      registrationIndex.put(nodeId, index++);
    }
    final Map<String, Integer> indegree = new HashMap<>();
    final Map<String, List<String>> outgoing = new HashMap<>();
    for (final String nodeId : snapshotNodes.keySet()) {
      indegree.put(nodeId, 0);
      outgoing.put(nodeId, new ArrayList<>());
    }
    for (final StageNode node : snapshotNodes.values()) {
      for (final String dependency : node.dependsOn()) {
        if (!snapshotNodes.containsKey(dependency)) {
          throw new IllegalStateException(
              msg("kernel.pipeline.error.missing_dependency", node.nodeId(), dependency));
        }
        outgoing.get(dependency).add(node.nodeId());
        indegree.put(node.nodeId(), indegree.get(node.nodeId()) + 1);
      }
    }
    final PriorityQueue<String> ready =
        new PriorityQueue<>(
            (a, b) -> Integer.compare(registrationIndex.get(a), registrationIndex.get(b)));
    for (final Map.Entry<String, Integer> entry : indegree.entrySet()) {
      if (entry.getValue() == 0) {
        ready.add(entry.getKey());
      }
    }
    final List<String> ordered = new ArrayList<>(snapshotNodes.size());
    while (!ready.isEmpty()) {
      final String nodeId = ready.poll();
      ordered.add(nodeId);
      for (final String next : outgoing.get(nodeId)) {
        final int remaining = indegree.get(next) - 1;
        indegree.put(next, remaining);
        if (remaining == 0) {
          ready.add(next);
        }
      }
    }
    if (ordered.size() != snapshotNodes.size()) {
      final ArrayDeque<String> unresolved = new ArrayDeque<>();
      for (final Map.Entry<String, Integer> entry : indegree.entrySet()) {
        if (entry.getValue() > 0) {
          unresolved.add(entry.getKey());
        }
      }
      throw new IllegalStateException(
          msg("kernel.pipeline.error.dependency_cycle", String.join(", ", unresolved)));
    }
    return ordered;
  }

  /** Get registered stages keyed by node id. */
  public Map<String, Stage> getStages() {
    return getStageNodes();
  }

  /**
   * Get registered stage nodes keyed by node id.
   *
   * @return an unmodifiable map keyed by node id
   */
  public Map<String, Stage> getStageNodes() {
    synchronized (lock) {
      final LinkedHashMap<String, Stage> byNode = new LinkedHashMap<>();
      for (final StageNode node : stageNodes.values()) {
        byNode.put(node.nodeId(), node.stage());
      }
      return Collections.unmodifiableMap(byNode);
    }
  }

  private void notifyPipelineStarted(
      final RunContext context,
      final List<PipelineListener> snapshotListeners,
      final List<String> nodeIds) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    Objects.requireNonNull(snapshotListeners, msg(KEY_ERROR_LISTENERS_NULL));
    Objects.requireNonNull(nodeIds, msg(KEY_ERROR_NODE_IDS_NULL));
    notifyListeners(
        context,
        snapshotListeners,
        "onPipelineStarted",
        listener -> listener.onPipelineStarted(context, nodeIds));
  }

  private void notifyPipelineCompleted(
      final RunContext context,
      final List<PipelineListener> snapshotListeners,
      final int exitCode) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    Objects.requireNonNull(snapshotListeners, msg(KEY_ERROR_LISTENERS_NULL));
    notifyListeners(
        context,
        snapshotListeners,
        "onPipelineCompleted",
        listener -> listener.onPipelineCompleted(context, exitCode));
  }

  private void notifyStageStarted(
      final RunContext context, final List<PipelineListener> snapshotListeners, final Stage stage) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    Objects.requireNonNull(snapshotListeners, msg(KEY_ERROR_LISTENERS_NULL));
    Objects.requireNonNull(stage, msg(KEY_ERROR_STAGE_NULL));
    notifyListeners(
        context,
        snapshotListeners,
        "onStageStarted",
        listener -> listener.onStageStarted(context, stage));
  }

  private void notifyStageCompleted(
      final RunContext context, final List<PipelineListener> snapshotListeners, final Stage stage) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    Objects.requireNonNull(snapshotListeners, msg(KEY_ERROR_LISTENERS_NULL));
    Objects.requireNonNull(stage, msg(KEY_ERROR_STAGE_NULL));
    notifyListeners(
        context,
        snapshotListeners,
        "onStageCompleted",
        listener -> listener.onStageCompleted(context, stage));
  }

  private void notifyStageSkipped(
      final RunContext context, final List<PipelineListener> snapshotListeners, final Stage stage) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    Objects.requireNonNull(snapshotListeners, msg(KEY_ERROR_LISTENERS_NULL));
    Objects.requireNonNull(stage, msg(KEY_ERROR_STAGE_NULL));
    notifyListeners(
        context,
        snapshotListeners,
        "onStageSkipped",
        listener -> listener.onStageSkipped(context, stage));
  }

  private void notifyStageFailed(
      final RunContext context,
      final List<PipelineListener> snapshotListeners,
      final Stage stage,
      final StageException e) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    Objects.requireNonNull(snapshotListeners, msg(KEY_ERROR_LISTENERS_NULL));
    Objects.requireNonNull(stage, msg(KEY_ERROR_STAGE_NULL));
    Objects.requireNonNull(e, msg(KEY_ERROR_EXCEPTION_NULL));
    notifyListeners(
        context,
        snapshotListeners,
        "onStageFailed",
        listener -> listener.onStageFailed(context, stage, e));
  }

  private void notifyStageFailed(
      final RunContext context,
      final List<PipelineListener> snapshotListeners,
      final Stage stage,
      final Throwable t) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    Objects.requireNonNull(snapshotListeners, msg(KEY_ERROR_LISTENERS_NULL));
    Objects.requireNonNull(stage, msg(KEY_ERROR_STAGE_NULL));
    Objects.requireNonNull(t, msg(KEY_ERROR_THROWABLE_NULL));
    notifyListeners(
        context,
        snapshotListeners,
        "onStageFailed",
        listener -> listener.onStageFailed(context, stage, t));
  }

  private void notifyListeners(
      final RunContext context,
      final List<PipelineListener> snapshotListeners,
      final String eventName,
      final Consumer<PipelineListener> invocation) {
    Objects.requireNonNull(context, msg(KEY_ERROR_CONTEXT_NULL));
    for (final var listener : snapshotListeners) {
      try {
        invocation.accept(listener);
      } catch (RuntimeException ex) {
        final String listenerName = listener.getClass().getName();
        LOG.error(msg("kernel.pipeline.error.listener_failed", listenerName, eventName), ex);
        context.addWarning(
            msg("kernel.pipeline.warn.listener_failed", listenerName, eventName, ex.getMessage()));
      }
    }
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  /** Listener interface for pipeline events. */
  public interface PipelineListener {
    default void onPipelineStarted(final RunContext context, final List<String> nodeIds) {}

    default void onPipelineCompleted(final RunContext context, final int exitCode) {}

    default void onStageStarted(final RunContext context, final Stage stage) {}

    default void onStageCompleted(final RunContext context, final Stage stage) {}

    default void onStageSkipped(final RunContext context, final Stage stage) {}

    default void onStageFailed(
        final RunContext context, final Stage stage, final StageException e) {}

    default void onStageFailed(final RunContext context, final Stage stage, final Throwable t) {}
  }

  private record StageNode(String nodeId, Stage stage, List<String> dependsOn) {}

  private enum StepStatus {
    SUCCESS,
    FAILURE_CONTINUE,
    FAILURE_STOP
  }
}
