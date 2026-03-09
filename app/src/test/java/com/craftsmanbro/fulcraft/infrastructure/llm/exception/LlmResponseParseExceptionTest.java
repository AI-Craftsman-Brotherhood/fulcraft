package com.craftsmanbro.fulcraft.infrastructure.llm.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LlmResponseParseExceptionTest {

  @Test
  void constructor_withMessage_defaultsRetryableFalseAndNoCause() {
    LlmResponseParseException ex = new LlmResponseParseException("message");

    assertEquals("message", ex.getMessage());
    assertFalse(ex.isRetryable());
    assertNull(ex.getCause());
  }

  @Test
  void constructor_withMessageAndCause_defaultsRetryableFalse() {
    RuntimeException cause = new RuntimeException("cause");

    LlmResponseParseException ex = new LlmResponseParseException("message", cause);

    assertEquals("message", ex.getMessage());
    assertSame(cause, ex.getCause());
    assertFalse(ex.isRetryable());
  }

  @Test
  void constructor_withRetryable_setsFlag() {
    LlmResponseParseException ex = new LlmResponseParseException("message", true);

    assertTrue(ex.isRetryable());
  }

  @Test
  void constructor_withCauseAndRetryable_preservesMessageAndCause() {
    RuntimeException cause = new RuntimeException("cause");

    LlmResponseParseException ex = new LlmResponseParseException("message", cause, true);

    assertEquals("message", ex.getMessage());
    assertSame(cause, ex.getCause());
    assertTrue(ex.isRetryable());
  }
}
