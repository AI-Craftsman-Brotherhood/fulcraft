package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionSession;
import java.util.List;
import org.junit.jupiter.api.Test;

class TuiLogRedirectorTest {

  @Test
  void redirectorMasksSecretsAndCapturesLines() {
    ExecutionSession session = new ExecutionSession();
    TuiLogRedirector redirector = new TuiLogRedirector(session);

    redirector.getStdout().println("api_key=secret-value");
    redirector.getStdout().flush();

    List<String> logs = session.getLogLines();
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0)).contains("api_key=****");
  }

  @Test
  void redirectorHandlesCarriageReturnUpdates() {
    ExecutionSession session = new ExecutionSession();
    TuiLogRedirector redirector = new TuiLogRedirector(session);

    redirector.getStdout().print("first\rsecond\n");
    redirector.getStdout().flush();

    assertThat(session.getLogLines()).containsExactly("second");
  }
}
