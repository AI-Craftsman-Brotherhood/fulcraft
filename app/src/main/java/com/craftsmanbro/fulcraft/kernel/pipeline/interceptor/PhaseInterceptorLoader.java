package com.craftsmanbro.fulcraft.kernel.pipeline.interceptor;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.Config.InterceptorEntryConfig;
import com.craftsmanbro.fulcraft.config.Config.InterceptorsConfig;
import com.craftsmanbro.fulcraft.config.Config.PhaseInterceptorsConfig;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.Hook;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.logging.LoggerPort;
import com.craftsmanbro.fulcraft.logging.LoggerPortProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Loads and organizes {@link PhaseInterceptor} implementations from the classpath.
 *
 * <p>This class uses {@link ServiceLoader} to discover all available interceptor implementations,
 * filters them based on configuration, and organizes them by phase and hook for efficient lookup
 * during pipeline execution.
 *
 * <p>Configuration in config.json can override the enabled state and order of interceptors:
 *
 * <pre>{@code
 * interceptors:
 *   ANALYZE:
 *     pre:
 *       - class: com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.ConfigLoaderInterceptor
 *         enabled: true
 *         order: 10
 * }</pre>
 *
 * @see PhaseInterceptor
 * @see ServiceLoader
 */
public class PhaseInterceptorLoader {

  private static final LoggerPort LOG = LoggerPortProvider.getLogger(PhaseInterceptorLoader.class);

  /**
   * Loads all {@link PhaseInterceptor} implementations from the classpath.
   *
   * <p>Interceptors are discovered via {@link ServiceLoader}, filtered by configuration and {@link
   * PhaseInterceptor#supports(Config)}, grouped by phase node id and {@link Hook}, and sorted by
   * configured or default order within each group.
   *
   * @param config the configuration used for filtering interceptors, must not be null
   * @return an immutable map of interceptors organized by phase node id and hook
   * @throws NullPointerException if config is null
   */
  public Map<String, Map<Hook, List<PhaseInterceptor>>> loadAll(final Config config) {
    Objects.requireNonNull(config, msg("kernel.interceptor.loader.error.config_null"));
    final Map<String, InterceptorEntryConfig> configLookup = buildConfigLookup(config);
    final Map<String, Map<Hook, List<InterceptorWithOrder>>> result = new HashMap<>();
    final ServiceLoader<PhaseInterceptor> serviceLoader =
        ServiceLoader.load(PhaseInterceptor.class);
    final var iterator = serviceLoader.iterator();
    while (true) {
      final ServiceLoadAttempt loadAttempt = tryLoadNextInterceptor(iterator);
      if (!loadAttempt.hasMore()) {
        break;
      }
      if (loadAttempt.interceptor() != null) {
        processInterceptor(loadAttempt.interceptor(), config, configLookup, result);
      }
    }
    return freezeResult(result);
  }

  private void processInterceptor(
      final PhaseInterceptor interceptor,
      final Config config,
      final Map<String, InterceptorEntryConfig> configLookup,
      final Map<String, Map<Hook, List<InterceptorWithOrder>>> result) {
    try {
      final InterceptorRegistration registration =
          resolveRegistration(interceptor, config, configLookup);
      if (registration == null) {
        return;
      }
      final Map<Hook, List<InterceptorWithOrder>> byHook =
          result.computeIfAbsent(registration.phase(), ignored -> newMutableHookMap());
      byHook
          .get(registration.hook())
          .add(new InterceptorWithOrder(interceptor, registration.order()));
      LOG.debug(
          msg("kernel.interceptor.loader.log.loaded"),
          interceptor.id(),
          registration.phase(),
          registration.hook(),
          registration.order());
    } catch (RuntimeException e) {
      LOG.warn(
          msg(
              "kernel.interceptor.loader.warn.interceptor_process_failed",
              interceptor.getClass().getName(),
              e.getMessage()));
    }
  }

  /** Builds a lookup map from interceptor class name to its configuration entry. */
  private Map<String, InterceptorEntryConfig> buildConfigLookup(final Config config) {
    final Map<String, InterceptorEntryConfig> lookup = new HashMap<>();
    final InterceptorsConfig interceptorsConfig = config.getInterceptors();
    if (interceptorsConfig == null) {
      return lookup;
    }
    for (final PhaseInterceptorsConfig phaseConfig : interceptorsConfig.configuredPhaseConfigs()) {
      if (phaseConfig == null) {
        continue;
      }
      addPhaseEntriesToLookup(phaseConfig, lookup);
    }
    return lookup;
  }

  private void addPhaseEntriesToLookup(
      final PhaseInterceptorsConfig phaseConfig, final Map<String, InterceptorEntryConfig> lookup) {
    addEntriesToLookup(phaseConfig.getPre(), lookup);
    addEntriesToLookup(phaseConfig.getPost(), lookup);
  }

  private void addEntriesToLookup(
      final List<InterceptorEntryConfig> entries,
      final Map<String, InterceptorEntryConfig> lookup) {
    if (entries == null) {
      return;
    }
    for (final InterceptorEntryConfig entry : entries) {
      if (entry.getClassName() != null) {
        lookup.put(entry.getClassName(), entry);
      }
    }
  }

