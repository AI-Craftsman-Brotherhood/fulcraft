package com.craftsmanbro.fulcraft.ui.tui;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictCandidate;
import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictPolicy;
import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueHandlingOption;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionIssue;
import com.craftsmanbro.fulcraft.ui.tui.plan.Plan;
import com.craftsmanbro.fulcraft.ui.tui.state.ExecutionContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link ExecutionContext}. */
class ExecutionContextTest {

  @Nested
  @DisplayName("Initial state")
  class InitialStateTests {

    @Test
    @DisplayName("Should have empty plan initially")
    void shouldHaveEmptyPlan() {
      ExecutionContext ctx = new ExecutionContext();

      assertTrue(ctx.getPlan().isEmpty());
    }

    @Test
    @DisplayName("Should have empty conflict policy initially")
    void shouldHaveEmptyConflictPolicy() {
      ExecutionContext ctx = new ExecutionContext();

      assertTrue(ctx.getConflictPolicy().isEmpty());
    }

    @Test
    @DisplayName("Should have empty conflicts initially")
    void shouldHaveEmptyConflicts() {
      ExecutionContext ctx = new ExecutionContext();

      assertTrue(ctx.getConflictCandidates().isEmpty());
      assertFalse(ctx.hasConflicts());
      assertEquals(0, ctx.getConflictCount());
    }

    @Test
    @DisplayName("Should not have overwrite confirmed initially")
    void shouldNotHaveOverwriteConfirmed() {
      ExecutionContext ctx = new ExecutionContext();

      assertFalse(ctx.isOverwriteConfirmed());
    }
  }

  @Nested
  @DisplayName("Conflict policy management")
  class ConflictPolicyTests {

    @Test
    @DisplayName("Should set and get conflict policy")
    void shouldSetAndGetPolicy() {
      ExecutionContext ctx = new ExecutionContext();

      ctx.setConflictPolicy(ConflictPolicy.SAFE);

      assertTrue(ctx.getConflictPolicy().isPresent());
      assertEquals(ConflictPolicy.SAFE, ctx.getConflictPolicy().get());
    }

    @Test
    @DisplayName("Setting non-OVERWRITE policy should reset overwrite confirmation")
    void settingNonOverwriteShouldResetConfirmation() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setConflictPolicy(ConflictPolicy.OVERWRITE);
      ctx.setOverwriteConfirmed(true);

      ctx.setConflictPolicy(ConflictPolicy.SKIP);

      assertFalse(ctx.isOverwriteConfirmed());
    }

    @Test
    @DisplayName("Setting OVERWRITE policy should keep confirmation flag")
    void settingOverwriteShouldKeepConfirmation() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setOverwriteConfirmed(true);

      ctx.setConflictPolicy(ConflictPolicy.OVERWRITE);

