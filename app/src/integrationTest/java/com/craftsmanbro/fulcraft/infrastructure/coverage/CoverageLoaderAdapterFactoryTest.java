package com.craftsmanbro.fulcraft.infrastructure.coverage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.coverage.contract.CoverageLoaderFactoryPort;
import com.craftsmanbro.fulcraft.infrastructure.coverage.contract.CoverageLoaderPort;
import com.craftsmanbro.fulcraft.infrastructure.coverage.impl.CoverageLoaderAdapterFactory;
import com.craftsmanbro.fulcraft.infrastructure.coverage.impl.JacocoCoverageAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link CoverageLoaderAdapterFactory}.
 *
 * <p>Verifies factory creation logic, config-based tool selection, and report path resolution.
 */
class CoverageLoaderAdapterFactoryTest {

  @TempDir Path tempDir;

  private Config config;

  @BeforeEach
  void setUp() {
    config = new Config();
    Config.QualityGateConfig qualityGate = new Config.QualityGateConfig();
    config.setQualityGate(qualityGate);
  }

  // --- Null handling tests ---

  @Test
  void create_withNullProjectRoot_throwsNullPointerException() {
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class, () -> CoverageLoaderAdapterFactory.createPort(null, config));
  }

  @Test
  void create_withNullConfig_returnsNull() {
    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, null);
    assertNull(loader);
  }

  // --- Tool selection tests ---

  @Test
  void create_withUnsupportedTool_returnsNull() {
    config.getQualityGate().setCoverageTool("cobertura");

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNull(loader);
  }

  @Test
  void create_withJacocoTool_andNoReport_returnsNull() {
    config.getQualityGate().setCoverageTool("jacoco");

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNull(loader);
  }

  @Test
  void create_withJacocoToolUpperCase_andNoReport_returnsNull() {
    config.getQualityGate().setCoverageTool("JACOCO");

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNull(loader);
  }

  // --- Default path tests ---

  @Test
  void create_withDefaultPath_andReportExists_returnsAdapter() throws IOException {
    Path defaultReport = tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
    Files.createDirectories(defaultReport.getParent());
    Files.writeString(defaultReport, "<report></report>");

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNotNull(loader);
  }

  @Test
  void create_withDefaultPath_andReportNotExists_returnsNull() {
    // No report file created

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNull(loader);
  }

  // --- Configured path tests ---

  @Test
  void create_withConfiguredRelativePath_andReportExists_returnsAdapter() throws IOException {
    Path customReport = tempDir.resolve("custom/coverage.xml");
    Files.createDirectories(customReport.getParent());
    Files.writeString(customReport, "<report></report>");
    config.getQualityGate().setCoverageReportPath("custom/coverage.xml");

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNotNull(loader);
  }

  @Test
  void create_withConfiguredAbsolutePath_andReportExists_returnsAdapter() throws IOException {
    Path customReport = tempDir.resolve("absolute/coverage.xml");
    Files.createDirectories(customReport.getParent());
    Files.writeString(customReport, "<report></report>");
    config.getQualityGate().setCoverageReportPath(customReport.toAbsolutePath().toString());

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNotNull(loader);
  }

  @Test
  void create_withConfiguredPath_andReportNotExists_returnsNull() {
    config.getQualityGate().setCoverageReportPath("nonexistent/report.xml");

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNull(loader);
  }

  @Test
  void create_withConfiguredPathMissing_andDefaultExists_returnsNull() throws IOException {
    Path defaultReport = tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
    Files.createDirectories(defaultReport.getParent());
    Files.writeString(defaultReport, "<report></report>");
    config.getQualityGate().setCoverageReportPath("custom/missing.xml");

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNull(loader);
  }

  @Test
  void create_withConfiguredPathPrioritizedOverDefaultPath_usesConfiguredFile() throws IOException {
    Path defaultReport = tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
    Files.createDirectories(defaultReport.getParent());
    Files.writeString(defaultReport, "<report></report>");
    Path customReport = tempDir.resolve("custom/coverage.xml");
    Files.createDirectories(customReport.getParent());
    Files.writeString(customReport, "<invalid");
    config.getQualityGate().setCoverageReportPath("custom/coverage.xml");

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNotNull(loader);
    assertFalse(loader.isAvailable());
  }

  @Test
  void create_withNullQualityGateAndDefaultReport_returnsAdapter() throws IOException {
    Config noQualityGateConfig = new Config();
    Path defaultReport = tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
    Files.createDirectories(defaultReport.getParent());
    Files.writeString(defaultReport, "<report></report>");

    CoverageLoaderPort loader =
        CoverageLoaderAdapterFactory.createPort(tempDir, noQualityGateConfig);

    assertNotNull(loader);
  }

  @Test
  void create_withBlankConfiguredPath_andDefaultExists_returnsAdapter() throws IOException {
    Path defaultReport = tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
    Files.createDirectories(defaultReport.getParent());
    Files.writeString(defaultReport, "<report></report>");
    config.getQualityGate().setCoverageReportPath("   ");

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNotNull(loader);
  }

  @Test
  void createPort_withDefaultPathAndReportExists_returnsCoverageLoaderPort() throws IOException {
    Path defaultReport = tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
    Files.createDirectories(defaultReport.getParent());
    Files.writeString(defaultReport, "<report></report>");

    CoverageLoaderPort loader = CoverageLoaderAdapterFactory.createPort(tempDir, config);

    assertNotNull(loader);
    assertInstanceOf(JacocoCoverageAdapter.class, loader);
  }

  @Test
  void port_returnsFactoryContractSingleton() {
    CoverageLoaderFactoryPort first = CoverageLoaderAdapterFactory.port();
    CoverageLoaderFactoryPort second = CoverageLoaderAdapterFactory.port();

    assertSame(first, second);
    assertInstanceOf(CoverageLoaderAdapterFactory.class, first);
  }
}
