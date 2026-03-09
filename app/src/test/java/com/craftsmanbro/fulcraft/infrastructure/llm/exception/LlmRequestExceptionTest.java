package com.craftsmanbro.fulcraft.infrastructure.llm.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LlmRequestExceptionTest {

  @Test
  void constructor_withMessage_defaultsRetryableFalseAndNoCause() {
    LlmRequestException ex = new LlmRequestException("message");

    assertEquals("message", ex.getMessage());
    assertFalse(ex.isRetryable());
    assertNull(ex.getCause());
  }

  @Test
  void constructor_withMessageAndCause_defaultsRetryableFalse() {
    RuntimeException cause = new RuntimeException("cause");

    LlmRequestException ex = new LlmRequestException("message", cause);

    assertEquals("message", ex.getMessage());
    assertSame(cause, ex.getCause());
    assertFalse(ex.isRetryable());
  }

  @Test
  void constructor_withRetryable_setsFlag() {
    LlmRequestException ex = new LlmRequestException("message", true);

    assertTrue(ex.isRetryable());
  }

  @Test
  void constructor_withCauseAndRetryable_preservesMessageAndCause() {
    RuntimeException cause = new RuntimeException("cause");

    LlmRequestException ex = new LlmRequestException("message", cause, true);

    assertEquals("message", ex.getMessage());
    assertSame(cause, ex.getCause());
    assertTrue(ex.isRetryable());
  }
}
