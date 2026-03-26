package com.craftsmanbro.fulcraft.ui.tui.conflict;

import com.craftsmanbro.fulcraft.i18n.MessageSource;

/**
 * Options available to the user when handling an execution issue.
 *
 * <p>When a failure occurs during test generation, the user can choose one of these options to
 * determine how the system should proceed:
 *
 * <ul>
 *   <li>{@link #SAFE_FIX} - Apply a conservative fix (e.g., relax assertions)
 *   <li>{@link #PROPOSE_ONLY} - Generate a proposal without applying changes
 *   <li>{@link #SKIP} - Skip this target and continue with the next one
 * </ul>
 */
public enum IssueHandlingOption {

  /**
   * Apply a conservative/safe fix to address the issue.
   *
   * <p>Current TUI safe fix behavior:
   *
   * <ul>
   *   <li>Disable failing tests with {@code @Disabled}
   *   <li>Re-run the test execution stage
   * </ul>
   */
  SAFE_FIX(1, "tui.issue_option.safe_fix.name", "tui.issue_option.safe_fix.desc"),
  /**
   * Generate a design improvement proposal without applying changes.
   *
   * <p>The system will analyze the issue and suggest improvements, but will not automatically apply
   * any modifications.
   */
  PROPOSE_ONLY(2, "tui.issue_option.propose_only.name", "tui.issue_option.propose_only.desc"),
  /**
   * Skip the current target and continue execution.
   *
   * <p>The problematic target will be skipped, and execution will continue with the remaining
   * targets.
   */
  SKIP(3, "tui.issue_option.skip.name", "tui.issue_option.skip.desc");

  private final int keyNumber;

  private final String displayNameKey;

  private final String descriptionKey;

  IssueHandlingOption(
      final int keyNumber, final String displayNameKey, final String descriptionKey) {
    this.keyNumber = keyNumber;
    this.displayNameKey = displayNameKey;
    this.descriptionKey = descriptionKey;
  }

  /**
   * Returns the key number used to select this option (1, 2, or 3).
   *
   * @return the key number
   */
  public int getKeyNumber() {
    return keyNumber;
  }

  /**
   * Returns a human-readable name for this option.
   *
   * @return the display name
   */
  public String getDisplayName() {
    return MessageSource.getMessage(displayNameKey);
  }

  /**
   * Returns a menu label combining the key number and display name.
   *
   * @return the menu label
   */
  public String getMenuLabel() {
    return keyNumber + " - " + getDisplayName();
  }

  /**
   * Returns a description of what this option does.
   *
   * @return the description
   */
  public String getDescription() {
    return MessageSource.getMessage(descriptionKey);
  }

  /**
   * Finds an option by its key number.
   *
   * @param keyNumber the key number (1, 2, or 3)
   * @return the corresponding option, or null if not found
   */
  public static IssueHandlingOption fromKeyNumber(final int keyNumber) {
    for (final IssueHandlingOption candidateOption : values()) {
      if (candidateOption.keyNumber == keyNumber) {
        return candidateOption;
      }
    }
    return null;
  }
}
