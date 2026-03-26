package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StaticAnalysisSummaryTest {

  @Test
  void setFindings_rebuildsSeverityCountsWhenCountsAreUnset() {
    StaticAnalysisSummary summary = new StaticAnalysisSummary();

    summary.setFindings(
        List.of(
            new StaticAnalysisSummary.Finding(
                "SpotBugs", StaticAnalysisSummary.SEVERITY_CRITICAL, "SB-1", "critical"),
            new StaticAnalysisSummary.Finding(
                "PMD", StaticAnalysisSummary.SEVERITY_INFO, "PMD-1", "informational")));

    assertThat(summary.getCriticalCount()).isEqualTo(1);
    assertThat(summary.getInfoCount()).isEqualTo(1);
    assertThat(summary.getTotalCount()).isEqualTo(2);
    assertThat(summary.hasCriticals()).isTrue();
    assertThat(summary.hasHighSeverity()).isTrue();
  }

  @Test
  void setFindings_preservesExplicitCountsWhenAlreadyProvided() {
    StaticAnalysisSummary summary = new StaticAnalysisSummary();
    summary.setMajorCount(3);
    summary.setInfoCount(1);

    summary.setFindings(
        List.of(
            new StaticAnalysisSummary.Finding(
                "Checkstyle", StaticAnalysisSummary.SEVERITY_BLOCKER, "CS-1", "blocker")));

    assertThat(summary.getMajorCount()).isEqualTo(3);
    assertThat(summary.getInfoCount()).isEqualTo(1);
    assertThat(summary.getBlockerCount()).isZero();
    assertThat(summary.getTotalCount()).isEqualTo(4);
  }
}
