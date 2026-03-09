package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedactionReportTest {

  @Test
  void fromFindings_countsTypesAndMlEntities() {
    List<Finding> findings =
        List.of(
            new Finding("EMAIL", 0, 3, 0.9, "a@b", "regex:email"),
            new Finding("CREDIT_CARD", 4, 8, 0.95, "4242", "regex:credit_card"),
            new Finding("DICTIONARY", 9, 12, 0.8, "term", "dictionary:deny.txt"),
            new Finding("PERSON_NAME", 13, 17, 0.7, "John", "ml:default:PERSON"),
            new Finding("LOCATION", 18, 22, 0.7, "Rome", "ml:default:LOC"));

    RedactionReport report = RedactionReport.fromFindings(findings);

    assertThat(report.emailCount()).isEqualTo(1);
    assertThat(report.creditCardCount()).isEqualTo(1);
    assertThat(report.dictionaryCount()).isEqualTo(1);
    assertThat(report.mlEntityCount()).isEqualTo(2);
    assertThat(report.totalCount()).isEqualTo(5);
  }

  @Test
  void merge_combinesCounts() {
    RedactionReport left = new RedactionReport(1, 0, 0, 0, 0);
    RedactionReport right = new RedactionReport(0, 2, 0, 1, 0);

    RedactionReport merged = left.merge(right);

    assertThat(merged.emailCount()).isEqualTo(1);
    assertThat(merged.creditCardCount()).isEqualTo(2);
    assertThat(merged.authTokenCount()).isEqualTo(1);
  }

  @Test
  void toString_whenClean_returnsCleanSummary() {
    assertThat(RedactionReport.EMPTY.toString()).isEqualTo("RedactionReport{clean}");
  }

  @Test
  void fromFindings_withNullOrEmpty_returnsEmptyReport() {
    assertThat(RedactionReport.fromFindings(null)).isSameAs(RedactionReport.EMPTY);
    assertThat(RedactionReport.fromFindings(List.of())).isSameAs(RedactionReport.EMPTY);
  }

  @Test
  void merge_withNull_returnsSameReport() {
    RedactionReport report = new RedactionReport(1, 2, 0, 0, 0);

    assertThat(report.merge(null)).isSameAs(report);
  }

  @Test
  void toString_includesOnlyPositiveCounts() {
    RedactionReport report = new RedactionReport(1, 0, 2, 0, 0, 0, 1);

    assertThat(report.toString())
        .contains("emails=1", "pemKeys=2", "mlEntities=1")
        .doesNotContain("creditCards", "authTokens", "jwt", "dictionary");
  }
}
