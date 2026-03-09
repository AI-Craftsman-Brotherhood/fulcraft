package com.craftsmanbro.fulcraft.plugins.analysis.core.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.plugins.analysis.reporting.adapter.QualityReportGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link AnalysisReportService}. */
class AnalysisReportServiceTest {

  @TempDir Path tempDir;

  private QualityReportGenerator reportGenerator;
  private AnalysisReportService service;

  @BeforeEach
  void setUp() {
    reportGenerator = mock(QualityReportGenerator.class);
    service = new AnalysisReportService(reportGenerator);
  }

  @Test
  @DisplayName("generateQualityReport: Should return true when generator succeeds")
  void generateQualityReport_shouldReturnTrue_whenGeneratorSucceeds() throws IOException {
    when(reportGenerator.generate(any(Path.class))).thenReturn(true);

    boolean result = service.generateQualityReport(tempDir);

    assertThat(result).isTrue();
    verify(reportGenerator).generate(tempDir);
  }

  @Test
  @DisplayName("generateQualityReport: Should return false when generator returns false")
  void generateQualityReport_shouldReturnFalse_whenGeneratorFails() throws IOException {
    when(reportGenerator.generate(any(Path.class))).thenReturn(false);

    boolean result = service.generateQualityReport(tempDir);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("generateQualityReport: Should return false and handle IOException")
  void generateQualityReport_shouldReturnFalse_whenExceptionOccurs() throws IOException {
    when(reportGenerator.generate(any(Path.class))).thenThrow(new IOException("Fail"));

    boolean result = service.generateQualityReport(tempDir);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("generateQualityReport: Should throw NPE when analysisDir is null")
  void generateQualityReport_shouldThrowNPE_whenAnalysisDirIsNull() {
    assertThatNullPointerException().isThrownBy(() -> service.generateQualityReport(null));
  }

  @Test
  @DisplayName("generateAndGetReportPath: Should return path when report is generated and exists")
  void generateAndGetReportPath_shouldReturnPath_whenExists() throws IOException {
    when(reportGenerator.generate(any(Path.class))).thenReturn(true);
    Path reportFile = tempDir.resolve("quality_report.md");
    Files.createFile(reportFile);

    Path result = service.generateAndGetReportPath(tempDir);

    assertThat(result).isEqualTo(reportFile);
  }

  @Test
  @DisplayName("generateAndGetReportPath: Should return null when generator returns false")
  void generateAndGetReportPath_shouldReturnNull_whenGenerationFails() throws IOException {
    when(reportGenerator.generate(any(Path.class))).thenReturn(false);

    Path result = service.generateAndGetReportPath(tempDir);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName(
      "generateAndGetReportPath: Should return null when file is missing despite generation success")
  void generateAndGetReportPath_shouldReturnNull_whenFileMissing() throws IOException {
    when(reportGenerator.generate(any(Path.class))).thenReturn(true);
    // File not created

    Path result = service.generateAndGetReportPath(tempDir);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("generateAndGetReportPath: Should return null on IOException")
  void generateAndGetReportPath_shouldReturnNull_whenExceptionOccurs() throws IOException {
    when(reportGenerator.generate(any(Path.class))).thenThrow(new IOException("Fail"));

    Path result = service.generateAndGetReportPath(tempDir);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("generateAndGetReportPath: Should throw NPE when analysisDir is null")
  void generateAndGetReportPath_shouldThrowNPE_whenAnalysisDirIsNull() {
    assertThatNullPointerException().isThrownBy(() -> service.generateAndGetReportPath(null));
  }

  @Test
  @DisplayName("hasExistingReport: Should return true if markdown exists")
  void hasExistingReport_shouldReturnTrue_whenMarkdownExists() throws IOException {
    Files.createFile(tempDir.resolve("quality_report.md"));

    assertThat(service.hasExistingReport(tempDir)).isTrue();
  }

  @Test
  @DisplayName("hasExistingReport: Should return true if json exists")
  void hasExistingReport_shouldReturnTrue_whenJsonExists() throws IOException {
    Files.createFile(tempDir.resolve("quality_report.json"));

    assertThat(service.hasExistingReport(tempDir)).isTrue();
  }

  @Test
  @DisplayName("hasExistingReport: Should return false if neither exists")
  void hasExistingReport_shouldReturnFalse_whenNoneExists() {
    assertThat(service.hasExistingReport(tempDir)).isFalse();
  }

  @Test
  @DisplayName("getReportPath: Should return existing path if found")
  void getReportPath_shouldReturnExisting_whenFound() throws IOException {
    Path mdFile = tempDir.resolve("quality_report.md");
    Files.createFile(mdFile);

    assertThat(service.getReportPath(tempDir)).isEqualTo(mdFile);
  }

  @Test
  @DisplayName("getReportPath: Should prioritize Markdown over JSON")
  void getReportPath_shouldPrioritizeMarkdown() throws IOException {
    Path mdFile = tempDir.resolve("quality_report.md");
    Path jsonFile = tempDir.resolve("quality_report.json");
    Files.createFile(mdFile);
    Files.createFile(jsonFile);

    assertThat(service.getReportPath(tempDir)).isEqualTo(mdFile);
  }

  @Test
  @DisplayName("getReportPath: Should return JSON if Markdown missing")
  void getReportPath_shouldReturnJson_whenMarkdownMissing() throws IOException {
    Path jsonFile = tempDir.resolve("quality_report.json");
    Files.createFile(jsonFile);

    assertThat(service.getReportPath(tempDir)).isEqualTo(jsonFile);
  }

  @Test
  @DisplayName("getReportPath: Should return default path if not found")
  void getReportPath_shouldReturnDefault_whenNotFound() {
    Path defaultPath = tempDir.resolve("quality_report.md");

    assertThat(service.getReportPath(tempDir)).isEqualTo(defaultPath);
  }

  @Test
  @DisplayName("getReportPath: Should throw NPE when analysisDir is null")
  void getReportPath_shouldThrowNPE_whenAnalysisDirIsNull() {
    assertThatNullPointerException().isThrownBy(() -> service.getReportPath(null));
  }
}
