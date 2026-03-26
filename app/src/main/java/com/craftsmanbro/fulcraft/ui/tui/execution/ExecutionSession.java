package com.craftsmanbro.fulcraft.ui.tui.execution;

import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueHandlingOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the execution state, progress, and cancellation control for TUI pipeline execution.
 *
 * <p>This class is thread-safe and coordinates between the execution thread and the TUI event loop.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Track current execution stage and progress
 *   <li>Collect log messages for display in TUI
 *   <li>Handle cancel requests
 *   <li>Store partial results for resumption
 * </ul>
 */
public class ExecutionSession {

  /** Execution status. */
  public enum Status {

    /** Ready to start execution. */
    READY,
    /** Execution is running. */
    RUNNING,
    /** Execution was cancelled by user. */
    CANCELLED,
    /** Execution completed successfully. */
    COMPLETED,
    /** Execution failed with error. */
    FAILED
  }

  // Maximum log lines to retain (circular buffer behavior)
  private static final int MAX_LOG_LINES = 500;

  private final AtomicReference<Status> status = new AtomicReference<>(Status.READY);

  private final AtomicReference<String> currentStage = new AtomicReference<>("");

  private final AtomicReference<String> currentProgress = new AtomicReference<>("");

  private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

  private final Object issueLock = new Object();

  private final AtomicReference<ExecutionIssue> pendingIssue = new AtomicReference<>();

  private final AtomicReference<IssueHandlingOption> issueDecision = new AtomicReference<>();

  // Thread-safe log collection
  private final List<String> logLines = new CopyOnWriteArrayList<>();

  // Partial results for resumption
  private final AtomicInteger completedStages = new AtomicInteger(0);

  private final AtomicInteger totalStages = new AtomicInteger(0);

  // Error message if failed
  private volatile String errorMessage;

  // Generated files output
  private final List<String> generatedFiles = new CopyOnWriteArrayList<>();

  /** Creates a new execution session in READY state. */

  // ========== Status Management ==========
  /**
   * Returns the current execution status.
   *
   * @return the current status
   */
  public Status getStatus() {
    return status.get();
  }

  /**
   * Transitions to a new status.
   *
   * @param newStatus the new status
   */
  public void setStatus(final Status newStatus) {
    Objects.requireNonNull(newStatus, "newStatus must not be null");
    status.set(newStatus);
  }

  /**
   * Returns true if execution is currently running.
   *
   * @return true if running
   */
  public boolean isRunning() {
    return status.get() == Status.RUNNING;
  }

  /**
   * Returns true if execution has finished (completed, cancelled, or failed).
   *
   * @return true if finished
   */
  public boolean isFinished() {
    final Status s = status.get();
    return s == Status.COMPLETED || s == Status.CANCELLED || s == Status.FAILED;
  }

  // ========== Stage/Progress Tracking ==========
  /**
   * Sets the current stage name for display.
   *
   * @param stageName the stage name (e.g., "ANALYZE", "SELECT")
   */
  public void setCurrentStage(final String stageName) {
    currentStage.set(stageName != null ? stageName : "");
  }

  /**
   * Returns the current stage name.
   *
   * @return the stage name
   */
  public String getCurrentStage() {
    return currentStage.get();
  }

  /**
   * Sets a progress message (e.g., "Processing 5/10 files").
   *
   * @param progress the progress message
   */
  public void setProgress(final String progress) {
    currentProgress.set(progress != null ? progress : "");
  }

  /**
   * Returns the current progress message.
   *
   * @return the progress message
   */
  public String getProgress() {
    return currentProgress.get();
  }

  /**
   * Sets the total number of stages.
   *
   * @param total total stages
   */
  public void setTotalStages(final int total) {
    this.totalStages.set(total);
  }

  /**
   * Returns the total number of stages.
   *
   * @return total stages
   */
  public int getTotalStages() {
    return totalStages.get();
  }

  /** Increments the completed stages counter. */
  public void incrementCompletedStages() {
    completedStages.incrementAndGet();
  }

  /**
   * Returns the number of completed stages.
   *
   * @return completed stages
   */
  public int getCompletedStages() {
    return completedStages.get();
  }

