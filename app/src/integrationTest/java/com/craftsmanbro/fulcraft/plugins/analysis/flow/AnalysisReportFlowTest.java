package com.craftsmanbro.fulcraft.plugins.analysis.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.plugins.analysis.core.service.report.AnalysisReportService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisReportFlowTest {

  @TempDir Path tempDir;

  @Test
  void generateQualityReport_delegatesToService() {
    AnalysisReportService service = mock(AnalysisReportService.class);
    AnalysisReportFlow flow = new AnalysisReportFlow(service);

    when(service.generateQualityReport(tempDir)).thenReturn(true);

    assertThat(flow.generateQualityReport(tempDir)).isTrue();
    verify(service).generateQualityReport(tempDir);
  }

  @Test
  void generateAndGetReportPath_delegatesToService() {
    AnalysisReportService service = mock(AnalysisReportService.class);
    AnalysisReportFlow flow = new AnalysisReportFlow(service);
    Path reportPath = tempDir.resolve("quality_report.md");

    when(service.generateAndGetReportPath(tempDir)).thenReturn(reportPath);

    assertThat(flow.generateAndGetReportPath(tempDir)).isEqualTo(reportPath);
    verify(service).generateAndGetReportPath(tempDir);
  }

  @Test
  void hasExistingReport_delegatesToService() {
    AnalysisReportService service = mock(AnalysisReportService.class);
    AnalysisReportFlow flow = new AnalysisReportFlow(service);

    when(service.hasExistingReport(tempDir)).thenReturn(true);

    assertThat(flow.hasExistingReport(tempDir)).isTrue();
    verify(service).hasExistingReport(tempDir);
  }

  @Test
  void getReportPath_delegatesToService() {
    AnalysisReportService service = mock(AnalysisReportService.class);
    AnalysisReportFlow flow = new AnalysisReportFlow(service);
    Path reportPath = tempDir.resolve("quality_report.md");

    when(service.getReportPath(tempDir)).thenReturn(reportPath);

    assertThat(flow.getReportPath(tempDir)).isSameAs(reportPath);
    verify(service).getReportPath(tempDir);
  }

  @Test
  void constructor_rejectsNullService() {
    assertThatThrownBy(() -> new AnalysisReportFlow(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void getReportPath_returnsDefaultMarkdownPath_whenNoReportExists() {
    AnalysisReportFlow flow = new AnalysisReportFlow();

    Path reportPath = flow.getReportPath(tempDir);

    assertThat(reportPath).isEqualTo(tempDir.resolve("quality_report.md"));
  }

  @Test
  void hasExistingReport_returnsFalse_whenNoReportExists() {
    AnalysisReportFlow flow = new AnalysisReportFlow();

    assertThat(flow.hasExistingReport(tempDir)).isFalse();
  }

  @Test
  void generateQualityReport_rejectsNullAnalysisDir_whenUsingDefaultConstructor() {
    AnalysisReportFlow flow = new AnalysisReportFlow();

    assertThatNullPointerException()
        .isThrownBy(() -> flow.generateQualityReport(null))
        .withMessageContaining("analysisDir");
  }
}
