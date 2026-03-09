package com.craftsmanbro.fulcraft.ui.tui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StateTest {

  @Test
  void fromString_returnsHomeForNullAndUnknownValues() {
    assertThat(State.fromString(null)).isEqualTo(State.HOME);
    assertThat(State.fromString("UNKNOWN_STATE")).isEqualTo(State.HOME);
  }

  @Test
  void fromString_isCaseSensitiveAndFallsBackToHome() {
    assertThat(State.fromString("home")).isEqualTo(State.HOME);
  }

  @Test
  void fromString_parsesAllValidStateNames() {
    for (State state : State.values()) {
      assertThat(State.fromString(state.name())).isEqualTo(state);
    }
  }

  @Test
  void getDisplayName_isNonBlankForAllStates() {
    for (State state : State.values()) {
      assertThat(state.getDisplayName()).isNotBlank();
    }
  }
}
