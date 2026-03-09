package com.craftsmanbro.fulcraft.plugins.reporting.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.reporting.io.contract.CoverageReader;
import com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverageReportLoaderTest {

  @TempDir Path tempDir;

  @Test
  void loadCoverage_returnsNullWhenReportMissing() {
    CoverageReportLoader loader = new CoverageReportLoader();
    Config config = new Config();
    RunContext context = new RunContext(tempDir, config, "run-1");
    AtomicReference<Path> seenPath = new AtomicReference<>();
    CoverageReader reader =
        reportPath -> {
          seenPath.set(reportPath);
          return new CoverageSummary();
        };

    CoverageSummary summary = loader.loadCoverage(context, config, reader);

    assertNull(summary);
    assertNull(seenPath.get());
  }

  @Test
  void loadCoverage_usesConfiguredRelativePath() throws IOException {
    CoverageReportLoader loader = new CoverageReportLoader();
    Config config = new Config();
    Config.QualityGateConfig qualityGate = new Config.QualityGateConfig();
    qualityGate.setCoverageReportPath("custom/report.xml");
    config.setQualityGate(qualityGate);

    Path reportPath = tempDir.resolve("custom/report.xml");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, "<report/>");

    RunContext context = new RunContext(tempDir, config, "run-2");
    AtomicReference<Path> seenPath = new AtomicReference<>();
    CoverageSummary expected = new CoverageSummary();
    CoverageReader reader =
        path -> {
          seenPath.set(path);
          return expected;
        };

    CoverageSummary summary = loader.loadCoverage(context, config, reader);

    assertSame(expected, summary);
    assertEquals(reportPath, seenPath.get());
  }

  @Test
  void loadCoverage_usesDefaultJacocoPathWhenConfigDoesNotSpecifyPath() throws IOException {
    CoverageReportLoader loader = new CoverageReportLoader();
    Config config = new Config();
    Path reportPath = tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, "<report/>");

    RunContext context = new RunContext(tempDir, config, "run-default");
    AtomicReference<Path> seenPath = new AtomicReference<>();
    CoverageSummary expected = new CoverageSummary();
    CoverageReader reader =
        path -> {
          seenPath.set(path);
          return expected;
        };

    CoverageSummary summary = loader.loadCoverage(context, config, reader);

    assertSame(expected, summary);
    assertEquals(reportPath, seenPath.get());
  }

  @Test
  void loadCoverage_usesConfiguredAbsolutePath() throws IOException {
    CoverageReportLoader loader = new CoverageReportLoader();
    Config config = new Config();
    Config.QualityGateConfig qualityGate = new Config.QualityGateConfig();
    Path absoluteReport = tempDir.resolve("absolute-report.xml").toAbsolutePath();
    qualityGate.setCoverageReportPath(absoluteReport.toString());
    config.setQualityGate(qualityGate);
    Files.writeString(absoluteReport, "<report/>");

    RunContext context = new RunContext(tempDir, config, "run-abs");
    AtomicReference<Path> seenPath = new AtomicReference<>();
    CoverageSummary expected = new CoverageSummary();
    CoverageReader reader =
        path -> {
          seenPath.set(path);
          return expected;
        };

    CoverageSummary summary = loader.loadCoverage(context, config, reader);

    assertSame(expected, summary);
    assertEquals(absoluteReport, seenPath.get());
  }

  @Test
  void loadCoverage_returnsNullWhenReaderThrows() throws IOException {
    CoverageReportLoader loader = new CoverageReportLoader();
    Config config = new Config();
    Config.QualityGateConfig qualityGate = new Config.QualityGateConfig();
    qualityGate.setCoverageReportPath("coverage/report.xml");
    config.setQualityGate(qualityGate);

    Path reportPath = tempDir.resolve("coverage/report.xml");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, "<report/>");

    RunContext context = new RunContext(tempDir, config, "run-3");
    CoverageReader reader =
        path -> {
          throw new IOException("boom");
        };

    CoverageSummary summary = loader.loadCoverage(context, config, reader);

    assertNull(summary);
  }

  @Test
  void loadCoverage_requiresNonNullArguments() {
    CoverageReportLoader loader = new CoverageReportLoader();
    Config config = new Config();
    RunContext context = new RunContext(tempDir, config, "run-non-null");
    CoverageReader reader = path -> new CoverageSummary();

    assertThrows(NullPointerException.class, () -> loader.loadCoverage(null, config, reader));
    assertThrows(NullPointerException.class, () -> loader.loadCoverage(context, null, reader));
    assertThrows(NullPointerException.class, () -> loader.loadCoverage(context, config, null));
  }
}
