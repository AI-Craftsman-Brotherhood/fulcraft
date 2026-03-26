package com.craftsmanbro.fulcraft.kernel.pipeline.interceptor;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.Hook;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;

/**
 * Defines the contract for pipeline phase interceptors.
 *
 * <p>A {@code PhaseInterceptor} allows custom logic to be executed before ({@link Hook#PRE}) or
 * after ({@link Hook#POST}) the main processing of a pipeline phase. This enables extensibility
 * without modifying core pipeline logic.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and can be enabled or
 * disabled based on configuration.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class SecurityFilterInterceptor implements PhaseInterceptor {
 *   @Override
 *   public String id() {
 *     return "security-filter";
 *   }
 *
 *   @Override
 *   public String phase() {
 *     return PipelineNodeIds.GENERATE;
 *   }
 *
 *   @Override
 *   public Hook hook() {
 *     return Hook.PRE;
 *   }
 *
 *   @Override
 *   public void apply(RunContext context) {
 *     // Filter out sensitive targets
 *   }
 * }
 * }</pre>
 *
 * @see Hook
 * @see PipelineNodeIds
 */
public interface PhaseInterceptor {

  /**
   * Returns a unique identifier for this interceptor.
   *
   * <p>The ID is used for logging, configuration, and debugging purposes. It should be a
   * human-readable, kebab-case string (e.g., "config-loader", "security-filter").
   *
   * @return a non-null unique identifier
   */
  String id();

  /**
   * Returns the pipeline phase this interceptor targets.
   *
   * <p>The interceptor will only be invoked during the specified phase (e.g., {@link
   * PipelineNodeIds#ANALYZE}, {@link PipelineNodeIds#GENERATE}).
   *
   * @return the target phase, never null
   */
  String phase();

  /**
   * Returns the execution timing relative to the main phase action.
   *
   * <p>Use {@link Hook#PRE} to run before the main action, or {@link Hook#POST} to run after.
   *
   * @return the hook type, never null
   */
  Hook hook();

  /**
   * Returns the execution order within the same phase and hook.
   *
   * <p>Lower values are executed first. Interceptors with the same order are executed in an
   * undefined order. The default value is 100.
   *
   * @return the execution priority (lower = earlier)
   */
  default int order() {
    return 100;
  }

  /**
   * Determines whether this interceptor should be active for the given configuration.
   *
   * <p>This method allows interceptors to be conditionally enabled based on user settings. The
   * default implementation always returns {@code true}.
   *
   * @param config the current configuration, never null
   * @return {@code true} if the interceptor should be executed, {@code false} otherwise
   */
  default boolean supports(final Config config) {
    return true;
  }

  /**
   * Executes the interceptor logic.
   *
   * <p>This method is called by the pipeline at the appropriate time (before or after the main
   * phase action, depending on {@link #hook()}). Implementations may read from or modify the {@link
   * RunContext}.
   *
   * <p>If an exception is thrown, the pipeline will log a warning and continue execution.
   *
   * @param context the current run context, never null
   */
  void apply(RunContext context);
}
