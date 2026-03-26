package com.craftsmanbro.fulcraft.infrastructure.llm.resilience;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience.ResilienceExecutionException;
import org.junit.jupiter.api.Test;

class ResilienceExecutionExceptionTest {

  @Test
  void messageConstructor_setsMessage() {
    ResilienceExecutionException exception = new ResilienceExecutionException("custom");

    assertEquals("custom", exception.getMessage());
  }

  @Test
  void messageAndCauseConstructor_setsCause() {
    RuntimeException cause = new RuntimeException("cause");
    ResilienceExecutionException exception = new ResilienceExecutionException("custom", cause);

    assertEquals("custom", exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void causeOnlyConstructor_usesDefaultMessage() {
    RuntimeException cause = new RuntimeException("cause");
    ResilienceExecutionException exception = new ResilienceExecutionException(cause);

    assertEquals("Resilience execution failed", exception.getMessage());
    assertEquals(cause, exception.getCause());
  }
}
