package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link StateMachine}. */
class StateMachineTest {

  private StateMachine stateMachine;

  @BeforeEach
  void setUp() {
    stateMachine = new StateMachine();
  }

  @Test
  void defaultConstructor_startsAtHome() {
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.HOME);
  }

  @Test
  void constructorWithInitialState_startsAtSpecifiedState() {
    StateMachine sm = new StateMachine(State.SUMMARY);
    assertThat(sm.getCurrentState()).isEqualTo(State.SUMMARY);
  }

  @Test
  void constructorWithNullState_throwsException() {
    assertThatThrownBy(() -> new StateMachine(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("initialState");
  }

  @Test
  void transitionTo_changesState() {
    stateMachine.transitionTo(State.CHAT_INPUT);
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CHAT_INPUT);

    stateMachine.transitionTo(State.PLAN_REVIEW);
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.PLAN_REVIEW);
  }

  @Test
  void transitionTo_nullState_throwsException() {
    assertThatThrownBy(() -> stateMachine.transitionTo(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("newState");
  }

  @Test
  void transitionTo_notifiesListeners() {
    List<State> previousStates = new ArrayList<>();
    List<State> newStates = new ArrayList<>();

    stateMachine.addListener(
        (prev, next) -> {
          previousStates.add(prev);
          newStates.add(next);
        });

    stateMachine.transitionTo(State.CHAT_INPUT);
    stateMachine.transitionTo(State.EXECUTION_RUNNING);

    assertThat(previousStates).containsExactly(State.HOME, State.CHAT_INPUT);
    assertThat(newStates).containsExactly(State.CHAT_INPUT, State.EXECUTION_RUNNING);
  }

  @Test
  void addListener_nullListener_doesNotThrow() {
    // Should not throw
    stateMachine.addListener(null);

    // And should still work normally
    stateMachine.transitionTo(State.SUMMARY);
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.SUMMARY);
  }

  @Test
  void removeListener_stopsNotifications() {
    List<State> newStates = new ArrayList<>();
    StateMachine.StateChangeListener listener = (prev, next) -> newStates.add(next);

    stateMachine.addListener(listener);
    stateMachine.transitionTo(State.CHAT_INPUT);

    stateMachine.removeListener(listener);
    stateMachine.transitionTo(State.SUMMARY);

    // Only one notification (before removal)
    assertThat(newStates).containsExactly(State.CHAT_INPUT);
  }

  @Test
  void canTransitionTo_returnsTrueForNonNull() {
    assertThat(stateMachine.canTransitionTo(State.CHAT_INPUT)).isTrue();
    assertThat(stateMachine.canTransitionTo(State.SUMMARY)).isTrue();
  }

  @Test
  void canTransitionTo_returnsFalseForNull() {
    assertThat(stateMachine.canTransitionTo(null)).isFalse();
  }

  @Test
  void transitionTo_sameState_stillNotifiesListeners() {
    List<State> newStates = new ArrayList<>();
    stateMachine.addListener((prev, next) -> newStates.add(next));

    stateMachine.transitionTo(State.HOME);

    assertThat(newStates).containsExactly(State.HOME);
  }

  @Test
  void allStates_canBeTransitionedTo() {
    for (State state : State.values()) {
      stateMachine.transitionTo(state);
      assertThat(stateMachine.getCurrentState()).isEqualTo(state);
    }
  }

  @Test
  void transitionTo_rejectsWhenValidationFails() {
    StateMachine rejecting =
        new StateMachine(State.HOME) {
          @Override
          public boolean canTransitionTo(State target) {
            return target == State.HOME;
          }
        };

    assertThatThrownBy(() -> rejecting.transitionTo(State.CHAT_INPUT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid state transition")
        .hasMessageContaining("HOME")
        .hasMessageContaining("CHAT_INPUT");
  }
}
