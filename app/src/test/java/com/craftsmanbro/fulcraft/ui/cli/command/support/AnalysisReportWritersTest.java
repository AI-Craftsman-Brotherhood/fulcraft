package com.craftsmanbro.fulcraft.ui.cli.command.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.plugins.reporting.adapter.HtmlReportWriter;
import com.craftsmanbro.fulcraft.plugins.reporting.adapter.JsonReportWriter;
import com.craftsmanbro.fulcraft.plugins.reporting.adapter.MarkdownReportWriter;
import com.craftsmanbro.fulcraft.plugins.reporting.adapter.YamlReportWriter;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import java.nio.file.Path;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class AnalysisReportWritersTest {

  @Test
  void create_returnsWritersForAllFormats() {
    Path outputDir = Path.of("build/reports");
    EnumMap<ReportFormat, ReportWriterPort> writers =
        AnalysisReportWriters.create(outputDir, ReportFormat.MARKDOWN, "custom.md");

    assertThat(writers)
        .containsOnlyKeys(
            ReportFormat.MARKDOWN, ReportFormat.JSON, ReportFormat.HTML, ReportFormat.YAML);
    assertThat(writers.get(ReportFormat.MARKDOWN)).isInstanceOf(MarkdownReportWriter.class);
    assertThat(writers.get(ReportFormat.JSON)).isInstanceOf(JsonReportWriter.class);
    assertThat(writers.get(ReportFormat.HTML)).isInstanceOf(HtmlReportWriter.class);
    assertThat(writers.get(ReportFormat.YAML)).isInstanceOf(YamlReportWriter.class);
  }

  @Test
  void create_appliesFilenameOverride_onlyToPrimaryFormat() {
    EnumMap<ReportFormat, ReportWriterPort> writers =
        AnalysisReportWriters.create(
            Path.of("build/reports"), ReportFormat.JSON, "custom-summary.json");

    assertThat(readFilename(writers.get(ReportFormat.JSON))).isEqualTo("custom-summary.json");
    assertThat(readFilename(writers.get(ReportFormat.MARKDOWN))).isEqualTo("report.md");
    assertThat(readFilename(writers.get(ReportFormat.HTML))).isEqualTo("report.html");
    assertThat(readFilename(writers.get(ReportFormat.YAML))).isEqualTo("report.yaml");
  }

  @Test
  void create_usesDefaultFilenames_whenOverrideNotProvided() {
    EnumMap<ReportFormat, ReportWriterPort> writers =
        AnalysisReportWriters.create(Path.of("build/reports"), ReportFormat.MARKDOWN, null);

    assertThat(readFilename(writers.get(ReportFormat.MARKDOWN))).isEqualTo("report.md");
    assertThat(readFilename(writers.get(ReportFormat.JSON))).isEqualTo("summary.json");
    assertThat(readFilename(writers.get(ReportFormat.HTML))).isEqualTo("report.html");
    assertThat(readFilename(writers.get(ReportFormat.YAML))).isEqualTo("report.yaml");
  }

  @Test
  void create_requiresNonNullArguments() {
    assertThatThrownBy(() -> AnalysisReportWriters.create(null, ReportFormat.MARKDOWN, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> AnalysisReportWriters.create(Path.of("."), null, null))
        .isInstanceOf(NullPointerException.class);
  }

  private static String readFilename(ReportWriterPort writer) {
    try {
      var filenameField = writer.getClass().getDeclaredField("filename");
      filenameField.setAccessible(true);
      return (String) filenameField.get(writer);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Failed to read writer filename", e);
    }
  }
}
