package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.io.ReportFileFinder;
import com.craftsmanbro.fulcraft.plugins.reporting.io.ReportParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultReportFileAccessorTest {

  @TempDir Path tempDir;

  @Test
  void isReportsDirectory_andReportFileExists_handleNullAndPaths() throws Exception {
    DefaultReportFileAccessor accessor = new DefaultReportFileAccessor();

    Path file = tempDir.resolve("report.txt");
    Files.writeString(file, "content");
    Path missing = tempDir.resolve("missing-report.xml");

    assertThat(accessor.isReportsDirectory(null)).isFalse();
    assertThat(accessor.isReportsDirectory(file)).isFalse();
    assertThat(accessor.isReportsDirectory(tempDir)).isTrue();
    assertThat(accessor.isReportsDirectory(missing)).isFalse();

    assertThat(accessor.reportFileExists(null)).isFalse();
    assertThat(accessor.reportFileExists(file)).isTrue();
    assertThat(accessor.reportFileExists(missing)).isFalse();
  }

  @Test
  void delegatesToFinderAndParser() {
    ReportFileFinder finder = mock(ReportFileFinder.class);
    ReportParser parser = mock(ReportParser.class);
    DefaultReportFileAccessor accessor = new DefaultReportFileAccessor(finder, parser);

    Path reportDir = tempDir.resolve("reports");
    Path reportFile = reportDir.resolve("TEST-com.example.Foo.xml");

    when(finder.resolveReportDir(tempDir)).thenReturn(reportDir);
    when(finder.hasAnyReportFile(reportDir)).thenReturn(true);
    when(finder.findReportFile(reportDir, "com.example.Foo", "Foo"))
        .thenReturn(Optional.of(reportFile));

    ReportTaskResult result = new ReportTaskResult();
    when(parser.parseReport(reportFile, result)).thenReturn(true);

    assertThat(accessor.resolveReportDir(tempDir)).isEqualTo(reportDir);
    assertThat(accessor.hasAnyReportFile(reportDir)).isTrue();
    assertThat(accessor.findReportFile(reportDir, "com.example.Foo", "Foo")).contains(reportFile);
    assertThat(accessor.parseReport(reportFile, result)).isTrue();

    verify(finder).resolveReportDir(tempDir);
    verify(finder).hasAnyReportFile(reportDir);
    verify(finder).findReportFile(reportDir, "com.example.Foo", "Foo");
    verify(parser).parseReport(reportFile, result);
  }
}
