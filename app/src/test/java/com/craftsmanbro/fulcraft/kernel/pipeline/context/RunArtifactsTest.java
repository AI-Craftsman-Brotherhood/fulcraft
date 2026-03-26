package com.craftsmanbro.fulcraft.kernel.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunArtifactsTest {

  @Test
  void shouldReturnEmptyImmutableResultsByDefault() {
    RunArtifacts artifacts = new RunArtifacts();

    List<ReportTaskResult> snapshot = artifacts.getReportTaskResults();

    assertThat(snapshot).isEmpty();
    assertThatThrownBy(() -> snapshot.add(new ReportTaskResult()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldReturnCopyOfTestResults() {
    RunArtifacts artifacts = new RunArtifacts();
    ReportTaskResult result = new ReportTaskResult();
    List<ReportTaskResult> input = new ArrayList<>();
    input.add(result);

    artifacts.setReportTaskResults(input);
    input.clear();

    List<ReportTaskResult> snapshot = artifacts.getReportTaskResults();
    assertThat(snapshot).containsExactly(result);
    assertThatThrownBy(() -> snapshot.add(new ReportTaskResult()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldRejectNullElementsInResults() {
    RunArtifacts artifacts = new RunArtifacts();
    List<ReportTaskResult> input = new ArrayList<>();
    input.add(null);

    assertThatThrownBy(() -> artifacts.setReportTaskResults(input))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("reportTaskResults[0]");
  }

  @Test
  void shouldRejectNullResultsList() {
    RunArtifacts artifacts = new RunArtifacts();

    assertThatThrownBy(() -> artifacts.setReportTaskResults(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("reportTaskResults");
  }

  @Test
  void shouldTrackBrittlenessFlag() {
    RunArtifacts artifacts = new RunArtifacts();

    assertThat(artifacts.isBrittlenessDetected()).isFalse();
    artifacts.setBrittlenessDetected(true);
    assertThat(artifacts.isBrittlenessDetected()).isTrue();
  }
}
