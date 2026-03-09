package com.craftsmanbro.fulcraft.infrastructure.llm.exception;

/** HTTP error from an LLM provider. */
public class LlmProviderHttpException extends LlmProviderException {

  private static final long serialVersionUID = 1L;

  private static final int MAX_RESPONSE_BODY_LENGTH_FOR_MESSAGE = 1000;
  private static final String DEFAULT_MESSAGE_PREFIX = "LLM provider HTTP error";
  private static final String MESSAGE_TRUNCATED_SUFFIX = "...(truncated)";

  private final int statusCode;

  private final String responseBody;

  public LlmProviderHttpException(
      final int statusCode, final String responseBody, final boolean retryable) {
    this(
        buildMessage(DEFAULT_MESSAGE_PREFIX, statusCode, responseBody),
        statusCode,
        responseBody,
        retryable);
  }

  public LlmProviderHttpException(
      final String message,
      final int statusCode,
      final String responseBody,
      final boolean retryable) {
    super(message, retryable);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }

  public static String buildMessage(
      final String prefix, final int statusCode, final String responseBody) {
    return prefix + ": " + statusCode + " - " + truncateForMessage(responseBody);
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getResponseBody() {
    return responseBody;
  }

  private static String truncateForMessage(final String responseBody) {
    // Keep null handling stable by rendering null as the literal "null".
    final String responseBodyText = String.valueOf(responseBody);
    if (responseBodyText.length() <= MAX_RESPONSE_BODY_LENGTH_FOR_MESSAGE) {
      return responseBodyText;
    }
    return responseBodyText.substring(0, MAX_RESPONSE_BODY_LENGTH_FOR_MESSAGE)
        + MESSAGE_TRUNCATED_SUFFIX;
  }
}
