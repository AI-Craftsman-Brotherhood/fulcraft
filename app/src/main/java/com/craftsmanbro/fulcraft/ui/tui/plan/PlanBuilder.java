package com.craftsmanbro.fulcraft.ui.tui.plan;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.List;

/**
 * Builds a Plan from user input.
 *
 * <p>This is currently a stub implementation that returns a fixed plan structure. Future
 * implementations will integrate with LLM to generate contextual plans.
 *
 * <p>The builder is designed to support async generation:
 *
 * <pre>
 * Plan plan = new PlanBuilder().build(userInput);
 * </pre>
 */
public class PlanBuilder {

  private static final int MAX_GOAL_INPUT_LENGTH = 50;

  /**
   * Builds a Plan from the given user input.
   *
   * <p>Currently returns a stub plan for demonstration purposes. Future: This will call LLM APIs to
   * analyze the input and generate a contextual plan.
   *
   * @param userInput the raw user input string
   * @return a Plan object ready for review
   */
  public Plan build(final String userInput) {
    final String defaultGoal = message("tui.plan.default_goal");
    final List<String> defaultSteps = buildDefaultPlanSteps();
    final String defaultImpact = message("tui.plan.default_impact");
    final List<String> defaultRisks = buildDefaultPlanRisks();
    final String goal = buildGoal(defaultGoal, userInput);
    return new Plan(goal, defaultSteps, defaultImpact, defaultRisks);
  }

  private String buildGoal(final String defaultGoal, final String userInput) {
    final String summarizedInput = summarizeGoalInput(userInput);
    return summarizedInput.isEmpty() ? defaultGoal : defaultGoal + ": " + summarizedInput;
  }

  private List<String> buildDefaultPlanSteps() {
    return List.of(
        message("tui.plan.default_step.analyze"),
        message("tui.plan.default_step.identify"),
        message("tui.plan.default_step.generate"),
        message("tui.plan.default_step.validate"),
        message("tui.plan.default_step.run"));
  }

  private List<String> buildDefaultPlanRisks() {
    return List.of(
        message("tui.plan.default_risk.review"), message("tui.plan.default_risk.mocking"));
  }

  private String summarizeGoalInput(final String userInput) {
    if (userInput == null) {
      return "";
    }
    final String normalizedInput = collapseWhitespace(userInput).trim();
    if (normalizedInput.isEmpty()) {
      return "";
    }
    return truncateWithEllipsis(normalizedInput, MAX_GOAL_INPUT_LENGTH);
  }

  private String collapseWhitespace(final String text) {
    final StringBuilder normalizedText = new StringBuilder(text.length());
    boolean lastCharacterWasWhitespace = false;
    for (int i = 0; i < text.length(); i++) {
      final char currentCharacter = text.charAt(i);
      if (Character.isWhitespace(currentCharacter)) {
        if (!lastCharacterWasWhitespace) {
          normalizedText.append(' ');
          lastCharacterWasWhitespace = true;
        }
      } else {
        normalizedText.append(currentCharacter);
        lastCharacterWasWhitespace = false;
      }
    }
    return normalizedText.toString();
  }

  private String truncateWithEllipsis(final String text, final int maxLength) {
    if (maxLength <= 0) {
      return "";
    }
    if (text.length() <= maxLength) {
      return text;
    }
    if (maxLength <= 3) {
      return text.substring(0, maxLength);
    }
    // Reserve space for the ellipsis so the result never exceeds maxLength.
    return text.substring(0, maxLength - 3) + "...";
  }

  private String message(final String key) {
    return MessageSource.getMessage(key);
  }
}
