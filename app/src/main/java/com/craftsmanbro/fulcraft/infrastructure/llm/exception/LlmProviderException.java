package com.craftsmanbro.fulcraft.infrastructure.llm.exception;

/** Base runtime exception for LLM provider failures. */
public class LlmProviderException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final boolean retryable;

  public LlmProviderException(final String message) {
    this(message, false);
  }

  public LlmProviderException(final String message, final Throwable cause) {
    this(message, cause, false);
  }

  public LlmProviderException(final String message, final boolean retryable) {
    this(message, null, retryable);
  }

  public LlmProviderException(
      final String message, final Throwable cause, final boolean retryable) {
    super(message, cause);
    this.retryable = retryable;
  }

  public boolean isRetryable() {
    return retryable;
  }
}
