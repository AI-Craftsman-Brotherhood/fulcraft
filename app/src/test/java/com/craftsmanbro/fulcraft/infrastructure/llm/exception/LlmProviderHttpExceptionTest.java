package com.craftsmanbro.fulcraft.infrastructure.llm.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LlmProviderHttpExceptionTest {

  @Test
  void constructor_setsFieldsAndMessage() {
    LlmProviderHttpException ex = new LlmProviderHttpException(429, "rate limit", true);

    assertEquals(429, ex.getStatusCode());
    assertEquals("rate limit", ex.getResponseBody());
    assertTrue(ex.isRetryable());
    assertTrue(ex.getMessage().contains("LLM provider HTTP error"));
    assertTrue(ex.getMessage().contains("429"));
    assertTrue(ex.getMessage().contains("rate limit"));
  }

  @Test
  void buildMessage_truncatesBodyBeyondLimit() {
    String body = "a".repeat(1200);
    String message = LlmProviderHttpException.buildMessage("prefix", 400, body);

    String truncatedPart = message.substring(message.indexOf(" - ") + 3);
    assertTrue(truncatedPart.startsWith("a".repeat(1000)));
    assertTrue(truncatedPart.endsWith("...(truncated)"));
  }

  @Test
  void buildMessage_handlesNullBody() {
    String message = LlmProviderHttpException.buildMessage("prefix", 500, null);

    assertEquals("prefix: 500 - null", message);
  }

  @Test
  void constructor_withNullBody_preservesNullResponseBody() {
    LlmProviderHttpException ex = new LlmProviderHttpException(500, null, false);

    assertNull(ex.getResponseBody());
    assertTrue(ex.getMessage().contains("null"));
  }

  @Test
  void constructor_withExplicitMessage_usesProvidedMessage() {
    LlmProviderHttpException ex =
        new LlmProviderHttpException("custom message", 503, "service unavailable", true);

    assertEquals("custom message", ex.getMessage());
    assertEquals(503, ex.getStatusCode());
    assertEquals("service unavailable", ex.getResponseBody());
    assertTrue(ex.isRetryable());
    assertNull(ex.getCause());
  }

  @Test
  void buildMessage_doesNotTruncateBodyAtLimit() {
    String body = "b".repeat(1000);
    String message = LlmProviderHttpException.buildMessage("prefix", 418, body);

    assertEquals("prefix: 418 - " + body, message);
  }
}
