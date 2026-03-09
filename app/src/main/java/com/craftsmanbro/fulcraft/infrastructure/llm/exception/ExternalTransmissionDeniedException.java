package com.craftsmanbro.fulcraft.infrastructure.llm.exception;

/**
 * Exception thrown when external LLM transmission is blocked by governance policy.
 *
 * <p>This exception is thrown when {@code governance.external_transmission: deny} is configured,
 * ensuring that no external API calls can be made to cloud LLM providers.
 *
 * <p>This is a runtime exception as it represents a policy violation that should cause immediate
 * failure with a clear error message.
 */
public class ExternalTransmissionDeniedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private static final String DEFAULT_MESSAGE =
      "External LLM transmission is denied by governance policy. "
          + "Set governance.external_transmission to 'allow' to enable cloud LLM calls.";

  /** Creates an exception with the default message. */
  public ExternalTransmissionDeniedException() {
    super(DEFAULT_MESSAGE);
  }

  /**
   * Creates an exception with a custom message.
   *
   * @param message the detail message
   */
  public ExternalTransmissionDeniedException(final String message) {
    super(message);
  }

  /**
   * Creates an exception with a custom message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public ExternalTransmissionDeniedException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
