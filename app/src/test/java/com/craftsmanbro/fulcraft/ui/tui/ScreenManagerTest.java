package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenManagerTest {

  @Test
  void constructorRejectsNullInitialState() {
    Screen home = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);

    assertThatThrownBy(() -> new ScreenManager(null, List.of(home)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("initialState must not be null");
  }

  @Test
  void constructorRejectsNullScreens() {
    assertThatThrownBy(() -> new ScreenManager(State.HOME, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("screens must not be null");
  }

  @Test
  void constructorRejectsNullScreenState() {
    Screen invalid = mock(Screen.class);
    when(invalid.getState()).thenReturn(null);

    assertThatThrownBy(() -> new ScreenManager(State.HOME, List.of(invalid)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("screen state must not be null");
  }

  @Test
  void constructorRejectsInitialStateWithoutRegisteredScreen() {
    Screen home = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);

    assertThatThrownBy(() -> new ScreenManager(State.CHAT_INPUT, List.of(home)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No screen registered for state");
  }

  @Test
  void constructorRejectsDuplicateStates() {
    Screen home1 = mock(Screen.class);
    Screen home2 = mock(Screen.class);
    when(home1.getState()).thenReturn(State.HOME);
    when(home2.getState()).thenReturn(State.HOME);

    assertThatThrownBy(() -> new ScreenManager(State.HOME, List.of(home1, home2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate screen registered for state");
  }

  @Test
  void constructorSkipsNullEntries() {
    Screen home = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);

    ScreenManager manager = new ScreenManager(State.HOME, Arrays.asList(home, null));

    assertThat(manager.getCurrentScreen()).isSameAs(home);
    assertThat(manager.getCurrentState()).isEqualTo(State.HOME);
  }

  @Test
  void setCurrentStateRejectsMissingScreen() {
    Screen home = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);

    ScreenManager manager = new ScreenManager(State.HOME, List.of(home));

    assertThatThrownBy(() -> manager.setCurrentState(State.CHAT_INPUT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No screen registered for state");
  }

  @Test
  void setCurrentStateRejectsNullState() {
    Screen home = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);

    ScreenManager manager = new ScreenManager(State.HOME, List.of(home));

    assertThatThrownBy(() -> manager.setCurrentState(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("state must not be null");
  }

  @Test
  void handleInputDoesNothingWhenCurrentScreenIsNull() throws Exception {
    Screen home = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);
    ScreenManager manager = new ScreenManager(State.HOME, List.of(home));
    clearCurrentScreen(manager);
    TuiApplication app = mock(TuiApplication.class);

    manager.handleInput(app, new KeyStroke(KeyType.Enter));

    verifyNoInteractions(app);
    verify(home, never()).handleInput(eq(app), any(KeyStroke.class));
  }

  @Test
  void handleInputFollowsStateMachineWhenScreenReturnsNull() throws IOException {
    Screen home = mock(Screen.class);
    Screen chat = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);
    when(chat.getState()).thenReturn(State.CHAT_INPUT);
    StateMachine stateMachine = new StateMachine();
    TuiApplication app = new TuiApplication(stateMachine);
    KeyStroke enter = new KeyStroke(KeyType.Enter);
    when(home.handleInput(app, enter)).thenReturn(null);
    ScreenManager manager = new ScreenManager(State.HOME, List.of(home, chat));

    stateMachine.transitionTo(State.CHAT_INPUT);
    manager.handleInput(app, enter);

    assertThat(manager.getCurrentState()).isEqualTo(State.CHAT_INPUT);
  }

  @Test
  void handleInputKeepsCurrentStateWhenScreenAndStateMachineAgree() throws IOException {
    Screen home = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);
    KeyStroke enter = new KeyStroke(KeyType.Enter);
    StateMachine stateMachine = new StateMachine();
    TuiApplication app = new TuiApplication(stateMachine);
    when(home.handleInput(app, enter)).thenReturn(State.HOME);
    ScreenManager manager = new ScreenManager(State.HOME, List.of(home));

    manager.handleInput(app, enter);

    assertThat(manager.getCurrentState()).isEqualTo(State.HOME);
  }

  @Test
  void handleInputUsesStateMachineWhenScreenReturnsDifferentState() throws IOException {
    Screen home = mock(Screen.class);
    Screen conflict = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);
    when(conflict.getState()).thenReturn(State.CONFLICT_POLICY);
    StateMachine stateMachine = new StateMachine();
    TuiApplication app = new TuiApplication(stateMachine);
    KeyStroke enter = new KeyStroke(KeyType.Enter);
    when(home.handleInput(app, enter)).thenReturn(State.CHAT_INPUT);
    ScreenManager manager = new ScreenManager(State.HOME, List.of(home, conflict));

    stateMachine.transitionTo(State.CONFLICT_POLICY);
    manager.handleInput(app, enter);

    assertThat(manager.getCurrentState()).isEqualTo(State.CONFLICT_POLICY);
  }

  @Test
  void drawDelegatesToCurrentScreen() {
    Screen home = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);
    ScreenManager manager = new ScreenManager(State.HOME, List.of(home));
    TuiApplication app = mock(TuiApplication.class);
    TextGraphics tg = mock(TextGraphics.class);
    TerminalSize size = new TerminalSize(80, 24);

    manager.draw(app, tg, size, 3);

    verify(home).draw(app, tg, size, 3);
  }

  @Test
  void drawSkipsWhenCurrentScreenIsNull() throws Exception {
    Screen home = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);
    ScreenManager manager = new ScreenManager(State.HOME, List.of(home));
    clearCurrentScreen(manager);
    TuiApplication app = mock(TuiApplication.class);
    TextGraphics tg = mock(TextGraphics.class);
    TerminalSize size = new TerminalSize(80, 24);

    manager.draw(app, tg, size, 2);

    verifyNoInteractions(tg);
    verify(home, never()).draw(eq(app), eq(tg), eq(size), eq(2));
  }

  @Test
  void getCurrentStateReturnsNullWhenCurrentScreenIsNull() throws Exception {
    Screen home = mock(Screen.class);
    when(home.getState()).thenReturn(State.HOME);
    ScreenManager manager = new ScreenManager(State.HOME, List.of(home));
    clearCurrentScreen(manager);

    assertThat(manager.getCurrentState()).isNull();
    assertThat(manager.getCurrentScreen()).isNull();
  }

  private static void clearCurrentScreen(ScreenManager manager) throws Exception {
    Field currentScreen = ScreenManager.class.getDeclaredField("currentScreen");
    currentScreen.setAccessible(true);
    currentScreen.set(manager, null);
  }
}
