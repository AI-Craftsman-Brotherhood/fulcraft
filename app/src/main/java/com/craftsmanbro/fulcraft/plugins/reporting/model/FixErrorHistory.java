package com.craftsmanbro.fulcraft.plugins.reporting.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Feature-layer neutral fix history representation. */
public class FixErrorHistory {

  private String taskId;

  private boolean converged;

  private List<FixAttempt> attempts = new ArrayList<>();

  public static class FixAttempt {

    private int attemptNumber;

    private String errorCategory;

    private String errorMessage;

    private long timestampMs;

    public FixAttempt() {}

    public FixAttempt(
        final int attemptNumber, final String errorCategory, final String errorMessage) {
      this.attemptNumber = attemptNumber;
      this.errorCategory = errorCategory;
      this.errorMessage = errorMessage;
      this.timestampMs = System.currentTimeMillis();
    }

    public int getAttemptNumber() {
      return attemptNumber;
    }

    public void setAttemptNumber(final int attemptNumber) {
      this.attemptNumber = attemptNumber;
    }

    public String getErrorCategory() {
      return errorCategory;
    }

    public void setErrorCategory(final String errorCategory) {
      this.errorCategory = errorCategory;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public long getTimestampMs() {
      return timestampMs;
    }

    public void setTimestampMs(final long timestampMs) {
      this.timestampMs = timestampMs;
    }
  }

  public FixErrorHistory() {}

  public FixErrorHistory(final String taskId) {
    this.taskId =
        Objects.requireNonNull(
            taskId,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "taskId"));
    this.converged = false;
  }

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(final String taskId) {
    this.taskId = taskId;
  }

  public boolean isConverged() {
    return converged;
  }

  public void setConverged(final boolean converged) {
    this.converged = converged;
  }

  public List<FixAttempt> getAttempts() {
    return List.copyOf(attempts);
  }

  public void setAttempts(final List<FixAttempt> attempts) {
    this.attempts =
        new ArrayList<>(
            Objects.requireNonNull(
                attempts,
                com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                    "report.common.error.argument_null", "attempts")));
  }

  public void addAttempt(final String errorCategory, final String errorMessage) {
    final int attemptNumber = attempts.size() + 1;
    attempts.add(new FixAttempt(attemptNumber, errorCategory, errorMessage));
  }

  public void markConverged() {
    this.converged = true;
  }
}
