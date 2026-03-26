package com.craftsmanbro.fulcraft.ui.tui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * State machine for managing TUI application states.
 *
 * <p>Manages state transitions and notifies listeners when the state changes. This is the core
 * coordinator for the TUI workflow.
 */
public class StateMachine {

  /** Listener interface for state change events. */
  @FunctionalInterface
  public interface StateChangeListener {

    /**
     * Called when the state changes.
     *
     * @param previousState the previous state (may be null on initial transition)
     * @param newState the new current state
     */
    void onStateChange(State previousState, State newState);
  }

  private State currentState;

  private final List<StateChangeListener> listeners = new ArrayList<>();

  /** Creates a new StateMachine starting in the HOME state. */
  public StateMachine() {
    this(State.HOME);
  }

  /**
   * Creates a new StateMachine starting in the specified state.
   *
   * @param initialState the initial state
   */
  public StateMachine(final State initialState) {
    this.currentState = Objects.requireNonNull(initialState, "initialState must not be null");
  }

  /**
   * Returns the current state.
   *
   * @return the current state
   */
  public State getCurrentState() {
    return currentState;
  }

  /**
   * Transitions to a new state.
   *
   * @param newState the state to transition to
   * @throws NullPointerException if newState is null
   * @throws IllegalStateException if the transition is not allowed
   */
  public void transitionTo(final State newState) {
    final State targetState = Objects.requireNonNull(newState, "newState must not be null");
    validateTransition(targetState);
    applyTransition(targetState);
  }

  private void validateTransition(final State targetState) {
    if (!canTransitionTo(targetState)) {
      throw new IllegalStateException(
          "Invalid state transition: " + currentState + " -> " + targetState);
    }
  }

  private void applyTransition(final State targetState) {
    final State previousState = this.currentState;
    this.currentState = targetState;
    notifyStateChangeListeners(previousState, targetState);
  }

  /**
   * Adds a state change listener.
   *
   * @param listener the listener to add
   */
  public void addListener(final StateChangeListener listener) {
    if (listener == null) {
      return;
    }
    listeners.add(listener);
  }

  /**
   * Removes a state change listener.
   *
   * @param listener the listener to remove
   */
  public void removeListener(final StateChangeListener listener) {
    listeners.remove(listener);
  }

  private void notifyStateChangeListeners(final State previousState, final State newState) {
    final List<StateChangeListener> snapshot = new ArrayList<>(listeners);
    for (final StateChangeListener listener : snapshot) {
      listener.onStateChange(previousState, newState);
    }
  }

  /**
   * Checks if a transition is valid from the current state to the target state.
   *
   * <p>This is a placeholder for future validation logic. Currently, all transitions are allowed.
   * Override this method to add validation rules; {@link #transitionTo(State)} enforces it.
   *
   * @param target the target state
   * @return true if the transition is valid
   */
  public boolean canTransitionTo(final State target) {
    // For now, allow all transitions. Future implementation may add restrictions.
    return target != null;
  }
}
