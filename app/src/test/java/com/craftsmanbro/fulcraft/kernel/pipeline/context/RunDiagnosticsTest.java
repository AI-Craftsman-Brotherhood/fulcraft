package com.craftsmanbro.fulcraft.kernel.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RunDiagnosticsTest {

  @Test
  void shouldTrackErrorsAndWarnings() {
    RunDiagnostics diagnostics = new RunDiagnostics();

    assertThat(diagnostics.hasErrors()).isFalse();
    assertThat(diagnostics.hasWarnings()).isFalse();

    diagnostics.addError("boom");
    diagnostics.addWarning("heads up");

    assertThat(diagnostics.hasErrors()).isTrue();
    assertThat(diagnostics.hasWarnings()).isTrue();
    assertThat(diagnostics.getErrors()).containsExactly("boom");
    assertThat(diagnostics.getWarnings()).containsExactly("heads up");
  }

  @Test
  void shouldReturnImmutableSnapshots() {
    RunDiagnostics diagnostics = new RunDiagnostics();
    diagnostics.addError("boom");
    diagnostics.addWarning("heads up");

    List<String> errors = diagnostics.getErrors();
    List<String> warnings = diagnostics.getWarnings();

    assertThatThrownBy(() -> errors.add("extra")).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> warnings.add("extra"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldRejectNullEntries() {
    RunDiagnostics diagnostics = new RunDiagnostics();

    assertThatThrownBy(() -> diagnostics.addError(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("error");
    assertThatThrownBy(() -> diagnostics.addWarning(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("warning");
  }
}
