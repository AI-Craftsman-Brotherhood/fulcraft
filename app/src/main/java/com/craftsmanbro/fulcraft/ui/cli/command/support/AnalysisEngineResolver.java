package com.craftsmanbro.fulcraft.ui.cli.command.support;

import com.craftsmanbro.fulcraft.config.Config;

/**
 * Resolves the effective analysis engine type for a CLI invocation.
 *
 * <p>Precedence (highest first):
 *
 * <ol>
 *   <li>An explicit {@code --engine} CLI flag.
 *   <li>{@code analysis.engine} from the loaded configuration.
 *   <li>The project default ({@value #DEFAULT_ENGINE_TYPE}).
 * </ol>
 *
 * <p>This exists because the {@code --engine} option must not carry a hard picocli {@code
 * defaultValue}; doing so makes the CLI value always non-null and silently shadows the {@code
 * analysis.engine} configuration. By defaulting at the CLI layer to {@code null}, the configured
 * value is honored when no flag is supplied.
 */
public final class AnalysisEngineResolver {

  /** Default analysis engine when neither the CLI flag nor configuration specify one. */
  public static final String DEFAULT_ENGINE_TYPE = "composite";

  private AnalysisEngineResolver() {
    // utility
  }

  /**
   * Resolve the effective engine type.
   *
   * @param cliEngine value of the {@code --engine} flag, or {@code null}/blank when not supplied
   * @param config loaded configuration (may be {@code null})
   * @return the resolved engine type, never {@code null}
   */
  public static String resolve(final String cliEngine, final Config config) {
    if (cliEngine != null && !cliEngine.isBlank()) {
      return cliEngine.trim();
    }
    if (config != null && config.getAnalysis() != null) {
      final String configured = config.getAnalysis().getEngine();
      if (configured != null && !configured.isBlank()) {
        return configured.trim();
      }
    }
    return DEFAULT_ENGINE_TYPE;
  }
}
