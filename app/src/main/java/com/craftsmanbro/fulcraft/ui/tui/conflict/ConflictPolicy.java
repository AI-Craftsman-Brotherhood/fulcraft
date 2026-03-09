package com.craftsmanbro.fulcraft.ui.tui.conflict;

import com.craftsmanbro.fulcraft.i18n.MessageSource;

/**
 * Conflict resolution policy for handling existing files.
 *
 * <p>This enum represents the three available policies when a generated test file would conflict
 * with an existing file:
 *
 * <ul>
 *   <li>{@link #SAFE} - Generate with a new file name to avoid collision
 *   <li>{@link #SKIP} - Skip generation for conflicting files
 *   <li>{@link #OVERWRITE} - Overwrite existing files (requires confirmation)
 * </ul>
 */
public enum ConflictPolicy {

  /**
   * Safe mode: Generate a new file name when conflict is detected.
   *
   * <p>For example, if {@code UserServiceTest.java} already exists, the new file would be named
   * {@code UserServiceTest_1.java}.
   */
  SAFE("tui.conflict_policy.safe.name", "tui.conflict_policy.safe.desc"),
  /**
   * Skip mode: Do not generate test files that would conflict.
   *
   * <p>Conflicting files are simply skipped and remain unchanged.
   */
  SKIP("tui.conflict_policy.skip.name", "tui.conflict_policy.skip.desc"),
  /**
   * Overwrite mode: Replace existing files with generated content.
   *
   * <p><strong>Warning:</strong> This is dangerous and requires two-step confirmation. Diff
   * comparison is delegated to Git; no internal diff display is provided.
   */
  OVERWRITE("tui.conflict_policy.overwrite.name", "tui.conflict_policy.overwrite.desc");

  private final String displayNameMessageKey;

  private final String descriptionMessageKey;

  ConflictPolicy(final String displayNameMessageKey, final String descriptionMessageKey) {
    this.displayNameMessageKey = displayNameMessageKey;
    this.descriptionMessageKey = descriptionMessageKey;
  }

  /**
   * Returns the human-readable display name for this policy.
   *
   * @return the display name
   */
  public String getDisplayName() {
    return MessageSource.getMessage(displayNameMessageKey);
  }

  /**
   * Returns the description explaining this policy.
   *
   * @return the description
   */
  public String getDescription() {
    return MessageSource.getMessage(descriptionMessageKey);
  }
}
