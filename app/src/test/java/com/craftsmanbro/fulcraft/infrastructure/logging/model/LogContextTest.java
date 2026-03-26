package com.craftsmanbro.fulcraft.infrastructure.logging.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogContextTest {

  @Test
  void constructor_normalizesBlankValues() {
    LogContext context = new LogContext(" ", "\t", "", null, "  ", "\n");

    assertThat(context.runId()).isNull();
    assertThat(context.traceId()).isNull();
    assertThat(context.subsystem()).isNull();
    assertThat(context.stage()).isNull();
    assertThat(context.targetClass()).isNull();
    assertThat(context.taskId()).isNull();
    assertThat(context.hasAny()).isFalse();
  }

  @Test
  void hasAny_returnsTrueWhenAnyFieldHasValue() {
    LogContext context = new LogContext("run-1", null, null, null, null, null);

    assertThat(context.hasAny()).isTrue();
  }
}
