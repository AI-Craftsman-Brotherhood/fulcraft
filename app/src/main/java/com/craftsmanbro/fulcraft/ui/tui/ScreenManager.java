package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScreenManager {

  private final Map<State, Screen> screens;

  private Screen currentScreen;

  public ScreenManager(final State initialState, final List<Screen> screens) {
    Objects.requireNonNull(initialState, "initialState must not be null");
    Objects.requireNonNull(screens, "screens must not be null");
    final Map<State, Screen> screenRegistry = new EnumMap<>(State.class);
    for (final Screen screen : screens) {
      if (screen == null) {
        continue;
      }
      final State screenState =
          Objects.requireNonNull(screen.getState(), "screen state must not be null");
      final Screen existingScreen = screenRegistry.putIfAbsent(screenState, screen);
      if (existingScreen != null) {
        throw new IllegalArgumentException(
            "Duplicate screen registered for state: "
                + screenState
                + " ("
                + existingScreen.getClass().getSimpleName()
                + ", "
                + screen.getClass().getSimpleName()
                + ")");
      }
    }
    this.screens = Map.copyOf(screenRegistry);
    setCurrentState(initialState);
  }

  public Screen getCurrentScreen() {
    return currentScreen;
  }

  public State getCurrentState() {
    return currentScreen != null ? currentScreen.getState() : null;
  }

  public void setCurrentState(final State state) {
    Objects.requireNonNull(state, "state must not be null");
    final Screen screenForState = screens.get(state);
    if (screenForState == null) {
      throw new IllegalArgumentException(
          "No screen registered for state: " + state + ". Available: " + screens.keySet());
    }
    this.currentScreen = screenForState;
  }

  public void handleInput(final TuiApplication app, final KeyStroke keyStroke) throws IOException {
    if (currentScreen == null) {
      return;
    }
    State requestedState = currentScreen.handleInput(app, keyStroke);
    final State stateMachineState = app.getStateMachine().getCurrentState();
    // The state machine remains authoritative after a screen handles input.
    if (requestedState == null || requestedState != stateMachineState) {
      requestedState = stateMachineState;
    }
    if (requestedState != currentScreen.getState()) {
      setCurrentState(requestedState);
    }
  }

  public void draw(
      final TuiApplication app,
      final TextGraphics tg,
      final TerminalSize size,
      final int startRow) {
    if (currentScreen == null) {
      return;
    }
    currentScreen.draw(app, tg, size, startRow);
  }
}
