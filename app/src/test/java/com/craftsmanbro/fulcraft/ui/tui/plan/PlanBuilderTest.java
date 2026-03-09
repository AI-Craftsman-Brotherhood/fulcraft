package com.craftsmanbro.fulcraft.ui.tui.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for {@link PlanBuilder}. */
class PlanBuilderTest {

  private static final String GOAL_PREFIX = "Process user request";
  private static final String GOAL_PREFIX_WITH_SEPARATOR = GOAL_PREFIX + ": ";

  @Test
  @DisplayName("should build plan from user input with correct stubs")
  void shouldBuildPlanFromUserInput() {
    PlanBuilder builder = new PlanBuilder();

    Plan plan = builder.build("Generate tests for OrderService");

    assertThat(plan).isNotNull();
    assertThat(plan.goal()).contains("Generate tests for OrderService");
    assertThat(plan.hasSteps()).isTrue();
    assertThat(plan.steps()).isNotEmpty();
    assertThat(plan.impact()).isNotBlank();
  }

  @Test
  @DisplayName("should truncate long input to avoid overly long goal")
  void shouldTruncateLongInput() {
    PlanBuilder builder = new PlanBuilder();
    String longInput = "A".repeat(100);

    Plan plan = builder.build(longInput);

    assertThat(plan.goal()).startsWith(GOAL_PREFIX_WITH_SEPARATOR);
    assertThat(extractSummary(plan)).hasSize(50);
    assertThat(extractSummary(plan)).isEqualTo("A".repeat(47) + "...");
  }

  @Test
  @DisplayName("should handle empty input gracefully")
  void shouldHandleEmptyInput() {
    PlanBuilder builder = new PlanBuilder();

    Plan plan = builder.build("");

    assertThat(plan).isNotNull();
    // Default goal prefix without appended input
    assertThat(plan.goal()).isEqualTo(GOAL_PREFIX);
  }

  @Test
  @DisplayName("should handle whitespace-only input gracefully")
  void shouldHandleWhitespaceOnlyInput() {
    PlanBuilder builder = new PlanBuilder();

    Plan plan = builder.build("   \t\n  ");

    assertThat(plan).isNotNull();
    assertThat(plan.goal()).isEqualTo(GOAL_PREFIX);
  }

  @Test
  @DisplayName("should handle null input gracefully")
  void shouldHandleNullInput() {
    PlanBuilder builder = new PlanBuilder();

    Plan plan = builder.build(null);

    assertThat(plan).isNotNull();
    assertThat(plan.goal()).isEqualTo(GOAL_PREFIX);
  }

  @Test
  @DisplayName("should include default steps in stub implementation")
  void shouldIncludeDefaultSteps() {
    PlanBuilder builder = new PlanBuilder();

    Plan plan = builder.build("any input");

    assertThat(plan.steps())
        .contains(
            "Analyze target source files",
            "Identify testable methods",
            "Generate unit test code",
            "Validate compilation",
            "Run generated tests");
  }

  @Test
  @DisplayName("should include risks in stub implementation")
  void shouldIncludeRisks() {
    PlanBuilder builder = new PlanBuilder();

    Plan plan = builder.build("any input");

    assertThat(plan.risks()).isNotEmpty();
  }

  @Test
  @DisplayName("should include default impact and risks")
  void shouldIncludeDefaultImpactAndRisks() {
    PlanBuilder builder = new PlanBuilder();

    Plan plan = builder.build("any input");

    assertThat(plan.impact()).isEqualTo("Unit tests will be generated for the specified targets.");
    assertThat(plan.risks())
        .containsExactly(
            "Generated tests may require manual review",
            "Complex mocking scenarios may need adjustment");
  }

  @Test
  @DisplayName("should return immutable step and risk lists")
  void shouldReturnImmutableLists() {
    PlanBuilder builder = new PlanBuilder();

    Plan plan = builder.build("any input");

    assertThatThrownBy(() -> plan.steps().add("extra step"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> plan.risks().add("extra risk"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("should include normalized input in goal")
  void shouldIncludeNormalizedInputInGoal() {
    PlanBuilder builder = new PlanBuilder();

    Plan plan = builder.build("  Generate   tests\tfor\nOrderService ");

    assertThat(plan.goal())
        .isEqualTo(GOAL_PREFIX_WITH_SEPARATOR + "Generate tests for OrderService");
  }

  @ParameterizedTest
  @DisplayName("should normalize whitespace in input")
  @org.junit.jupiter.params.provider.MethodSource("provideWhitespaceTestCases")
  void shouldNormalizeWhitespace(String input, String expected) {
    PlanBuilder builder = new PlanBuilder();
    Plan plan = builder.build(input);

    assertThat(plan.goal()).contains(expected);
  }

  static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>
      provideWhitespaceTestCases() {
    return java.util.stream.Stream.of(
        org.junit.jupiter.params.provider.Arguments.of("hello  world", "hello world"),
        org.junit.jupiter.params.provider.Arguments.of("  hello   world  ", "hello world"),
        org.junit.jupiter.params.provider.Arguments.of("hello\tworld", "hello world"),
        org.junit.jupiter.params.provider.Arguments.of("hello\nworld", "hello world"),
        org.junit.jupiter.params.provider.Arguments.of("hello \r\n world", "hello world"));
  }

  @ParameterizedTest
  @DisplayName("should truncate various lengths")
  @ValueSource(ints = {50, 51, 60, 100})
  void shouldTruncateVariousLengths(int length) {
    PlanBuilder builder = new PlanBuilder();
    String input = "x".repeat(length);
    Plan plan = builder.build(input);

    // Max length is 50 in implementation
    if (length > 50) {
      assertThat(extractSummary(plan)).hasSize(50).endsWith("...");
    } else {
      assertThat(extractSummary(plan)).isEqualTo(input);
    }
  }

  private String extractSummary(Plan plan) {
    if (plan.goal().equals(GOAL_PREFIX)) {
      return "";
    }
    return plan.goal().substring(GOAL_PREFIX_WITH_SEPARATOR.length());
  }
}
