package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.io.IOException;

/**
 * Contract for TUI screens managed by {@link ScreenManager}.
 *
 * <p>State transitions should be driven by {@link
 * com.craftsmanbro.fulcraft.ui.tui.state.StateMachine}; implementations typically return the
 * current state from the state machine, or {@code null} to indicate no change.
 */
public interface Screen {

  /** Returns the state this screen represents. */
  State getState();

  /** Draws this screen starting at the provided row within the content area. */
  void draw(TuiApplication app, TextGraphics tg, TerminalSize size, int startRow);

  /** Handles input and returns the current state after handling, or {@code null} to keep it. */
  State handleInput(TuiApplication app, KeyStroke keyStroke) throws IOException;
}
