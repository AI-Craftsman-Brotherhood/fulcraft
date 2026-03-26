package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.index.ProjectSymbolIndex;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Facade for dynamic resolution within the analysis package.
 *
 * <p>This class provides a stable API for resolving dynamic code patterns (Class.forName,
 * getMethod, ServiceLoader, etc.).
 */
public final class DynamicResolutions {

  private final DynamicResolver resolver;

  public DynamicResolutions() {
    this.resolver = new DynamicResolver();
  }

  /**
   * Sets the project symbol index for resolution.
   *
   * @param symbolIndex the symbol index
   */
  public void setSymbolIndex(final ProjectSymbolIndex symbolIndex) {
    resolver.setProjectSymbolIndex(symbolIndex);
  }

  /**
   * Sets external configuration values for resolution.
   *
   * @param configValues the configuration values
   */
  public void setExternalConfigValues(final Map<String, String> configValues) {
    resolver.setExternalConfigValues(configValues);
  }

  /**
   * Resolves dynamic patterns in the analysis result.
   *
   * @param result the analysis result
   * @param projectRoot the project root
   * @param enableInterprocedural enable interprocedural resolution
   * @param callsiteLimit callsite limit for resolution
   * @param debugMode enable debug logging
   * @param experimentalCandidateEnum enable experimental candidate enumeration
   */
  public void resolve(
      final AnalysisResult result,
      final Path projectRoot,
      final boolean enableInterprocedural,
      final int callsiteLimit,
      final boolean debugMode,
      final boolean experimentalCandidateEnum) {
    Objects.requireNonNull(
        result,
        MessageSource.getMessage("analysis.common.error.argument_null", "result must not be null"));
    Objects.requireNonNull(
        projectRoot,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "projectRoot must not be null"));
    resolver.resolve(
        result,
        projectRoot,
        enableInterprocedural,
        callsiteLimit,
        debugMode,
        experimentalCandidateEnum);
  }

  /**
   * Gets the resolved dynamic resolutions.
   *
   * @return list of resolutions
   */
  public List<DynamicResolution> getResolutions() {
    return resolver.getResolutions();
  }

  /**
   * Gets counts by subtype.
   *
   * @return map of subtype to count
   */
  public Map<String, Long> countBySubtype() {
    return resolver.countBySubtype();
  }

  /**
   * Gets counts by trust level.
   *
   * @return map of trust level to count
   */
  public Map<String, Long> countByTrustLevel() {
    return resolver.countByTrustLevel();
  }

  /**
   * Gets the average confidence of resolutions.
   *
   * @return average confidence (0.0 to 1.0)
   */
  public double getAverageConfidence() {
    return resolver.getAverageConfidence();
  }

  /**
   * Gets the underlying resolver for compatibility.
   *
   * @return the underlying resolver
   */
  public DynamicResolver unwrap() {
    return resolver;
  }
}
