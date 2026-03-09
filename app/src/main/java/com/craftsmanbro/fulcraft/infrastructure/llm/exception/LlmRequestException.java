package com.craftsmanbro.fulcraft.infrastructure.llm.exception;

/**
 * Represents a failure to send or receive an LLM request (network/IO/timeout/interrupt errors).
 *
 * <p>This exception is intended to surface user-friendly messages for request transport failures
 * and to signal whether the failure is retryable.
 */
public class LlmRequestException extends LlmProviderException {

  private static final long serialVersionUID = 1L;

  public LlmRequestException(final String message) {
    super(message);
  }

  public LlmRequestException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public LlmRequestException(final String message, final boolean retryable) {
    super(message, retryable);
  }

  public LlmRequestException(final String message, final Throwable cause, final boolean retryable) {
    super(message, cause, retryable);
  }
}
