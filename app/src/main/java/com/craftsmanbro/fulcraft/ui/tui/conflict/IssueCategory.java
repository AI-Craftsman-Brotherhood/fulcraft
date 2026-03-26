package com.craftsmanbro.fulcraft.ui.tui.conflict;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.Objects;

/**
 * Category of execution issues that can occur during test generation.
 *
 * <p>Each category represents a distinct type of failure that may require different handling
 * strategies:
 *
 * <ul>
 *   <li>{@link #EXCEPTION} - Unexpected runtime exception during execution
 *   <li>{@link #TEST_FAILURE} - Generated test failed during execution
 *   <li>{@link #COMPILE_ERROR} - Generated code failed to compile
 * </ul>
 */
public enum IssueCategory {

  /**
   * Unexpected runtime exception during execution.
   *
   * <p>This includes uncaught exceptions, infrastructure failures, and other unexpected errors.
   */
  EXCEPTION("tui.issue_category.exception.name", "tui.issue_category.exception.desc"),
  /**
   * Generated test failed during execution.
   *
   * <p>The test was generated and compiled successfully, but the assertions or expected behavior
   * did not match.
   */
  TEST_FAILURE("tui.issue_category.test_failure.name", "tui.issue_category.test_failure.desc"),
  /**
   * Generated code failed to compile.
   *
   * <p>The generated test code contains syntax errors or cannot resolve required
   * imports/dependencies.
   */
  COMPILE_ERROR("tui.issue_category.compile_error.name", "tui.issue_category.compile_error.desc");

  private final String displayNameKey;

  private final String descriptionKey;

  IssueCategory(final String displayNameKey, final String descriptionKey) {
    this.displayNameKey = requireNonBlank(displayNameKey, "displayNameKey");
    this.descriptionKey = requireNonBlank(descriptionKey, "descriptionKey");
  }

  /**
   * Returns a human-readable name for this category.
   *
   * @return the display name
   */
  public String getDisplayName() {
    return MessageSource.getMessage(displayNameKey);
  }

  /**
   * Returns a brief description of this category.
   *
   * @return the description
   */
  public String getDescription() {
    return MessageSource.getMessage(descriptionKey);
  }

  private static String requireNonBlank(final String messageKey, final String argumentName) {
    Objects.requireNonNull(messageKey, argumentName + " must not be null");
    if (messageKey.isBlank()) {
      throw new IllegalArgumentException(argumentName + " must not be blank");
    }
    return messageKey;
  }
}
