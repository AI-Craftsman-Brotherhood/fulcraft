package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class BrittlenessSignalTest {

  @Test
  void token_returnsLowercaseName() {
    for (BrittlenessSignal signal : BrittlenessSignal.values()) {
      assertThat(signal.token()).isEqualTo(signal.name().toLowerCase(Locale.ROOT));
    }
  }
}
