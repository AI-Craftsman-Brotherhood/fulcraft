package com.craftsmanbro.fulcraft.infrastructure.llm.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LlmProviderExceptionTest {

  @Test
  void constructor_withMessage_defaultsRetryableFalse() {
    LlmProviderException ex = new LlmProviderException("message");

    assertEquals("message", ex.getMessage());
    assertFalse(ex.isRetryable());
    assertNull(ex.getCause());
  }

  @Test
  void constructor_withMessageAndCause_defaultsRetryableFalse() {
    RuntimeException cause = new RuntimeException("cause");

    LlmProviderException ex = new LlmProviderException("message", cause);

    assertEquals("message", ex.getMessage());
    assertSame(cause, ex.getCause());
    assertFalse(ex.isRetryable());
  }

  @Test
  void constructor_withMessageAndRetryable_setsFlag() {
    LlmProviderException ex = new LlmProviderException("message", true);

    assertTrue(ex.isRetryable());
  }

  @Test
  void constructor_withMessageCauseAndRetryable_setsAllFields() {
    RuntimeException cause = new RuntimeException("cause");

    LlmProviderException ex = new LlmProviderException("message", cause, true);

    assertEquals("message", ex.getMessage());
    assertSame(cause, ex.getCause());
    assertTrue(ex.isRetryable());
  }
}
