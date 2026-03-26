package com.craftsmanbro.fulcraft.infrastructure.llm.impl.request;

import com.craftsmanbro.fulcraft.config.Config;

/**
 * Factory for creating generation parameters from Config.
 *
 * <p>This is the single source of truth for Config → generation parameters conversion. All LLM
 * clients should use this factory to ensure consistent parameter handling.
 *
 * <p>Deterministic mode behavior:
 *
 * <ul>
 *   <li>deterministic=true: temperature=0.0, seed=config.seed or DEFAULT_SEED (42)
 *   <li>deterministic=false: temperature=0.2, seed=config.seed (null if not set)
 * </ul>
 */
public final class LlmRequestFactory {

  /** Default seed for deterministic mode when not specified in config. */
  public static final int DEFAULT_SEED = 42;

  /** Temperature for deterministic mode (forced to 0.0). */
  public static final double DETERMINISTIC_TEMPERATURE = 0.0;

  /** Default temperature for non-deterministic mode. */
  public static final double DEFAULT_TEMPERATURE = 0.2;

  private LlmRequestFactory() {
    // Utility class
  }

  /**
   * Immutable container for generation parameters.
   *
   * @param temperature Temperature for generation (0.0-1.0)
   * @param topP Top-p sampling parameter (null if not specified)
   * @param seed Random seed for reproducibility (null if not specified)
   * @param maxTokens Maximum tokens to generate (null if not specified)
   */
  public record GenerationParams(
      Double temperature, Double topP, Integer seed, Integer maxTokens) {}

  /**
   * Resolves generation parameters from Config.
   *
   * <p>When deterministic=true:
   *
   * <ul>
   *   <li>temperature is forced to 0.0 (regardless of config value)
   *   <li>seed uses config value or DEFAULT_SEED (42)
   *   <li>maxTokens uses config value if specified
   * </ul>
   *
   * <p>When deterministic=false:
   *
   * <ul>
   *   <li>temperature uses config value if specified, otherwise DEFAULT_TEMPERATURE (0.2)
   *   <li>seed is only set if explicitly specified in config
   *   <li>maxTokens uses config value if specified
   * </ul>
   *
   * @param config LLM configuration (may be null)
   * @return Resolved generation parameters
   */
  public static GenerationParams resolveParams(final Config.LlmConfig config) {
    if (config == null) {
      // Default: deterministic mode with default seed
      return new GenerationParams(DETERMINISTIC_TEMPERATURE, null, DEFAULT_SEED, null);
    }
    final boolean deterministic = Boolean.TRUE.equals(config.getDeterministic());
    if (!deterministic) {
      // Non-deterministic: respect config values
      final double temperature =
          config.getTemperature() != null ? config.getTemperature() : DEFAULT_TEMPERATURE;
      return new GenerationParams(temperature, null, config.getSeed(), config.getMaxTokens());
    }
    // Deterministic mode: force temperature=0, use seed
    final int seed = config.getSeed() != null ? config.getSeed() : DEFAULT_SEED;
    return new GenerationParams(DETERMINISTIC_TEMPERATURE, null, seed, config.getMaxTokens());
  }
}
