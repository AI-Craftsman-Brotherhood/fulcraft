package com.craftsmanbro.fulcraft.ui.tui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictCandidate;
import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictPolicy;
import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueHandlingOption;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionIssue;
import com.craftsmanbro.fulcraft.ui.tui.plan.Plan;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionContextTest {

  @Test
  void initialState_isEmptyAndNotReady() {
    ExecutionContext context = new ExecutionContext();

    assertThat(context.getPlan()).isEmpty();
    assertThat(context.getConflictPolicy()).isEmpty();
    assertThat(context.getConflictCandidates()).isEmpty();
    assertThat(context.isConflictScanDone()).isFalse();
    assertThat(context.isOverwriteConfirmed()).isFalse();
    assertThat(context.hasIssue()).isFalse();
    assertThat(context.getSelectedOption()).isEmpty();
    assertThat(context.isReadyForExecution()).isFalse();
  }

  @Test
  void setPlan_withDifferentPlan_resetsConflictScanAndConfirmation() {
    ExecutionContext context = new ExecutionContext();
    context.setPlan(plan("goal-1"));
    context.setConflictCandidates(List.of(ConflictCandidate.of("SampleTest.java")));
    context.setOverwriteConfirmed(true);

    context.setPlan(plan("goal-2"));

    assertThat(context.getConflictCandidates()).isEmpty();
    assertThat(context.isConflictScanDone()).isFalse();
    assertThat(context.isOverwriteConfirmed()).isFalse();
  }

  @Test
  void setPlan_withSamePlan_keepsConflictScanState() {
    ExecutionContext context = new ExecutionContext();
    Plan plan = plan("goal");
    context.setPlan(plan);
    context.setConflictCandidates(List.of(ConflictCandidate.of("SampleTest.java")));
    context.setOverwriteConfirmed(true);

    context.setPlan(plan);

    assertThat(context.getConflictCandidates()).hasSize(1);
    assertThat(context.isConflictScanDone()).isTrue();
    assertThat(context.isOverwriteConfirmed()).isTrue();
  }

  @Test
  void setConflictPolicy_nonOverwrite_resetsOverwriteConfirmation() {
    ExecutionContext context = new ExecutionContext();
    context.setConflictPolicy(ConflictPolicy.OVERWRITE);
    context.setOverwriteConfirmed(true);

    context.setConflictPolicy(ConflictPolicy.SAFE);

    assertThat(context.getConflictPolicy()).contains(ConflictPolicy.SAFE);
    assertThat(context.isOverwriteConfirmed()).isFalse();
  }

  @Test
  void setConflictCandidates_makesImmutableDefensiveCopyAndMarksScanDone() {
    ExecutionContext context = new ExecutionContext();
    List<ConflictCandidate> candidates = new ArrayList<>();
    candidates.add(ConflictCandidate.of("OneTest.java"));

    context.setConflictCandidates(candidates);
    candidates.add(ConflictCandidate.of("TwoTest.java"));

    assertThat(context.isConflictScanDone()).isTrue();
    assertThat(context.getConflictCount()).isEqualTo(1);
    assertThat(context.hasConflicts()).isTrue();
    assertThatThrownBy(
            () -> context.getConflictCandidates().add(ConflictCandidate.of("Other.java")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void setConflictCandidates_null_throws() {
    ExecutionContext context = new ExecutionContext();
    assertThatThrownBy(() -> context.setConflictCandidates(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("candidates");
  }

  @Test
  void isReadyForExecution_requiresPlanAndPolicy() {
    ExecutionContext context = new ExecutionContext();
    context.setConflictPolicy(ConflictPolicy.SAFE);
    assertThat(context.isReadyForExecution()).isFalse();

    context = new ExecutionContext();
    context.setPlan(plan("goal"));
    assertThat(context.isReadyForExecution()).isFalse();

    context.setConflictPolicy(ConflictPolicy.SAFE);
    assertThat(context.isReadyForExecution()).isTrue();
  }

  @Test
  void isReadyForExecution_overwriteWithConflictsRequiresConfirmation() {
    ExecutionContext context = new ExecutionContext();
    context.setPlan(plan("goal"));
    context.setConflictPolicy(ConflictPolicy.OVERWRITE);
    context.setConflictCandidates(List.of(ConflictCandidate.of("SampleTest.java")));

    assertThat(context.isReadyForExecution()).isFalse();

    context.setOverwriteConfirmed(true);
    assertThat(context.isReadyForExecution()).isTrue();
  }

  @Test
  void isReadyForExecution_overwriteWithoutConflictsIsReady() {
    ExecutionContext context = new ExecutionContext();
    context.setPlan(plan("goal"));
    context.setConflictPolicy(ConflictPolicy.OVERWRITE);

    assertThat(context.isReadyForExecution()).isTrue();
  }

  @Test
  void issueHandling_setCurrentIssueClearsSelectedOption_andClearIssueResetsBoth() {
    ExecutionContext context = new ExecutionContext();
    context.setSelectedOption(IssueHandlingOption.SKIP);

    ExecutionIssue issue = issue();
    context.setCurrentIssue(issue);
    assertThat(context.hasIssue()).isTrue();
    assertThat(context.getCurrentIssue()).contains(issue);
    assertThat(context.getSelectedOption()).isEmpty();

    context.setSelectedOption(IssueHandlingOption.PROPOSE_ONLY);
    context.clearIssue();
    assertThat(context.hasIssue()).isFalse();
    assertThat(context.getCurrentIssue()).isEmpty();
    assertThat(context.getSelectedOption()).isEmpty();
  }

  @Test
  void reset_clearsAllAccumulatedState() {
    ExecutionContext context = new ExecutionContext();
    context.setPlan(plan("goal"));
    context.setConflictPolicy(ConflictPolicy.OVERWRITE);
    context.setConflictCandidates(List.of(ConflictCandidate.of("SampleTest.java")));
    context.setOverwriteConfirmed(true);
    context.setCurrentIssue(issue());
    context.setSelectedOption(IssueHandlingOption.SAFE_FIX);

    context.reset();

    assertThat(context.getPlan()).isEmpty();
    assertThat(context.getConflictPolicy()).isEmpty();
    assertThat(context.getConflictCandidates()).isEmpty();
    assertThat(context.isConflictScanDone()).isFalse();
    assertThat(context.isOverwriteConfirmed()).isFalse();
    assertThat(context.getCurrentIssue()).isEmpty();
    assertThat(context.getSelectedOption()).isEmpty();
  }

  @Test
  void toString_includesStateSummary() {
    ExecutionContext context = new ExecutionContext();
    context.setConflictPolicy(ConflictPolicy.SAFE);
    context.setConflictCandidates(List.of(ConflictCandidate.of("SampleTest.java")));
    context.setCurrentIssue(issue());

    assertThat(context.toString())
        .contains("ExecutionContext")
        .contains("conflictPolicy=SAFE")
        .contains("conflicts=1")
        .contains("hasIssue=true");
  }

  private static Plan plan(String goal) {
    return new Plan(goal, List.of("step1"), "impact", List.of());
  }

  private static ExecutionIssue issue() {
    return ExecutionIssue.testFailure("MyClass", "RUN", "failed");
  }
}
