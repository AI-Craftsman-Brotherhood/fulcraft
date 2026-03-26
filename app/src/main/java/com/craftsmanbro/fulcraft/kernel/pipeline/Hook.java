package com.craftsmanbro.fulcraft.kernel.pipeline;

/**
 * Defines the execution timing of a {@link PhaseInterceptor} relative to the main phase processing.
 *
 * <p>Each phase (e.g., analyze, generate) has a main action. Interceptors can be registered to run
 * either before or after this main action using the {@code PRE} or {@code POST} hook respectively.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public class MyInterceptor implements PhaseInterceptor {
 *     @Override
 *     public Hook hook() {
 *         return Hook.PRE; // Run before the main phase action
 *     }
 * }
 * }</pre>
 *
 * @see PhaseInterceptor
 * @see PipelineNodeIds
 */
public enum Hook {

  /**
   * Indicates that the interceptor should be executed <strong>before</strong> the main phase
   * processing.
   *
   * <p>PRE interceptors are typically used for:
   *
   * <ul>
   *   <li>Validation of preconditions
   *   <li>Loading or preparing data required by the main action
   *   <li>Security checks or filtering
   * </ul>
   */
  PRE,

  /**
   * Indicates that the interceptor should be executed <strong>after</strong> the main phase
   * processing.
   *
   * <p>POST interceptors are typically used for:
   *
   * <ul>
   *   <li>Cleanup or resource release
   *   <li>Logging or metrics collection
   *   <li>Transformation or enrichment of results
   * </ul>
   */
  POST
}
