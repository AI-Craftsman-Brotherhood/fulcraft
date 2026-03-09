package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link State} enum. */
class StateTest {

  @Test
  void allStates_haveDisplayNames() {
    for (State state : State.values()) {
      assertThat(state.getDisplayName()).isNotNull().isNotBlank();
    }
  }

  @Test
  void home_hasCorrectDisplayName() {
    assertThat(State.HOME.getDisplayName()).isEqualTo(MessageSource.getMessage("tui.state.home"));
  }

  @Test
  void chatInput_hasCorrectDisplayName() {
    assertThat(State.CHAT_INPUT.getDisplayName())
        .isEqualTo(MessageSource.getMessage("tui.state.chat_input"));
  }

  @Test
  void planReview_hasCorrectDisplayName() {
    assertThat(State.PLAN_REVIEW.getDisplayName())
        .isEqualTo(MessageSource.getMessage("tui.state.plan_review"));
  }

  @Test
  void conflictPolicy_hasCorrectDisplayName() {
    assertThat(State.CONFLICT_POLICY.getDisplayName())
        .isEqualTo(MessageSource.getMessage("tui.state.conflict_policy"));
  }

  @Test
  void executionRunning_hasCorrectDisplayName() {
    assertThat(State.EXECUTION_RUNNING.getDisplayName())
        .isEqualTo(MessageSource.getMessage("tui.state.execution_running"));
  }

  @Test
  void issueHandling_hasCorrectDisplayName() {
    assertThat(State.ISSUE_HANDLING.getDisplayName())
        .isEqualTo(MessageSource.getMessage("tui.state.issue_handling"));
  }

  @Test
  void summary_hasCorrectDisplayName() {
    assertThat(State.SUMMARY.getDisplayName())
        .isEqualTo(MessageSource.getMessage("tui.state.summary"));
  }

  @Test
  void configCategory_hasCorrectDisplayName() {
    assertThat(State.CONFIG_CATEGORY.getDisplayName())
        .isEqualTo(MessageSource.getMessage("tui.state.config_category"));
  }

  @Test
  void configItems_hasCorrectDisplayName() {
    assertThat(State.CONFIG_ITEMS.getDisplayName())
        .isEqualTo(MessageSource.getMessage("tui.state.config_items"));
  }

  @Test
  void configEdit_hasCorrectDisplayName() {
    assertThat(State.CONFIG_EDIT.getDisplayName())
        .isEqualTo(MessageSource.getMessage("tui.state.config_edit"));
  }

  @Test
  void configValidate_hasCorrectDisplayName() {
    assertThat(State.CONFIG_VALIDATE.getDisplayName())
        .isEqualTo(MessageSource.getMessage("tui.state.config_validate"));
  }

  @Test
  void valueOf_allExpectedStatesExist() {
    assertThat(State.valueOf("HOME")).isEqualTo(State.HOME);
    assertThat(State.valueOf("CHAT_INPUT")).isEqualTo(State.CHAT_INPUT);
    assertThat(State.valueOf("PLAN_REVIEW")).isEqualTo(State.PLAN_REVIEW);
    assertThat(State.valueOf("CONFLICT_POLICY")).isEqualTo(State.CONFLICT_POLICY);
    assertThat(State.valueOf("EXECUTION_RUNNING")).isEqualTo(State.EXECUTION_RUNNING);
    assertThat(State.valueOf("ISSUE_HANDLING")).isEqualTo(State.ISSUE_HANDLING);
    assertThat(State.valueOf("SUMMARY")).isEqualTo(State.SUMMARY);
    assertThat(State.valueOf("CONFIG_CATEGORY")).isEqualTo(State.CONFIG_CATEGORY);
    assertThat(State.valueOf("CONFIG_ITEMS")).isEqualTo(State.CONFIG_ITEMS);
    assertThat(State.valueOf("CONFIG_EDIT")).isEqualTo(State.CONFIG_EDIT);
    assertThat(State.valueOf("CONFIG_VALIDATE")).isEqualTo(State.CONFIG_VALIDATE);
  }

  @Test
  void values_containsAllStates() {
    assertThat(State.values()).hasSize(11);
  }

  @Test
  void fromString_returnsHomeForNull() {
    assertThat(State.fromString(null)).isEqualTo(State.HOME);
  }

  @Test
  void fromString_returnsHomeForUnknownValue() {
    assertThat(State.fromString("UNKNOWN")).isEqualTo(State.HOME);
  }

  @Test
  void fromString_isCaseSensitiveAndFallsBackToHome() {
    assertThat(State.fromString("home")).isEqualTo(State.HOME);
  }

  @Test
  void fromString_parsesValidStateName() {
    assertThat(State.fromString("CHAT_INPUT")).isEqualTo(State.CHAT_INPUT);
  }
}
