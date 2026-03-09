package com.craftsmanbro.fulcraft.kernel.pipeline.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an aggregated per-task outcome used by report generation.
 *
 * <p>This model carries parsed report details and status for each generated task.
 */
public class ReportTaskResult {

  @JsonProperty("run_id")
  private String runId;

  @JsonProperty("attempt")
  private int attempt;

  @JsonProperty("task_id")
  private String taskId;

  @JsonProperty("project_id")
  private String projectId;

  @JsonProperty("class_fqn")
  private String classFqn;

  @JsonProperty("method_name")
  private String methodName;

  @JsonProperty("generated_test_class")
  private String generatedTestClass;

  @JsonProperty("generated_test_file_path")
  private String generatedTestFilePath;

  @JsonProperty("status")
  private String // "success" | "test_failed" | "build_failed" | "test_not_found" |
      status;

  // "not_selected"
  @JsonProperty("build_exit_code")
  private int buildExitCode;

  @JsonProperty("tests_run")
  private int testsRun;

  @JsonProperty("tests_failed")
  private int testsFailed;

  @JsonProperty("tests_error")
  private int testsError;

  @JsonProperty("tests_skipped")
  private int testsSkipped;

  @JsonProperty("failure_details")
  private List<FailureDetail> failureDetails = new ArrayList<>();

  @JsonProperty("logs")
  private Logs logs;

  public String getRunId() {
    return runId;
  }

  public void setRunId(final String runId) {
    this.runId = runId;
  }

  public int getAttempt() {
    return attempt;
  }

  public void setAttempt(final int attempt) {
    this.attempt = attempt;
  }

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(final String taskId) {
    this.taskId = taskId;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(final String projectId) {
    this.projectId = projectId;
  }

  public String getClassFqn() {
    return classFqn;
  }

  public void setClassFqn(final String classFqn) {
    this.classFqn = classFqn;
  }

  public String getMethodName() {
    return methodName;
  }

  public void setMethodName(final String methodName) {
    this.methodName = methodName;
  }

  public String getGeneratedTestClass() {
    return generatedTestClass;
  }

  public void setGeneratedTestClass(final String generatedTestClass) {
    this.generatedTestClass = generatedTestClass;
  }

  public String getGeneratedTestFilePath() {
    return generatedTestFilePath;
  }

  public void setGeneratedTestFilePath(final String generatedTestFilePath) {
    this.generatedTestFilePath = generatedTestFilePath;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  public int getBuildExitCode() {
    return buildExitCode;
  }

  public void setBuildExitCode(final int buildExitCode) {
    this.buildExitCode = buildExitCode;
  }

  public int getTestsRun() {
    return testsRun;
  }

  public void setTestsRun(final int testsRun) {
    this.testsRun = testsRun;
  }

  public int getTestsFailed() {
    return testsFailed;
  }

  public void setTestsFailed(final int testsFailed) {
    this.testsFailed = testsFailed;
  }

  public int getTestsError() {
    return testsError;
  }

  public void setTestsError(final int testsError) {
    this.testsError = testsError;
  }

  public int getTestsSkipped() {
    return testsSkipped;
  }

  public void setTestsSkipped(final int testsSkipped) {
    this.testsSkipped = testsSkipped;
  }

  public List<FailureDetail> getFailureDetails() {
    return Collections.unmodifiableList(failureDetails);
  }

  public void addFailureDetail(final FailureDetail detail) {
    if (detail != null) {
      failureDetails.add(detail);
    }
  }

  public void setFailureDetails(final List<FailureDetail> failureDetails) {
    this.failureDetails = normalizeFailureDetails(failureDetails);
  }

  public Logs getLogs() {
    return logs;
  }

  public void setLogs(final Logs logs) {
    this.logs = logs;
  }

  private static List<FailureDetail> normalizeFailureDetails(
      final List<FailureDetail> failureDetails) {
    return failureDetails == null ? new ArrayList<>() : new ArrayList<>(failureDetails);
  }

  public static class FailureDetail {

    @JsonProperty("test_method")
    private String testMethod;

    @JsonProperty("message_head")
    private String messageHead;

    public String getTestMethod() {
      return testMethod;
    }

    public void setTestMethod(final String testMethod) {
      this.testMethod = testMethod;
    }

    public String getMessageHead() {
      return messageHead;
    }

    public void setMessageHead(final String messageHead) {
      this.messageHead = messageHead;
    }
  }

  public static class Logs {

    @JsonProperty("build_and_test_log_path")
    private String buildAndTestLogPath;

    public String getBuildAndTestLogPath() {
      return buildAndTestLogPath;
    }

    public void setBuildAndTestLogPath(final String buildAndTestLogPath) {
      this.buildAndTestLogPath = buildAndTestLogPath;
    }
  }
}