      assertTrue(ctx.isOverwriteConfirmed());
    }
  }

  @Nested
  @DisplayName("Conflict candidates management")
  class ConflictCandidatesTests {

    @Test
    @DisplayName("Should set and get conflict candidates")
    void shouldSetAndGetCandidates() {
      ExecutionContext ctx = new ExecutionContext();
      List<ConflictCandidate> candidates =
          List.of(ConflictCandidate.of("FooTest.java"), ConflictCandidate.of("BarTest.java"));

      ctx.setConflictCandidates(candidates);

      assertEquals(2, ctx.getConflictCount());
      assertTrue(ctx.hasConflicts());
      assertEquals(candidates, ctx.getConflictCandidates());
    }

    @Test
    @DisplayName("Should make defensive copy of candidates")
    void shouldMakeDefensiveCopy() {
      ExecutionContext ctx = new ExecutionContext();
      List<ConflictCandidate> candidates = List.of(ConflictCandidate.of("Test.java"));

      ctx.setConflictCandidates(candidates);

      // Should be immutable
      assertThrows(
          UnsupportedOperationException.class,
          () -> ctx.getConflictCandidates().add(ConflictCandidate.of("Other.java")));
    }

    @Test
    @DisplayName("Should throw on null candidates")
    void shouldThrowOnNullCandidates() {
      ExecutionContext ctx = new ExecutionContext();

      assertThrows(NullPointerException.class, () -> ctx.setConflictCandidates(null));
    }
  }

  @Nested
  @DisplayName("Conflict scan tracking")
  class ConflictScanTests {

    @Test
    @DisplayName("Should not mark conflict scan done initially")
    void shouldNotMarkConflictScanDoneInitially() {
      ExecutionContext ctx = new ExecutionContext();

      assertFalse(ctx.isConflictScanDone());
    }

    @Test
    @DisplayName("Setting conflict candidates should mark scan as done")
    void settingCandidatesMarksScanDone() {
      ExecutionContext ctx = new ExecutionContext();

      ctx.setConflictCandidates(List.of(ConflictCandidate.of("Test.java")));

      assertTrue(ctx.isConflictScanDone());
    }

    @Test
    @DisplayName("Setting a different plan should reset conflict scan state")
    void settingDifferentPlanResetsConflictScan() {
      ExecutionContext ctx = new ExecutionContext();
      Plan initialPlan = new Plan("goal-1", List.of("step1"), "impact", List.of());
      Plan nextPlan = new Plan("goal-2", List.of("step1"), "impact", List.of());
      ctx.setPlan(initialPlan);
      ctx.setConflictCandidates(List.of(ConflictCandidate.of("Test.java")));
      ctx.setOverwriteConfirmed(true);

      ctx.setPlan(nextPlan);

      assertTrue(ctx.getConflictCandidates().isEmpty());
      assertFalse(ctx.isConflictScanDone());
      assertFalse(ctx.isOverwriteConfirmed());
    }

    @Test
    @DisplayName("Setting the same plan should keep conflict scan state")
    void settingSamePlanKeepsConflictScan() {
      ExecutionContext ctx = new ExecutionContext();
      Plan plan = new Plan("goal", List.of("step1"), "impact", List.of());
      ctx.setPlan(plan);
      ctx.setConflictCandidates(List.of(ConflictCandidate.of("Test.java")));
      ctx.setOverwriteConfirmed(true);

      ctx.setPlan(plan);

      assertFalse(ctx.getConflictCandidates().isEmpty());
      assertTrue(ctx.isConflictScanDone());
      assertTrue(ctx.isOverwriteConfirmed());
    }
  }

  @Nested
  @DisplayName("Ready for execution check")
  class ReadyForExecutionTests {

    @Test
    @DisplayName("Should not be ready without plan")
    void shouldNotBeReadyWithoutPlan() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setConflictPolicy(ConflictPolicy.SAFE);

      assertFalse(ctx.isReadyForExecution());
    }

    @Test
    @DisplayName("Should not be ready without conflict policy")
    void shouldNotBeReadyWithoutPolicy() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setPlan(new Plan("goal", List.of("step1"), "impact", List.of()));

      assertFalse(ctx.isReadyForExecution());
    }

    @Test
    @DisplayName("Should be ready with plan and SAFE policy")
    void shouldBeReadyWithSafePolicy() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setPlan(new Plan("goal", List.of("step1"), "impact", List.of()));
      ctx.setConflictPolicy(ConflictPolicy.SAFE);

      assertTrue(ctx.isReadyForExecution());
    }

    @Test
    @DisplayName("Should not be ready with OVERWRITE and conflicts but no confirmation")
    void shouldNotBeReadyWithUnconfirmedOverwrite() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setPlan(new Plan("goal", List.of("step1"), "impact", List.of()));
      ctx.setConflictPolicy(ConflictPolicy.OVERWRITE);
      ctx.setConflictCandidates(List.of(ConflictCandidate.of("Test.java")));

      assertFalse(ctx.isReadyForExecution());
    }

    @Test
    @DisplayName("Should be ready with OVERWRITE, conflicts, and confirmation")
    void shouldBeReadyWithConfirmedOverwrite() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setPlan(new Plan("goal", List.of("step1"), "impact", List.of()));
      ctx.setConflictPolicy(ConflictPolicy.OVERWRITE);
      ctx.setConflictCandidates(List.of(ConflictCandidate.of("Test.java")));
      ctx.setOverwriteConfirmed(true);

      assertTrue(ctx.isReadyForExecution());
    }

    @Test
    @DisplayName("Should be ready with OVERWRITE but no conflicts")
    void shouldBeReadyWithOverwriteNoConflicts() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setPlan(new Plan("goal", List.of("step1"), "impact", List.of()));
      ctx.setConflictPolicy(ConflictPolicy.OVERWRITE);
      // No conflicts set

      assertTrue(ctx.isReadyForExecution());
    }
  }

  @Nested
  @DisplayName("Reset")
  class ResetTests {

    @Test
    @DisplayName("Should reset all fields to initial state")
    void shouldResetAllFields() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setPlan(new Plan("goal", List.of("step1"), "impact", List.of()));
      ctx.setConflictPolicy(ConflictPolicy.OVERWRITE);
      ctx.setConflictCandidates(List.of(ConflictCandidate.of("Test.java")));
      ctx.setOverwriteConfirmed(true);

      ctx.reset();

      assertTrue(ctx.getPlan().isEmpty());
      assertTrue(ctx.getConflictPolicy().isEmpty());
      assertTrue(ctx.getConflictCandidates().isEmpty());
      assertFalse(ctx.isOverwriteConfirmed());
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTests {

    @Test
    @DisplayName("Should return meaningful string representation")
    void shouldReturnMeaningfulString() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setConflictPolicy(ConflictPolicy.SAFE);
      ctx.setConflictCandidates(List.of(ConflictCandidate.of("Test.java")));

      String str = ctx.toString();

      assertTrue(str.contains("ExecutionContext"));
      assertTrue(str.contains("conflictPolicy=SAFE"));
      assertTrue(str.contains("conflicts=1"));
    }

    @Test
    @DisplayName("Should include hasIssue in string representation")
    void shouldIncludeHasIssue() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setCurrentIssue(ExecutionIssue.testFailure("MyClass", "RUN", "failed"));

      String str = ctx.toString();
      assertTrue(str.contains("hasIssue=true"));
    }
  }

  @Nested
  @DisplayName("Issue handling")
  class IssueHandlingTests {

    @Test
    @DisplayName("Should have no issue initially")
    void shouldHaveNoIssueInitially() {
      ExecutionContext ctx = new ExecutionContext();

      assertFalse(ctx.hasIssue());
      assertTrue(ctx.getCurrentIssue().isEmpty());
      assertTrue(ctx.getSelectedOption().isEmpty());
    }

    @Test
    @DisplayName("Should set and get current issue")
    void shouldSetAndGetIssue() {
      ExecutionContext ctx = new ExecutionContext();
      ExecutionIssue issue = ExecutionIssue.testFailure("MyClass", "RUN", "Test failed");

      ctx.setCurrentIssue(issue);

      assertTrue(ctx.hasIssue());
      assertTrue(ctx.getCurrentIssue().isPresent());
      assertEquals(issue, ctx.getCurrentIssue().get());
    }

    @Test
    @DisplayName("Setting issue should clear previously selected option")
    void settingIssueShouldClearOption() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setSelectedOption(IssueHandlingOption.SKIP);

      ctx.setCurrentIssue(ExecutionIssue.testFailure("MyClass", "RUN", "failed"));

      assertTrue(ctx.getSelectedOption().isEmpty());
    }

    @Test
    @DisplayName("Should set and get selected option")
    void shouldSetAndGetOption() {
      ExecutionContext ctx = new ExecutionContext();

      ctx.setSelectedOption(IssueHandlingOption.SAFE_FIX);

      assertTrue(ctx.getSelectedOption().isPresent());
      assertEquals(IssueHandlingOption.SAFE_FIX, ctx.getSelectedOption().get());
    }

    @Test
    @DisplayName("Clear issue should remove both issue and option")
    void clearIssueShouldRemoveBoth() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setCurrentIssue(ExecutionIssue.testFailure("MyClass", "RUN", "failed"));
      ctx.setSelectedOption(IssueHandlingOption.SKIP);

      ctx.clearIssue();

      assertFalse(ctx.hasIssue());
      assertTrue(ctx.getCurrentIssue().isEmpty());
      assertTrue(ctx.getSelectedOption().isEmpty());
    }

    @Test
    @DisplayName("Reset should clear issue state")
    void resetShouldClearIssueState() {
      ExecutionContext ctx = new ExecutionContext();
      ctx.setCurrentIssue(ExecutionIssue.testFailure("MyClass", "RUN", "failed"));
      ctx.setSelectedOption(IssueHandlingOption.PROPOSE_ONLY);

      ctx.reset();

      assertFalse(ctx.hasIssue());
      assertTrue(ctx.getCurrentIssue().isEmpty());
      assertTrue(ctx.getSelectedOption().isEmpty());
    }
  }
}
