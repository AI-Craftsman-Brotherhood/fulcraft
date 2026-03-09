package com.craftsmanbro.fulcraft.infrastructure.llm.exception;

/** Indicates a failure to parse an LLM response payload. */
public class LlmResponseParseException extends LlmProviderException {

  private static final long serialVersionUID = 1L;

  public LlmResponseParseException(final String message) {
    super(message);
  }

  public LlmResponseParseException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public LlmResponseParseException(final String message, final boolean retryable) {
    super(message, retryable);
  }

  public LlmResponseParseException(
      final String message, final Throwable cause, final boolean retryable) {
    super(message, cause, retryable);
  }
}
