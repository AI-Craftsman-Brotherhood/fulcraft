package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CalledMethodRefTest {

  @Test
  void constructor_defaultsCandidatesToEmpty() {
    CalledMethodRef ref =
        new CalledMethodRef("raw", "resolved", ResolutionStatus.RESOLVED, 1.0, "src", null);

    assertThat(ref.getCandidates()).isEmpty();
    assertThat(ref.getArgumentLiterals()).isEmpty();
    assertThatThrownBy(() -> ref.getCandidates().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> ref.getArgumentLiterals().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void setCandidates_handlesNullAndReturnsUnmodifiableList() {
    CalledMethodRef ref = new CalledMethodRef();

    ref.setCandidates(null);

    assertThat(ref.getCandidates()).isEmpty();
    assertThatThrownBy(() -> ref.getCandidates().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void setArgumentLiterals_handlesNullAndReturnsUnmodifiableList() {
    CalledMethodRef ref = new CalledMethodRef();

    ref.setArgumentLiterals(null);

    assertThat(ref.getArgumentLiterals()).isEmpty();
    assertThatThrownBy(() -> ref.getArgumentLiterals().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
