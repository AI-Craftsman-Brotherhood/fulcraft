package com.craftsmanbro.fulcraft.ui.tui.state;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * State machine states for the TUI application.
 *
 * <p>Each state represents a distinct screen or mode in the interactive TUI workflow:
 *
 * <ul>
 *   <li>{@link #HOME} - Initial home screen with navigation options
 *   <li>{@link #CHAT_INPUT} - Chat input mode for user queries
 *   <li>{@link #PLAN_REVIEW} - Review of generated test plan
 *   <li>{@link #CONFLICT_POLICY} - Conflict resolution policy selection
 *   <li>{@link #EXECUTION_RUNNING} - Test execution in progress
 *   <li>{@link #ISSUE_HANDLING} - Handling issues and errors
 *   <li>{@link #SUMMARY} - Final summary display
 *   <li>{@link #CONFIG_CATEGORY} - Config category selection
 *   <li>{@link #CONFIG_ITEMS} - Config item selection
 *   <li>{@link #CONFIG_EDIT} - Config value editing
 *   <li>{@link #CONFIG_VALIDATE} - Config validation results
 * </ul>
 */
public enum State {

  /** Initial home screen with navigation options. */
  HOME("tui.state.home"),
  /** Chat input mode for user queries. */
  CHAT_INPUT("tui.state.chat_input"),
  /** Review of generated test plan. */
  PLAN_REVIEW("tui.state.plan_review"),
  /** Conflict resolution policy selection. */
  CONFLICT_POLICY("tui.state.conflict_policy"),
  /** Test execution in progress. */
  EXECUTION_RUNNING("tui.state.execution_running"),
  /** Handling issues and errors. */
  ISSUE_HANDLING("tui.state.issue_handling"),
  /** Final summary display. */
  SUMMARY("tui.state.summary"),
  /** Config category selection. */
  CONFIG_CATEGORY("tui.state.config_category"),
  /** Config item selection. */
  CONFIG_ITEMS("tui.state.config_items"),
  /** Config value editing. */
  CONFIG_EDIT("tui.state.config_edit"),
  /** Config validation results. */
  CONFIG_VALIDATE("tui.state.config_validate");

  private static final State DEFAULT_STATE = HOME;

  private final String displayNameKey;

  State(final String displayNameKey) {
    this.displayNameKey = displayNameKey;
  }

  /**
   * Parses a state name from persisted JSON, defaulting to HOME for unknown values.
   *
   * <p>This keeps session resume stable if newer states are introduced or older values remain.
   *
   * @param value serialized state name
   * @return resolved state, or HOME if unknown
   */
  @JsonCreator
  public static State fromString(final String value) {
    if (value == null) {
      return DEFAULT_STATE;
    }
    return parseOrDefault(value);
  }

  private static State parseOrDefault(final String value) {
    try {
      return State.valueOf(value);
    } catch (IllegalArgumentException ignored) {
      return DEFAULT_STATE;
    }
  }

  /** Returns a human-readable name for this state. */
  public String getDisplayName() {
    return MessageSource.getMessage(displayNameKey);
  }
}
