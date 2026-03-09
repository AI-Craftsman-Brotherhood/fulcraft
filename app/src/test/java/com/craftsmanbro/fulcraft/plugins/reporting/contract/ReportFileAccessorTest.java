package com.craftsmanbro.fulcraft.plugins.reporting.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportFileAccessorTest {

  @Test
  void methods_acceptArgumentsAndReturnValues() {
    Path projectRoot = Path.of("project");
    Path reportDir = Path.of("project", "reports");
    Path reportFile = reportDir.resolve("TEST-com.example.Foo.xml");
    ReportTaskResult result = new ReportTaskResult();

    ReportFileAccessor accessor =
        new ReportFileAccessor() {
          @Override
          public Path resolveReportDir(Path projectRootArg) {
            assertEquals(projectRoot, projectRootArg);
            return reportDir;
          }

          @Override
          public boolean isReportsDirectory(Path reportDirArg) {
            assertEquals(reportDir, reportDirArg);
            return true;
          }

          @Override
          public boolean hasAnyReportFile(Path reportDirArg) {
            assertEquals(reportDir, reportDirArg);
            return true;
          }

          @Override
          public Optional<Path> findReportFile(
              Path reportDirArg, String baseTestName, String testClassName) {
            assertEquals(reportDir, reportDirArg);
            assertEquals("com.example.Foo", baseTestName);
            assertEquals("Foo", testClassName);
            return Optional.of(reportFile);
          }

          @Override
          public boolean reportFileExists(Path reportFileArg) {
            assertEquals(reportFile, reportFileArg);
            return true;
          }

          @Override
          public boolean parseReport(Path reportFileArg, ReportTaskResult resultArg) {
            assertEquals(reportFile, reportFileArg);
            assertEquals(result, resultArg);
            return true;
          }
        };

    assertEquals(reportDir, accessor.resolveReportDir(projectRoot));
    assertTrue(accessor.isReportsDirectory(reportDir));
    assertTrue(accessor.hasAnyReportFile(reportDir));
    assertEquals(
        Optional.of(reportFile), accessor.findReportFile(reportDir, "com.example.Foo", "Foo"));
    assertTrue(accessor.reportFileExists(reportFile));
    assertTrue(accessor.parseReport(reportFile, result));
  }

  @Test
  void methods_allowMissingReportsAndParseFailure() {
    Path projectRoot = Path.of("project");
    Path reportDir = Path.of("project", "reports");
    Path reportFile = reportDir.resolve("TEST-com.example.Missing.xml");

    ReportFileAccessor accessor =
        new ReportFileAccessor() {
          @Override
          public Path resolveReportDir(Path projectRootArg) {
            assertEquals(projectRoot, projectRootArg);
            return reportDir;
          }

          @Override
          public boolean isReportsDirectory(Path reportDirArg) {
            assertEquals(reportDir, reportDirArg);
            return false;
          }

          @Override
          public boolean hasAnyReportFile(Path reportDirArg) {
            assertEquals(reportDir, reportDirArg);
            return false;
          }

          @Override
          public Optional<Path> findReportFile(
              Path reportDirArg, String baseTestName, String testClassName) {
            assertEquals(reportDir, reportDirArg);
            assertEquals("com.example.Missing", baseTestName);
            assertEquals("Missing", testClassName);
            return Optional.empty();
          }

          @Override
          public boolean reportFileExists(Path reportFileArg) {
            assertEquals(reportFile, reportFileArg);
            return false;
          }

          @Override
          public boolean parseReport(Path reportFileArg, ReportTaskResult resultArg) {
            assertEquals(reportFile, reportFileArg);
            return false;
          }
        };

    assertEquals(reportDir, accessor.resolveReportDir(projectRoot));
    assertFalse(accessor.isReportsDirectory(reportDir));
    assertFalse(accessor.hasAnyReportFile(reportDir));
    assertEquals(
        Optional.empty(), accessor.findReportFile(reportDir, "com.example.Missing", "Missing"));
    assertFalse(accessor.reportFileExists(reportFile));
    assertFalse(accessor.parseReport(reportFile, new ReportTaskResult()));
  }
}