  private Map<String, Map<Hook, List<PhaseInterceptor>>> freezeResult(
      final Map<String, Map<Hook, List<InterceptorWithOrder>>> mutableResult) {
    final Map<String, Map<Hook, List<PhaseInterceptor>>> frozen = new HashMap<>();
    for (final Map.Entry<String, Map<Hook, List<InterceptorWithOrder>>> stepEntry :
        mutableResult.entrySet()) {
      frozen.put(stepEntry.getKey(), freezeHookMap(stepEntry.getValue()));
    }
    return Collections.unmodifiableMap(frozen);
  }

  /**
   * Returns the interceptors for a specific phase and hook.
   *
   * <p>This is a convenience method that loads all interceptors and returns only the requested
   * subset. For repeated access, consider caching the result of {@link #loadAll(Config)}.
   *
   * @param config the configuration for filtering
   * @param phase the target phase node id
   * @param hook the target hook (PRE or POST)
   * @return an immutable list of matching interceptors, sorted by order
   */
  public List<PhaseInterceptor> loadFor(final Config config, final String phase, final Hook hook) {
    final String normalizedPhase = normalizePhase(phase);
    Objects.requireNonNull(normalizedPhase, msg("kernel.interceptor.loader.error.phase_null"));
    Objects.requireNonNull(hook, msg("kernel.interceptor.loader.error.hook_null"));
    return loadAll(config).getOrDefault(normalizedPhase, Map.of()).getOrDefault(hook, List.of());
  }

  private static String normalizePhase(final String phase) {
    return PipelineNodeIds.normalizeNullable(phase);
  }

  private static ServiceLoadAttempt tryLoadNextInterceptor(
      final Iterator<PhaseInterceptor> iterator) {
    try {
      if (!iterator.hasNext()) {
        return ServiceLoadAttempt.finished();
      }
      return ServiceLoadAttempt.loaded(iterator.next());
    } catch (java.util.ServiceConfigurationError e) {
      LOG.warn(msg("kernel.interceptor.loader.warn.service_load_failed", e.getMessage()));
      return ServiceLoadAttempt.failed();
    }
  }

  private InterceptorRegistration resolveRegistration(
      final PhaseInterceptor interceptor,
      final Config config,
      final Map<String, InterceptorEntryConfig> configLookup) {
    final InterceptorEntryConfig entryConfig = configLookup.get(interceptor.getClass().getName());
    if (entryConfig != null && !entryConfig.getEnabled()) {
      LOG.debug(msg("kernel.interceptor.loader.log.disabled_by_config"), interceptor.id());
      return null;
    }
    if (!interceptor.supports(config)) {
      LOG.debug(
          msg("kernel.interceptor.loader.log.disabled_by_supports"),
          interceptor.id(),
          interceptor.phase());
      return null;
    }

    // Normalization can invalidate misconfigured phase ids; skip those registrations consistently.
    final String phase = normalizePhase(interceptor.phase());
    final Hook hook = interceptor.hook();
    if (phase == null || hook == null) {
      LOG.warn(msg("kernel.interceptor.loader.warn.null_phase_or_hook"), interceptor.id());
      return null;
    }
    return new InterceptorRegistration(phase, hook, resolveOrder(interceptor, entryConfig));
  }

  private int resolveOrder(
      final PhaseInterceptor interceptor, final InterceptorEntryConfig entryConfig) {
    return (entryConfig != null && entryConfig.getOrder() != null)
        ? entryConfig.getOrder()
        : interceptor.order();
  }

  private Map<Hook, List<PhaseInterceptor>> freezeHookMap(
      final Map<Hook, List<InterceptorWithOrder>> mutableHookMap) {
    final Map<Hook, List<PhaseInterceptor>> frozenHookMap = new HashMap<>();
    for (final Map.Entry<Hook, List<InterceptorWithOrder>> hookEntry : mutableHookMap.entrySet()) {
      frozenHookMap.put(
          hookEntry.getKey(),
          Collections.unmodifiableList(toSortedInterceptors(hookEntry.getValue())));
    }
    return Collections.unmodifiableMap(frozenHookMap);
  }

  private List<PhaseInterceptor> toSortedInterceptors(
      final List<InterceptorWithOrder> interceptorsWithOrder) {
    final List<InterceptorWithOrder> sortedList = new ArrayList<>(interceptorsWithOrder);
    sortedList.sort(Comparator.comparingInt(InterceptorWithOrder::order));
    final List<PhaseInterceptor> interceptors = new ArrayList<>();
    for (final InterceptorWithOrder interceptorWithOrder : sortedList) {
      interceptors.add(interceptorWithOrder.interceptor());
    }
    return interceptors;
  }

  private static Map<Hook, List<InterceptorWithOrder>> newMutableHookMap() {
    final Map<Hook, List<InterceptorWithOrder>> hookMap = new HashMap<>();
    for (final Hook hook : Hook.values()) {
      hookMap.put(hook, new ArrayList<>());
    }
    return hookMap;
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  /** Internal record to hold interceptor with its effective order for sorting. */
  private record InterceptorWithOrder(PhaseInterceptor interceptor, int order) {}

  private record InterceptorRegistration(String phase, Hook hook, int order) {}

  private record ServiceLoadAttempt(boolean hasMore, PhaseInterceptor interceptor) {

    private static ServiceLoadAttempt failed() {
      return new ServiceLoadAttempt(true, null);
    }

    private static ServiceLoadAttempt finished() {
      return new ServiceLoadAttempt(false, null);
    }

    private static ServiceLoadAttempt loaded(final PhaseInterceptor interceptor) {
      return new ServiceLoadAttempt(true, interceptor);
    }
  }
}
