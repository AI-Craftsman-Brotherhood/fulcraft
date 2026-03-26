package com.craftsmanbro.fulcraft.infrastructure.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.craftsmanbro.fulcraft.infrastructure.audit.model.AuditEvent;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.RedactionReport;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AuditEvent} record.
 *
 * <p>Verifies that the record stores and returns values correctly, including null handling.
 */
class AuditEventTest {

  @Test
  void createWithAllFields_returnsCorrectValues() {
    TokenUsage tokenUsage = new TokenUsage(100, 200, 300);
    RedactionReport redactionReport = new RedactionReport(1, 0, 0, 0, 0, 0, 0);

    AuditEvent event =
        new AuditEvent(
            "openai",
            "gpt-4",
            "prompt text",
            "raw prompt text",
            "response text",
            "promptHash123",
            "responseHash456",
            50,
            80,
            tokenUsage,
            redactionReport,
            "success",
            null);

    assertEquals("openai", event.provider());
    assertEquals("gpt-4", event.model());
    assertEquals("prompt text", event.prompt());
    assertEquals("raw prompt text", event.promptRaw());
    assertEquals("response text", event.response());
    assertEquals("promptHash123", event.promptHash());
    assertEquals("responseHash456", event.responseHash());
    assertEquals(50, event.requestChars());
    assertEquals(80, event.responseChars());
    assertNotNull(event.tokenUsage());
    assertEquals(100, event.tokenUsage().getPromptTokens());
    assertNotNull(event.redactionReport());
    assertEquals("success", event.outcome());
    assertNull(event.errorType());
  }

  @Test
  void createWithNullOptionalFields_acceptsNullValues() {
    AuditEvent event =
        new AuditEvent(
            "anthropic",
            "claude-2",
            null,
            null,
            null,
            null,
            null,
            0,
            0,
            null,
            null,
            "error",
            "timeout");

    assertEquals("anthropic", event.provider());
    assertEquals("claude-2", event.model());
    assertNull(event.prompt());
    assertNull(event.promptRaw());
    assertNull(event.response());
    assertNull(event.promptHash());
    assertNull(event.responseHash());
    assertEquals(0, event.requestChars());
    assertEquals(0, event.responseChars());
    assertNull(event.tokenUsage());
    assertNull(event.redactionReport());
    assertEquals("error", event.outcome());
    assertEquals("timeout", event.errorType());
  }

  @Test
  void createWithEmptyRedactionReport_usesEmptyReport() {
    AuditEvent event =
        new AuditEvent(
            "test-provider",
            "test-model",
            "prompt",
            "prompt",
            "response",
            "hash1",
            "hash2",
            10,
            20,
            null,
            RedactionReport.EMPTY,
            "success",
            null);

    assertNotNull(event.redactionReport());
    assertEquals(0, event.redactionReport().totalCount());
  }
}
