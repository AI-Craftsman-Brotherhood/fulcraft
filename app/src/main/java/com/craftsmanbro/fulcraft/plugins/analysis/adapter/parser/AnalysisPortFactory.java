package com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultMerger;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Factory for creating AnalysisPort implementations.
 *
 * <p>This factory selects the appropriate analysis engine based on configuration.
 */
public final class AnalysisPortFactory {

  /**
   * Default engine name. Matches the CLI wiring in {@code DefaultServiceFactory}, which runs both
   * JavaParser and Spoon in parallel and merges their results.
   */
  private static final String DEFAULT_ENGINE_NAME = "composite";

  private AnalysisPortFactory() {
    // Utility class
  }

  /** Engine type enumeration. */
  public enum EngineType {
    JAVAPARSER,
    SPOON,
    COMPOSITE
  }

  /**
   * Creates an AnalysisPort based on the configuration.
   *
   * @param config the application configuration
   * @return the appropriate AnalysisPort implementation
   */
  public static AnalysisPort create(final Config config) {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "config must not be null"));
    String engineName = null;
    if (config.getAnalysis() != null) {
      engineName = config.getAnalysis().getEngine();
    }
    // Default to Composite to match CLI wiring (DefaultServiceFactory.createDefaultAnalysisPort).
    return create(parseEngineType(engineName));
  }

  /**
   * Creates an AnalysisPort for the specified engine type.
   *
   * @param engineType the engine type to create
   * @return the appropriate AnalysisPort implementation
   */
  public static AnalysisPort create(final EngineType engineType) {
    return switch (engineType) {
      case JAVAPARSER -> createJavaParser();
      case SPOON -> createSpoon();
      case COMPOSITE -> createComposite();
    };
  }

  /**
   * Creates a JavaParser-based AnalysisPort.
   *
   * @return JavaParser adapter
   */
  public static AnalysisPort createJavaParser() {
    final Tracer tracer = GlobalOpenTelemetry.getTracer("utgenerator");
    return new JavaParserAnalysisAdapter(tracer);
  }

  /**
   * Creates a Spoon-based AnalysisPort.
   *
   * @return Spoon adapter
   */
  public static AnalysisPort createSpoon() {
    return new SpoonAnalysisAdapter();
  }

  /**
   * Creates a composite AnalysisPort with both JavaParser and Spoon.
   *
   * @return Composite adapter
   */
  public static AnalysisPort createComposite() {
    final ResultMerger merger = new ResultMerger();
    return CompositeAnalysisAdapter.createDefault(merger);
  }

  /**
   * Creates a composite AnalysisPort with custom analyzers.
   *
   * @param analyzers the analyzers to use
   * @param merger the result merger
   * @return Composite adapter
   */
  public static AnalysisPort createComposite(
      final List<AnalysisPort> analyzers, final ResultMerger merger) {
    return new CompositeAnalysisAdapter(analyzers, merger);
  }

  /**
   * Gets the default engine type.
   *
   * @return the default engine type ({@link EngineType#COMPOSITE}) -- matches the CLI wiring in
   *     {@code DefaultServiceFactory.createDefaultAnalysisPort()}.
   */
  public static EngineType getDefaultEngineType() {
    return EngineType.COMPOSITE;
  }

  /**
   * Lists all available engine types.
   *
   * @return list of available engine types
   */
  public static List<EngineType> getAvailableEngines() {
    return List.of(EngineType.values());
  }

  private static EngineType parseEngineType(final String engineName) {
    if (engineName == null || engineName.isBlank()) {
      return getDefaultEngineType();
    }
    return switch (engineName.toLowerCase(Locale.ROOT)) {
      case "javaparser", "jp" -> EngineType.JAVAPARSER;
      case "spoon" -> EngineType.SPOON;
      case "composite", "all", "both" -> EngineType.COMPOSITE;
      default -> getDefaultEngineType();
    };
  }
}
