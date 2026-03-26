package com.craftsmanbro.fulcraft.ui.tui.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link Plan}. */
class PlanTest {

  @Test
  @DisplayName("should create plan with all fields correctly assigned")
  void shouldCreatePlanWithAllFields() {
    Plan plan =
        new Plan(
            "Generate unit tests",
            List.of("Analyze source", "Generate tests", "Validate"),
            "Improved test coverage",
            List.of("May need manual review"));

    assertThat(plan.goal()).isEqualTo("Generate unit tests");
    assertThat(plan.steps()).containsExactly("Analyze source", "Generate tests", "Validate");
    assertThat(plan.impact()).isEqualTo("Improved test coverage");
    assertThat(plan.risks()).containsExactly("May need manual review");
  }

  @Test
  @DisplayName("should be immutable by copy")
  void shouldBeImmutable() {
    List<String> steps = new java.util.ArrayList<>(List.of("Step 1"));
    List<String> risks = new java.util.ArrayList<>(List.of("Risk 1"));

    Plan plan = new Plan("Goal", steps, "Impact", risks);

    // Modify original lists
    steps.add("Step 2");
    risks.add("Risk 2");

    // Plan should not be affected
    assertThat(plan.steps()).containsExactly("Step 1");
    assertThat(plan.risks()).containsExactly("Risk 1");
  }

  @Test
  @DisplayName("should expose immutable step and risk lists")
  void shouldExposeImmutableLists() {
    Plan plan = new Plan("Goal", List.of("Step 1"), "Impact", List.of("Risk 1"));

    assertThatThrownBy(() -> plan.steps().add("Step 2"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> plan.risks().add("Risk 2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("should reject null goal")
  void shouldRejectNullGoal() {
    assertThatThrownBy(() -> new Plan(null, List.of(), "Impact", List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("goal");
  }

  @Test
  @DisplayName("should reject null steps")
  void shouldRejectNullSteps() {
    assertThatThrownBy(() -> new Plan("Goal", null, "Impact", List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("steps");
  }

  @Test
  @DisplayName("should reject null impact")
  void shouldRejectNullImpact() {
    assertThatThrownBy(() -> new Plan("Goal", List.of(), null, List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("impact");
  }

  @Test
  @DisplayName("should reject null risks")
  void shouldRejectNullRisks() {
    assertThatThrownBy(() -> new Plan("Goal", List.of(), "Impact", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("risks");
  }

  @Test
  @DisplayName("should reject null element in steps")
  void shouldRejectNullElementInSteps() {
    assertThatThrownBy(
            () -> new Plan("Goal", java.util.Arrays.asList("Step 1", null), "Impact", List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("should reject null element in risks")
  void shouldRejectNullElementInRisks() {
    assertThatThrownBy(
            () ->
                new Plan(
                    "Goal", List.of("Step 1"), "Impact", java.util.Arrays.asList("Risk 1", null)))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("hasSteps should return true when steps are present")
  void hasStepsShouldReturnTrueWhenStepsPresent() {
    Plan plan = new Plan("Goal", List.of("Step 1"), "Impact", List.of());
    assertThat(plan.hasSteps()).isTrue();
  }

  @Test
  @DisplayName("hasSteps should return false when no steps are present")
  void hasStepsShouldReturnFalseWhenNoSteps() {
    Plan plan = new Plan("Goal", List.of(), "Impact", List.of());
    assertThat(plan.hasSteps()).isFalse();
  }

  @Test
  @DisplayName("toDisplayString should format plan correctly")
  void toDisplayStringShouldFormatPlan() {
    Plan plan =
        new Plan(
            "Test goal",
            List.of("First step", "Second step"),
            "Expected impact",
            List.of("First risk"));

    String display = plan.toDisplayString();

    assertThat(display).contains("Goal:");
    assertThat(display).contains("Test goal");
    assertThat(display).contains("Steps:");
    assertThat(display).contains("1. First step");
    assertThat(display).contains("2. Second step");
    assertThat(display).contains("Impact:");
    assertThat(display).contains("Expected impact");
    assertThat(display).contains("Risks:");
    assertThat(display).contains("- First risk");
  }

  @Test
  @DisplayName("toDisplayString should show no risks when empty")
  void toDisplayStringShouldShowNoRisksWhenEmpty() {
    Plan plan = new Plan("Goal", List.of("Step"), "Impact", List.of());

    String display = plan.toDisplayString();

    assertThat(display).contains("(none identified)");
  }

  @Test
  @DisplayName("toDisplayString should render empty steps section without numbering")
  void toDisplayStringShouldRenderEmptyStepsSection() {
    Plan plan = new Plan("Goal", List.of(), "Impact", List.of("Risk"));

    String display = plan.toDisplayString();

    assertThat(display).contains("Steps:\n\nImpact:");
    assertThat(display).doesNotContain("1. ");
  }
}
