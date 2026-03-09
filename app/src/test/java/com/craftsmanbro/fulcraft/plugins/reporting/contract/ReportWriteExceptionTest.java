package com.craftsmanbro.fulcraft.plugins.reporting.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ReportWriteExceptionTest {

  @Test
  void constructor_withMessage_setsMessageAndNullCause() {
    ReportWriteException ex = new ReportWriteException("failed");

    assertEquals("failed", ex.getMessage());
    assertNull(ex.getCause());
  }

  @Test
  void constructor_withMessageAndCause_setsMessageAndCause() {
    RuntimeException cause = new RuntimeException("boom");

    ReportWriteException ex = new ReportWriteException("failed", cause);

    assertEquals("failed", ex.getMessage());
    assertSame(cause, ex.getCause());
  }

  @Test
  void constructor_withCause_setsCauseAndMessageFromCause() {
    RuntimeException cause = new RuntimeException("root");

    ReportWriteException ex = new ReportWriteException(cause);

    assertSame(cause, ex.getCause());
    assertEquals(cause.toString(), ex.getMessage());
  }
}
