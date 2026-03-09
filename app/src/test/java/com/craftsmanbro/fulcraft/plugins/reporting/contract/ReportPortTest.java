package com.craftsmanbro.fulcraft.plugins.reporting.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ReportPortTest {

  @Test
  void methods_acceptArgumentsAndReturnValues() throws ReportWriteException {
    AtomicBoolean generateReportsCalled = new AtomicBoolean(false);
    AtomicReference<ReportFormat> lastFormat = new AtomicReference<>();
    Path outputDirectory = Path.of("reports");

    ReportPort port =
        new ReportPort() {
          @Override
          public void generateReports() {
            generateReportsCalled.set(true);
          }

          @Override
          public void generateReport(ReportFormat format) {
            lastFormat.set(format);
          }

          @Override
          public Path getOutputDirectory() {
            return outputDirectory;
          }
        };

    port.generateReports();
    port.generateReport(ReportFormat.HTML);

    assertTrue(generateReportsCalled.get());
    assertEquals(ReportFormat.HTML, lastFormat.get());
    assertEquals(outputDirectory, port.getOutputDirectory());
  }

  @Test
  void methods_propagateReportWriteException() {
    ReportWriteException expected = new ReportWriteException("report failed");

    ReportPort port =
        new ReportPort() {
          @Override
          public void generateReports() throws ReportWriteException {
            throw expected;
          }

          @Override
          public void generateReport(ReportFormat format) throws ReportWriteException {
            throw expected;
          }

          @Override
          public Path getOutputDirectory() {
            return Path.of("reports");
          }
        };

    ReportWriteException fromGenerateReports =
        assertThrows(ReportWriteException.class, port::generateReports);
    ReportWriteException fromGenerateReport =
        assertThrows(ReportWriteException.class, () -> port.generateReport(ReportFormat.JSON));

    assertSame(expected, fromGenerateReports);
    assertSame(expected, fromGenerateReport);
  }
}
