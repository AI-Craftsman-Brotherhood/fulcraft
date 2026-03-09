package com.craftsmanbro.fulcraft.infrastructure.coverage.impl;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.coverage.contract.CoverageLoaderFactoryPort;
import com.craftsmanbro.fulcraft.infrastructure.coverage.contract.CoverageLoaderPort;
import com.craftsmanbro.fulcraft.infrastructure.coverage.model.CoverageReportReference;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Factory for resolving coverage loader instances. */
public final class CoverageLoaderAdapterFactory implements CoverageLoaderFactoryPort {

  private static final String DEFAULT_JACOCO_REPORT_PATH =
      "build/reports/jacoco/test/jacocoTestReport.xml";

  private static final CoverageLoaderAdapterFactory INSTANCE = new CoverageLoaderAdapterFactory();

  private CoverageLoaderAdapterFactory() {}

  public static CoverageLoaderFactoryPort port() {
    return INSTANCE;
  }

  public static CoverageLoaderPort createPort(final Path projectRoot, final Config config) {
    return INSTANCE.createCoverageLoader(projectRoot, config);
  }

  @Override
  public CoverageLoaderPort createCoverageLoader(final Path projectRoot, final Config config) {
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "projectRoot"));
    if (config == null) {
      return null;
    }
    final String coverageTool = resolveCoverageTool(config);
    if (coverageTool != null && !"jacoco".equalsIgnoreCase(coverageTool)) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Unsupported coverage tool for selection: " + coverageTool));
      return null;
    }
    final CoverageReportReference coverageReportReference = resolveReportPath(projectRoot, config);
    if (coverageReportReference == null) {
      return null;
    }
    return new JacocoCoverageAdapter(coverageReportReference.path());
  }

  private static String resolveCoverageTool(final Config config) {
    final Config.QualityGateConfig qualityGateConfig = config.getQualityGate();
    if (qualityGateConfig == null) {
      return "jacoco";
    }
    return qualityGateConfig.getCoverageTool();
  }

  private static CoverageReportReference resolveReportPath(
      final Path projectRoot, final Config config) {
    final String configuredReportPath = resolveConfiguredReportPath(config);
    if (configuredReportPath == null || configuredReportPath.isBlank()) {
      final Path defaultReportPath = projectRoot.resolve(DEFAULT_JACOCO_REPORT_PATH);
      if (Files.exists(defaultReportPath)) {
        return new CoverageReportReference(defaultReportPath, false);
      }
      return null;
    }
    final Path configuredReport = Path.of(configuredReportPath);
    final Path resolvedReportPath =
        configuredReport.isAbsolute() ? configuredReport : projectRoot.resolve(configuredReport);
    if (Files.exists(resolvedReportPath)) {
      return new CoverageReportReference(resolvedReportPath, true);
    }
    // An explicit report path suppresses fallback to the default report location.
    return null;
  }

  private static String resolveConfiguredReportPath(final Config config) {
    final Config.QualityGateConfig qualityGateConfig = config.getQualityGate();
    if (qualityGateConfig == null) {
      return null;
    }
    return qualityGateConfig.getCoverageReportPath();
  }
}
