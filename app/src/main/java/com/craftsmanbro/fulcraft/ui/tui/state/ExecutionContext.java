package com.craftsmanbro.fulcraft.ui.tui.state;

import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictCandidate;
import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictPolicy;
import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueHandlingOption;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionIssue;
import com.craftsmanbro.fulcraft.ui.tui.plan.Plan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Execution context that carries state between TUI screens.
 *
 * <p>This class holds all the context needed for test generation execution, accumulated as the user
 * progresses through the TUI workflow:
 *
 * <ul>
 *   <li>{@code plan} - The approved test generation plan
 *   <li>{@code conflictPolicy} - How to handle file conflicts
 *   <li>{@code conflictCandidates} - Files that would conflict
 *   <li>{@code currentIssue} - Current issue requiring user decision
 *   <li>{@code selectedOption} - User's selected handling option for current issue
 * </ul>
 *
 * <p>The context is mutable and updated as users make decisions in each state. It is passed from
 * state to state to maintain workflow continuity.
 */
public class ExecutionContext {

  private Plan plan;

  private ConflictPolicy conflictPolicy;

  private List<ConflictCandidate> conflictCandidates;

  private boolean overwriteConfirmed;

  private boolean conflictScanDone;

  // Issue handling state (UI-thread only)
  private ExecutionIssue currentIssue;

  private IssueHandlingOption selectedOption;

  /** Creates an empty ExecutionContext. */
  public ExecutionContext() {
    resetConflictScan();
    clearIssueState();
  }

  // ========== Plan ==========
  /**
   * Returns the current plan.
   *
   * @return optional containing the plan, or empty if not set
   */
  public Optional<Plan> getPlan() {
    return Optional.ofNullable(plan);
  }

  /**
   * Sets the plan.
   *
   * @param plan the plan to set
   */
  public void setPlan(final Plan plan) {
    if (!Objects.equals(this.plan, plan)) {
      resetConflictScan();
    }
    this.plan = plan;
  }

  // ========== Conflict Policy ==========
  /**
   * Returns the selected conflict policy.
   *
   * @return optional containing the policy, or empty if not set
   */
  public Optional<ConflictPolicy> getConflictPolicy() {
    return Optional.ofNullable(conflictPolicy);
  }

  /**
   * Sets the conflict policy.
   *
   * @param policy the policy to set
   */
  public void setConflictPolicy(final ConflictPolicy policy) {
    this.conflictPolicy = policy;
    // Reset overwrite confirmation when policy changes
    if (policy != ConflictPolicy.OVERWRITE) {
      this.overwriteConfirmed = false;
    }
  }

  // ========== Conflict Candidates ==========
  /**
   * Returns the list of conflict candidates.
   *
   * @return immutable list of conflict candidates
   */
  public List<ConflictCandidate> getConflictCandidates() {
    return conflictCandidates;
  }

  /**
   * Sets the list of conflict candidates.
   *
   * @param candidates the candidates to set (defensive copy made)
   */
  public void setConflictCandidates(final List<ConflictCandidate> candidates) {
    Objects.requireNonNull(candidates, "candidates must not be null");
    this.conflictCandidates = List.copyOf(candidates);
    this.conflictScanDone = true;
  }

  private void resetConflictScan() {
    this.conflictCandidates = List.of();
    this.conflictScanDone = false;
    this.overwriteConfirmed = false;
  }

  /**
   * Returns true if conflict detection has already been performed.
   *
   * @return true if conflict detection ran
   */
  public boolean isConflictScanDone() {
    return conflictScanDone;
  }

  /**
   * Returns the number of conflict candidates.
   *
   * @return the conflict count
   */
  public int getConflictCount() {
    return conflictCandidates.size();
  }

  /**
   * Returns true if there are any conflicts.
   *
   * @return true if conflicts exist
   */
  public boolean hasConflicts() {
    return !conflictCandidates.isEmpty();
  }

  // ========== Overwrite Confirmation ==========
  /**
   * Returns true if overwrite has been confirmed by the user.
   *
   * <p>This is only relevant when {@link ConflictPolicy#OVERWRITE} is selected. Two-step
   * confirmation is required for overwrite operations.
   *
   * @return true if overwrite is confirmed
   */
  public boolean isOverwriteConfirmed() {
    return overwriteConfirmed;
  }

  /**
   * Sets the overwrite confirmation status.
   *
   * @param confirmed true if the user confirmed overwrite
   */
  public void setOverwriteConfirmed(final boolean confirmed) {
    this.overwriteConfirmed = confirmed;
  }

  /**
   * Returns true if the context is ready for execution.
   *
   * <p>Ready means:
   *
   * <ul>
   *   <li>A plan is set
   *   <li>A conflict policy is selected
   *   <li>If OVERWRITE is selected, confirmation is required
   * </ul>
   *
   * @return true if ready for execution
   */
  public boolean isReadyForExecution() {
    if (plan == null || conflictPolicy == null) {
      return false;
    }
    if (conflictPolicy == ConflictPolicy.OVERWRITE && hasConflicts()) {
      return overwriteConfirmed;
    }
    return true;
  }

  // ========== Issue Handling ==========
  /**
   * Returns the current issue requiring user decision.
   *
   * @return optional containing the current issue, or empty if none
   */
  public Optional<ExecutionIssue> getCurrentIssue() {
    return Optional.ofNullable(currentIssue);
  }

  /**
   * Sets the current issue to be handled.
   *
   * <p>This will also clear any previously selected option.
   *
   * @param issue the issue to set
   */
  public void setCurrentIssue(final ExecutionIssue issue) {
    clearIssueState();
    this.currentIssue = issue;
  }

  /**
   * Returns the user's selected handling option for the current issue.
   *
   * @return optional containing the selected option, or empty if not selected
   */
  public Optional<IssueHandlingOption> getSelectedOption() {
    return Optional.ofNullable(selectedOption);
  }

  /**
   * Sets the selected handling option.
   *
   * @param option the option selected by the user
   */
  public void setSelectedOption(final IssueHandlingOption option) {
    this.selectedOption = option;
  }

  /**
   * Returns true if there is a current issue requiring handling.
   *
   * @return true if an issue is present
   */
  public boolean hasIssue() {
    return currentIssue != null;
  }

  /**
   * Clears the current issue and selected option.
   *
   * <p>Call this after the issue has been handled and execution should continue.
   */
  public void clearIssue() {
    clearIssueState();
  }

  private void clearIssueState() {
    this.selectedOption = null;
    this.currentIssue = null;
  }

  /** Resets the context to initial state. */
  public void reset() {
    this.plan = null;
    this.conflictPolicy = null;
    resetConflictScan();
    clearIssueState();
  }

  @Override
  public String toString() {
    return String.format(
        "ExecutionContext{plan=%s, conflictPolicy=%s, conflicts=%d, overwriteConfirmed=%s, hasIssue=%s}",
        plan != null ? "present" : "null",
        conflictPolicy,
        conflictCandidates.size(),
        overwriteConfirmed,
        currentIssue != null);
  }
}
