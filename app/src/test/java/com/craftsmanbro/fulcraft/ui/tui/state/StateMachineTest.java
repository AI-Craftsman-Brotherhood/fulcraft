package com.craftsmanbro.fulcraft.ui.tui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    StateMachine machine = new StateMachine(State.SUMMARY);
    assertThat(machine.getCurrentState()).isEqualTo(State.SUMMARY);
  }

  @Test
  void constructorWithNull_throws() {
    assertThatThrownBy(() -> new StateMachine(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("initialState");
  }

  @Test
  void transitionTo_updatesStateAndNotifiesListeners() {
    List<State> previousStates = new ArrayList<>();
    List<State> newStates = new ArrayList<>();
    stateMachine.addListener(
        (previous, next) -> {
          previousStates.add(previous);
          newStates.add(next);
        });

    stateMachine.transitionTo(State.CHAT_INPUT);
    stateMachine.transitionTo(State.EXECUTION_RUNNING);

    assertThat(stateMachine.getCurrentState()).isEqualTo(State.EXECUTION_RUNNING);
    assertThat(previousStates).containsExactly(State.HOME, State.CHAT_INPUT);
    assertThat(newStates).containsExactly(State.CHAT_INPUT, State.EXECUTION_RUNNING);
  }

  @Test
  void transitionTo_nullState_throws() {
    assertThatThrownBy(() -> stateMachine.transitionTo(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("newState");
  }

  @Test
  void addListener_null_isNoOp() {
    stateMachine.addListener(null);

    stateMachine.transitionTo(State.SUMMARY);

    assertThat(stateMachine.getCurrentState()).isEqualTo(State.SUMMARY);
  }

  @Test
  void removeListener_stopsNotifications() {
    List<State> notifications = new ArrayList<>();
    StateMachine.StateChangeListener listener = (previous, next) -> notifications.add(next);

    stateMachine.addListener(listener);
    stateMachine.transitionTo(State.CHAT_INPUT);
    stateMachine.removeListener(listener);
    stateMachine.transitionTo(State.SUMMARY);

    assertThat(notifications).containsExactly(State.CHAT_INPUT);
  }

  @Test
  void notifyListeners_usesSnapshotWhenListenersMutateList() {
    List<String> called = new ArrayList<>();
    StateMachine machine = new StateMachine();

    StateMachine.StateChangeListener selfRemoving =
        new StateMachine.StateChangeListener() {
          @Override
          public void onStateChange(State previousState, State newState) {
            called.add("self");
            machine.removeListener(this);
          }
        };
    StateMachine.StateChangeListener other = (previous, next) -> called.add("other");
    machine.addListener(selfRemoving);
    machine.addListener(other);

    machine.transitionTo(State.CHAT_INPUT);
    machine.transitionTo(State.SUMMARY);

    assertThat(called).containsExactly("self", "other", "other");
  }

  @Test
  void canTransitionTo_returnsFalseOnlyForNull() {
    assertThat(stateMachine.canTransitionTo(State.CHAT_INPUT)).isTrue();
    assertThat(stateMachine.canTransitionTo(null)).isFalse();
  }

  @Test
  void transitionTo_throwsWhenValidationRejects() {
    StateMachine guarded =
        new StateMachine(State.HOME) {
          @Override
          public boolean canTransitionTo(State target) {
            return target == State.HOME;
          }
        };

    assertThatThrownBy(() -> guarded.transitionTo(State.CHAT_INPUT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid state transition")
        .hasMessageContaining("HOME")
        .hasMessageContaining("CHAT_INPUT");
  }
}
