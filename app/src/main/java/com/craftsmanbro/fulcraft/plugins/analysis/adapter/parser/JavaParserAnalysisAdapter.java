package com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.JavaParserAnalyzer;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * JavaParser-based implementation of AnalysisPort.
 *
 * <p>This adapter wraps infrastructure parser contract and converts it into feature models.
 */
public class JavaParserAnalysisAdapter implements AnalysisPort {

  private static final String ENGINE_NAME = "javaparser";

  private final com.craftsmanbro.fulcraft.infrastructure.parser.contract.AnalysisPort delegate;

  /** Creates a JavaParserAnalysisAdapter with default configuration. */
  public JavaParserAnalysisAdapter() {
    final Tracer tracer = GlobalOpenTelemetry.getTracer("utgenerator");
    this.delegate = new JavaParserAnalyzer(tracer);
  }

  /**
   * Creates a JavaParserAnalysisAdapter with a custom tracer.
   *
   * @param tracer the OpenTelemetry tracer to use
   */
  public JavaParserAnalysisAdapter(final Tracer tracer) {
    Objects.requireNonNull(
        tracer,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "tracer must not be null"));
    this.delegate = new JavaParserAnalyzer(tracer);
  }

  /**
   * Creates a JavaParserAnalysisAdapter with a pre-configured analyzer. Primarily for testing.
   *
   * @param delegate the infrastructure parser port to delegate to
   */
  JavaParserAnalysisAdapter(
      final com.craftsmanbro.fulcraft.infrastructure.parser.contract.AnalysisPort delegate) {
    this.delegate =
        Objects.requireNonNull(
            delegate,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "analysis.common.error.argument_null", "delegate must not be null"));
  }

  @Override
  public AnalysisResult analyze(final Path projectRoot, final Config config) throws IOException {
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "projectRoot must not be null"));
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "config must not be null"));
    return AnalysisResultAdapter.toFeature(delegate.analyze(config, projectRoot));
  }

  @Override
  public String getEngineName() {
    return ENGINE_NAME;
  }

  @Override
  public boolean supports(final Path projectRoot) {
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "projectRoot must not be null"));
    if (!Files.isDirectory(projectRoot)) {
      return false;
    }
    // Check for standard Java project structure
    return Files.isDirectory(projectRoot.resolve("src/main/java"))
        || Files.isDirectory(projectRoot.resolve("app/src/main/java"))
        || Files.isDirectory(projectRoot.resolve("src"))
        || containsSourceDirectory(projectRoot)
        || containsJavaFiles(projectRoot);
  }

  private boolean containsSourceDirectory(final Path directory) {
    if (!Files.isDirectory(directory)) {
      return false;
    }
    try (var stream = Files.walk(directory, 3)) {
      return stream
          .filter(Files::isDirectory)
          .anyMatch(
              p -> {
                final Path fileName = p.getFileName();
                return fileName != null && "src".equals(fileName.toString());
              });
    } catch (IOException e) {
      return false;
    }
  }

  private boolean containsJavaFiles(final Path directory) {
    if (!Files.isDirectory(directory)) {
      return false;
    }
    try (var stream = Files.walk(directory, 3)) {
      return stream
          .filter(Files::isRegularFile)
          .anyMatch(
              p -> {
                final Path fileName = p.getFileName();
                return fileName != null && fileName.toString().endsWith(".java");
              });
    } catch (IOException e) {
      return false;
    }
  }
}
