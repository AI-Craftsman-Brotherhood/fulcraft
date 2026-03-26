package com.craftsmanbro.fulcraft.plugins.document.contract;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for document generation from analysis results.
 *
 * <p>Implementations of this interface generate various types of documentation (Markdown, HTML,
 * PDF, diagrams) from static analysis results. This provides a pluggable architecture for adding
 * new output formats.
 */
public interface DocumentGenerator {

  /**
   * Generates documentation from the analysis result.
   *
   * @param result the analysis result containing class and method information
   * @param outputDir the output directory for generated documentation
   * @param config the configuration object
   * @return the number of files generated
   * @throws IOException if writing fails
   */
  int generate(AnalysisResult result, Path outputDir, Config config) throws IOException;

  /**
   * Returns the output format identifier for this generator.
   *
   * @return the format name (e.g., "markdown", "html", "pdf", "diagram")
   */
  default String getFormat() {
    return "unknown";
  }

  /**
   * Returns the file extension used by this generator.
   *
   * @return the file extension (e.g., ".md", ".html", ".pdf")
   */
  default String getFileExtension() {
    return ".txt";
  }
}
