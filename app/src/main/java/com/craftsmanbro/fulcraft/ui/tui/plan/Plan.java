package com.craftsmanbro.fulcraft.ui.tui.plan;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;

/**
 * Represents a plan generated from user input.
 *
 * <p>A Plan contains the structured breakdown of what the system intends to do:
 *
 * <ul>
 *   <li>{@code goal} - The high-level objective
 *   <li>{@code steps} - Ordered list of steps to achieve the goal
 *   <li>{@code impact} - Expected impact or outcome
 *   <li>{@code risks} - Potential risks or caveats
 * </ul>
 *
 * <p>This is designed to be displayed for user review before execution.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Plan(String goal, List<String> steps, String impact, List<String> risks) {

  private static final String NEWLINE = "\n";
  private static final String INDENT = "  ";

  /**
   * Creates a new Plan.
   *
   * @param goal the high-level goal (required)
   * @param steps ordered list of steps (required, immutable copy made)
   * @param impact expected impact description (required)
   * @param risks list of potential risks (required, immutable copy made)
   */
  public Plan {
    goal = requireNonNull(goal, "goal");
    steps = copyRequiredList(steps, "steps");
    impact = requireNonNull(impact, "impact");
    risks = copyRequiredList(risks, "risks");
  }

  /**
   * Returns true if this plan has any steps.
   *
   * @return true if steps is non-empty
   */
  public boolean hasSteps() {
    return !steps.isEmpty();
  }

  /**
   * Returns a formatted string representation for display.
   *
   * @return multi-line formatted plan
   */
  public String toDisplayString() {
    final StringBuilder displayBuilder = new StringBuilder();
    appendValueSection(displayBuilder, "tui.plan.label.goal", goal);
    appendNumberedSection(displayBuilder, "tui.plan.label.steps", steps);
    displayBuilder.append(NEWLINE);
    appendValueSection(displayBuilder, "tui.plan.label.impact", impact);
    appendRisksSection(displayBuilder, risks);
    return displayBuilder.toString();
  }

  private static <T> T requireNonNull(final T value, final String fieldName) {
    return Objects.requireNonNull(value, fieldName + " must not be null");
  }

  private static <T> List<T> copyRequiredList(final List<T> values, final String fieldName) {
    // Keep the canonical constructor responsible for null validation and immutability.
    return List.copyOf(requireNonNull(values, fieldName));
  }

  private static void appendValueSection(
      final StringBuilder displayBuilder, final String labelKey, final String value) {
    displayBuilder
        .append(message(labelKey))
        .append(NEWLINE)
        .append(INDENT)
        .append(value)
        .append(NEWLINE)
        .append(NEWLINE);
  }

  private static void appendNumberedSection(
      final StringBuilder displayBuilder, final String labelKey, final List<String> items) {
    displayBuilder.append(message(labelKey)).append(NEWLINE);
    for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
      final int itemNumber = itemIndex + 1;
      final String item = items.get(itemIndex);
      displayBuilder.append(INDENT).append(itemNumber).append(". ").append(item).append(NEWLINE);
    }
  }

  private static void appendRisksSection(
      final StringBuilder displayBuilder, final List<String> risks) {
    displayBuilder.append(message("tui.plan.label.risks")).append(NEWLINE);
    if (risks.isEmpty()) {
      // Keep the risks section visible even when the plan has no reported risks.
      displayBuilder.append(INDENT).append(message("tui.plan.label.no_risks")).append(NEWLINE);
      return;
    }
    for (final String risk : risks) {
      displayBuilder.append(INDENT).append("- ").append(risk).append(NEWLINE);
    }
  }

  private static String message(final String key) {
    return MessageSource.getMessage(key);
  }
}
