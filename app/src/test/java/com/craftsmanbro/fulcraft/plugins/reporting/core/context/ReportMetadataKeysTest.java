package com.craftsmanbro.fulcraft.plugins.reporting.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportMetadataKeysTest {

  @Test
  void constants_shouldHaveCorrectValues() {
    assertThat(ReportMetadataKeys.TASKS_FILE_OVERRIDE).isEqualTo("report.tasksFile");
    assertThat(ReportMetadataKeys.OUTPUT_PATH_OVERRIDE).isEqualTo("report.outputPath");
    assertThat(ReportMetadataKeys.RUN_SUMMARY).isEqualTo("report.runSummary");
    assertThat(ReportMetadataKeys.REASON_SUMMARY).isEqualTo("report.reasonSummary");
    assertThat(ReportMetadataKeys.DYNAMIC_SELECTION_REPORT)
        .isEqualTo("report.dynamicSelectionReport");
    assertThat(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY).isEqualTo("report.analysisHumanSummary");
    assertThat(ReportMetadataKeys.REPORT_EXTENSIONS).isEqualTo("report.extensions");
  }
}
