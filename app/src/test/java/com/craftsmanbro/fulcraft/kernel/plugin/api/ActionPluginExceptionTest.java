package com.craftsmanbro.fulcraft.kernel.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActionPluginExceptionTest {

  @Test
  void constructor_withMessage_setsMessageAndNoCause() {
    ActionPluginException exception = new ActionPluginException("failed");

    assertThat(exception).hasMessage("failed");
    assertThat(exception.getCause()).isNull();
  }

  @Test
  void constructor_withMessageAndCause_setsMessageAndCause() {
    Throwable cause = new IllegalStateException("boom");

    ActionPluginException exception = new ActionPluginException("failed", cause);

    assertThat(exception).hasMessage("failed");
    assertThat(exception.getCause()).isSameAs(cause);
  }
}
