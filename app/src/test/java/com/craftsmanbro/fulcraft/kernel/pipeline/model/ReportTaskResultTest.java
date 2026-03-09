package com.craftsmanbro.fulcraft.kernel.pipeline.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportTaskResultTest {

  @Test
  void shouldRoundTripCoreFieldsAndNestedModels() {
    ReportTaskResult result = new ReportTaskResult();
    ReportTaskResult.FailureDetail detail = new ReportTaskResult.FailureDetail();
    ReportTaskResult.Logs logs = new ReportTaskResult.Logs();

    result.setRunId("run-1");
    result.setAttempt(2);
    result.setTaskId("task-1");
    result.setProjectId("project-1");
    result.setClassFqn("com.example.Foo");
    result.setMethodName("doWork");
    result.setGeneratedTestClass("FooTest");
    result.setGeneratedTestFilePath("src/test/java/com/example/FooTest.java");
    result.setStatus("success");
    result.setBuildExitCode(0);
    result.setTestsRun(5);
    result.setTestsFailed(1);
    result.setTestsError(0);
    result.setTestsSkipped(2);

    detail.setTestMethod("shouldDoWork");
    detail.setMessageHead("Expected true but was false");
    result.addFailureDetail(detail);

    logs.setBuildAndTestLogPath("logs/build-and-test.log");
    result.setLogs(logs);

    assertThat(result.getRunId()).isEqualTo("run-1");
    assertThat(result.getAttempt()).isEqualTo(2);
    assertThat(result.getTaskId()).isEqualTo("task-1");
    assertThat(result.getProjectId()).isEqualTo("project-1");
    assertThat(result.getClassFqn()).isEqualTo("com.example.Foo");
    assertThat(result.getMethodName()).isEqualTo("doWork");
    assertThat(result.getGeneratedTestClass()).isEqualTo("FooTest");
    assertThat(result.getGeneratedTestFilePath())
        .isEqualTo("src/test/java/com/example/FooTest.java");
    assertThat(result.getStatus()).isEqualTo("success");
    assertThat(result.getBuildExitCode()).isEqualTo(0);
    assertThat(result.getTestsRun()).isEqualTo(5);
    assertThat(result.getTestsFailed()).isEqualTo(1);
    assertThat(result.getTestsError()).isEqualTo(0);
    assertThat(result.getTestsSkipped()).isEqualTo(2);
    assertThat(result.getFailureDetails()).containsExactly(detail);
    assertThat(result.getFailureDetails().get(0).getTestMethod()).isEqualTo("shouldDoWork");
    assertThat(result.getFailureDetails().get(0).getMessageHead())
        .isEqualTo("Expected true but was false");
    assertThat(result.getLogs()).isSameAs(logs);
    assertThat(result.getLogs().getBuildAndTestLogPath()).isEqualTo("logs/build-and-test.log");
  }

  @Test
  void shouldExposeFailureDetailsAsUnmodifiableList() {
    ReportTaskResult result = new ReportTaskResult();
    ReportTaskResult.FailureDetail detail = new ReportTaskResult.FailureDetail();
    result.addFailureDetail(detail);

    List<ReportTaskResult.FailureDetail> details = result.getFailureDetails();

    assertThat(details).containsExactly(detail);
    assertThatThrownBy(() -> details.add(new ReportTaskResult.FailureDetail()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldIgnoreNullFailureDetail() {
    ReportTaskResult result = new ReportTaskResult();

    result.addFailureDetail(null);

    assertThat(result.getFailureDetails()).isEmpty();
  }

  @Test
  void shouldUseEmptyFailureDetailsWhenNullListIsSet() {
    ReportTaskResult result = new ReportTaskResult();

    result.setFailureDetails(null);

    assertThat(result.getFailureDetails()).isEmpty();
  }

  @Test
  void shouldDefensivelyCopyProvidedFailureDetailsList() {
    ReportTaskResult result = new ReportTaskResult();
    List<ReportTaskResult.FailureDetail> details = new ArrayList<>();
    ReportTaskResult.FailureDetail detail = new ReportTaskResult.FailureDetail();

    result.setFailureDetails(details);
    details.add(detail);

    assertThat(result.getFailureDetails()).isEmpty();
  }
}