  // ========== Log Collection ==========
  /**
   * Appends a log line.
   *
   * @param line the log line to append
   */
  public void appendLog(final String line) {
    if (line != null) {
      logLines.add(line);
      // Trim if exceeds max
      while (logLines.size() > MAX_LOG_LINES) {
        logLines.remove(0);
      }
    }
  }

  /**
   * Returns all log lines.
   *
   * @return immutable list of log lines
   */
  public List<String> getLogLines() {
    return List.copyOf(logLines);
  }

  /**
   * Returns the last N log lines.
   *
   * @param n number of lines to return
   * @return list of last N lines
   */
  public List<String> getLastLogLines(final int n) {
    final int size = logLines.size();
    if (n >= size) {
      return List.copyOf(logLines);
    }
    return List.copyOf(logLines.subList(size - n, size));
  }

  /** Clears all log lines. */
  public void clearLogs() {
    logLines.clear();
  }

  // ========== Cancel Control ==========
  /** Requests cancellation of the execution. */
  public void requestCancel() {
    cancelRequested.set(true);
  }

  /**
   * Returns true if cancellation was requested.
   *
   * @return true if cancel requested
   */
  public boolean isCancelRequested() {
    return cancelRequested.get();
  }

  // ========== Issue Handling ==========
  /**
   * Registers a pending issue and clears any previous decision.
   *
   * @param issue the issue requiring a user decision
   */
  public void requestIssueHandling(final ExecutionIssue issue) {
    Objects.requireNonNull(issue, "issue must not be null");
    synchronized (issueLock) {
      pendingIssue.set(issue);
      issueDecision.set(null);
      issueLock.notifyAll();
    }
  }

  /**
   * Returns the currently pending issue, if any.
   *
   * @return optional containing the pending issue
   */
  public Optional<ExecutionIssue> getPendingIssue() {
    return Optional.ofNullable(pendingIssue.get());
  }

  /**
   * Blocks until a decision is provided for the current issue.
   *
   * @return the selected issue handling option, or null if none was provided
   * @throws InterruptedException if interrupted while waiting
   */
  public IssueHandlingOption awaitIssueDecision() throws InterruptedException {
    synchronized (issueLock) {
      while (pendingIssue.get() != null && issueDecision.get() == null) {
        issueLock.wait();
      }
      return issueDecision.get();
    }
  }

  /**
   * Records the user's decision and clears the pending issue.
   *
   * @param option the selected handling option
   */
  public void resolveIssue(final IssueHandlingOption option) {
    Objects.requireNonNull(option, "option must not be null");
    synchronized (issueLock) {
      issueDecision.set(option);
      pendingIssue.set(null);
      issueLock.notifyAll();
    }
  }

  // ========== Error Handling ==========
  /**
   * Sets the error message when execution fails.
   *
   * @param message the error message
   */
  public void setErrorMessage(final String message) {
    this.errorMessage = message;
  }

  /**
   * Returns the error message.
   *
   * @return the error message, or null if no error
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  // ========== Generated Files ==========
  /**
   * Adds a generated file path.
   *
   * @param filePath the file path
   */
  public void addGeneratedFile(final String filePath) {
    if (filePath != null) {
      generatedFiles.add(filePath);
    }
  }

  /**
   * Returns all generated file paths.
   *
   * @return list of generated files
   */
  public List<String> getGeneratedFiles() {
    return List.copyOf(generatedFiles);
  }

  // ========== Reset ==========
  /** Resets the session to initial state. */
  public void reset() {
    status.set(Status.READY);
    currentStage.set("");
    currentProgress.set("");
    cancelRequested.set(false);
    logLines.clear();
    completedStages.set(0);
    totalStages.set(0);
    errorMessage = null;
    generatedFiles.clear();
    clearIssueState();
  }

  @Override
  public String toString() {
    return String.format(
        "ExecutionSession{status=%s, stage=%s, progress=%s, logs=%d, completed=%d/%d}",
        status.get(),
        currentStage.get(),
        currentProgress.get(),
        logLines.size(),
        completedStages.get(),
        totalStages.get());
  }

  private void clearIssueState() {
    synchronized (issueLock) {
      pendingIssue.set(null);
      issueDecision.set(null);
      issueLock.notifyAll();
    }
  }
}
