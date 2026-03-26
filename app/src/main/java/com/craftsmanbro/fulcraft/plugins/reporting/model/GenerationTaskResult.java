package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

/** Feature-layer neutral task generation result representation. */
public class GenerationTaskResult {

  public enum Status {
    SUCCESS,
    FAILURE,
    SKIPPED,
    PROCESSED
  }

  private String taskId;

  private String classFqn;

  private String methodName;

  private Status status;

  private String errorMessage;

  private Path generatedTestFilePath;

  private Integer expectedTestCount;

  private Integer actualTestCount;

  private Integer fixAttemptCount;

  private String errorCategory;

  private Integer staticFixCount;

  private Integer runtimeFixCount;

  private String complexityStrategy;

  private Boolean highComplexity;

  private GenerationResult generationResult;

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(final String taskId) {
    this.taskId = taskId;
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

  public String getStatus() {
    return status != null ? status.name() : null;
  }

  public Status getStatusEnum() {
    return status;
  }

  public void setStatus(final Status status) {
    this.status = status;
  }

  public void setStatus(final String status) {
    this.status = parseStatus(status);
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getGeneratedTestFile() {
    return generatedTestFilePath != null ? generatedTestFilePath.toString() : null;
  }

  public Path getGeneratedTestFilePath() {
    return generatedTestFilePath;
  }

  public void setGeneratedTestFile(final Path generatedTestFilePath) {
    this.generatedTestFilePath = generatedTestFilePath;
  }

  public void setGeneratedTestFile(final String generatedTestFile) {
    this.generatedTestFilePath = parsePath(generatedTestFile);
  }

  public Integer getExpectedTestCount() {
    return expectedTestCount;
  }

  public void setExpectedTestCount(final Integer expectedTestCount) {
    this.expectedTestCount = expectedTestCount;
  }

  public Integer getActualTestCount() {
    return actualTestCount;
  }

  public void setActualTestCount(final Integer actualTestCount) {
    this.actualTestCount = actualTestCount;
  }

  public Integer getFixAttemptCount() {
    return fixAttemptCount;
  }

  public void setFixAttemptCount(final Integer fixAttemptCount) {
    this.fixAttemptCount = fixAttemptCount;
  }

  public String getErrorCategory() {
    return errorCategory;
  }

  public void setErrorCategory(final String errorCategory) {
    this.errorCategory = errorCategory;
  }

  public Integer getStaticFixCount() {
    return staticFixCount;
  }

  public void setStaticFixCount(final Integer staticFixCount) {
    this.staticFixCount = staticFixCount;
  }

  public Integer getRuntimeFixCount() {
    return runtimeFixCount;
  }

  public void setRuntimeFixCount(final Integer runtimeFixCount) {
    this.runtimeFixCount = runtimeFixCount;
  }

  public String getComplexityStrategy() {
    return complexityStrategy;
  }

  public void setComplexityStrategy(final String complexityStrategy) {
    this.complexityStrategy = complexityStrategy;
  }

  public Boolean getHighComplexity() {
    return highComplexity;
  }

  public void setHighComplexity(final Boolean highComplexity) {
    this.highComplexity = highComplexity;
  }

  public GenerationResult getGenerationResult() {
    return generationResult;
  }

  public void setGenerationResult(final GenerationResult generationResult) {
    this.generationResult = generationResult;
  }

  private static Status parseStatus(final String value) {
    if (value == null) {
      return null;
    }
    final String normalized = value.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    return Status.valueOf(normalized.toUpperCase(Locale.ROOT));
  }

  private static Path parsePath(final String value) {
    if (value == null) {
      return null;
    }
    final String normalized = value.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    try {
      return Path.of(normalized);
    } catch (InvalidPathException e) {
      throw new IllegalArgumentException(
          MessageSource.getMessage("report.error.invalid_path", value), e);
    }
  }
}
