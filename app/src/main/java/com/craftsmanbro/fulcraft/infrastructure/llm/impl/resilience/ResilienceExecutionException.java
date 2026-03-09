package com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience;

/** Indicates a failure while executing an operation with resilience policies applied. */
public class ResilienceExecutionException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private static final String DEFAULT_MESSAGE = "Resilience execution failed";

  public ResilienceExecutionException(final String message) {
    super(message);
  }

  public ResilienceExecutionException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public ResilienceExecutionException(final Throwable cause) {
    this(DEFAULT_MESSAGE, cause);
  }
}
