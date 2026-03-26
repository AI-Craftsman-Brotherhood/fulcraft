package com.craftsmanbro.fulcraft.infrastructure.llm.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExternalTransmissionDeniedExceptionTest {

  @Test
  void defaultConstructor_includesPolicyGuidance() {
    ExternalTransmissionDeniedException ex = new ExternalTransmissionDeniedException();

    assertTrue(ex.getMessage().contains("External LLM transmission is denied"));
    assertTrue(ex.getMessage().contains("governance.external_transmission"));
  }

  @Test
  void constructor_withMessage_usesCustomMessage() {
    ExternalTransmissionDeniedException ex = new ExternalTransmissionDeniedException("custom");

    assertEquals("custom", ex.getMessage());
  }

  @Test
  void constructor_withMessageAndCause_setsCause() {
    RuntimeException cause = new RuntimeException("cause");

    ExternalTransmissionDeniedException ex =
        new ExternalTransmissionDeniedException("custom", cause);

    assertEquals("custom", ex.getMessage());
    assertSame(cause, ex.getCause());
  }
}
