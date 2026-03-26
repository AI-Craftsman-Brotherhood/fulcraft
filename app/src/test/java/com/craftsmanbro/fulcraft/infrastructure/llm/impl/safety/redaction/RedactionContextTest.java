package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedactionContextTest {

  @Test
  void consume_shouldReturnAndClearValues() {
    RedactionReport report = new RedactionReport(1, 0, 0, 0, 0);
    RedactionContext.setPrompt("prompt");
    RedactionContext.setReport(report);

    assertThat(RedactionContext.consumePrompt()).isEqualTo("prompt");
    assertThat(RedactionContext.consumeReport()).isEqualTo(report);

    assertThat(RedactionContext.consumePrompt()).isNull();
    assertThat(RedactionContext.consumeReport()).isNull();
  }

  @Test
  void setNull_shouldClearValues() {
    RedactionContext.setPrompt("prompt");
    RedactionContext.setReport(new RedactionReport(0, 1, 0, 0, 0));

    RedactionContext.setPrompt(null);
    RedactionContext.setReport(null);

    assertThat(RedactionContext.consumePrompt()).isNull();
    assertThat(RedactionContext.consumeReport()).isNull();
  }

  @Test
  void consume_withoutPriorSet_returnsNull() {
    RedactionContext.consumePrompt();
    RedactionContext.consumeReport();

    assertThat(RedactionContext.consumePrompt()).isNull();
    assertThat(RedactionContext.consumeReport()).isNull();
  }
}
