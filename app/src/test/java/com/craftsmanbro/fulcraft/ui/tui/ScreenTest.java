package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.io.IOException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ScreenTest {

  @Test
  void keepsStableScreenContract() throws NoSuchMethodException {
    assertThat(Screen.class.isInterface()).isTrue();

    Method getState = Screen.class.getMethod("getState");
    assertThat(getState.getReturnType()).isEqualTo(State.class);
    assertThat(getState.getParameterCount()).isZero();
    assertThat(getState.getExceptionTypes()).isEmpty();

    Method draw =
        Screen.class.getMethod(
            "draw", TuiApplication.class, TextGraphics.class, TerminalSize.class, int.class);
    assertThat(draw.getReturnType()).isEqualTo(void.class);
    assertThat(draw.getExceptionTypes()).isEmpty();

    Method handleInput =
        Screen.class.getMethod("handleInput", TuiApplication.class, KeyStroke.class);
    assertThat(handleInput.getReturnType()).isEqualTo(State.class);
    assertThat(handleInput.getExceptionTypes()).containsExactly(IOException.class);
  }
}
