package com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.audit.impl.AuditLogger;
import com.craftsmanbro.fulcraft.infrastructure.audit.model.AuditEvent;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.RedactionContext;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.RedactionReport;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LlmAuditLoggingClientTest {

  @AfterEach
  void clearRedactionContext() {
    RedactionContext.consumePrompt();
    RedactionContext.consumeReport();
  }

  @Test
  void generateTest_auditDisabled_skipsLoggingAndClearsContext() {
    AuditLogger auditLogger = mock(AuditLogger.class);
    when(auditLogger.isEnabled()).thenReturn(false);
    LlmClientPort delegate = mock(LlmClientPort.class);
    when(delegate.generateTest(
            org.mockito.Mockito.anyString(), org.mockito.Mockito.any(Config.LlmConfig.class)))
        .thenReturn("ok");

    RedactionContext.setPrompt("raw");
    RedactionContext.setReport(new RedactionReport(1, 0, 0, 0, 0, 0, 0));

    LlmAuditLoggingClient client = new LlmAuditLoggingClient(delegate, auditLogger);

    String result = client.generateTest("redacted", new Config.LlmConfig());

    assertEquals("ok", result);
    verify(auditLogger, never()).logExchange(org.mockito.Mockito.any());
    assertNull(RedactionContext.consumePrompt());
    assertNull(RedactionContext.consumeReport());
  }

  @Test
  void generateTest_auditEnabled_logsSuccess() throws Exception {
    AuditLogger auditLogger = mock(AuditLogger.class);
    when(auditLogger.isEnabled()).thenReturn(true);

    TokenUsage usage = new TokenUsage(2, 3, 5);
    UsageAwareClient delegate = new UsageAwareClient("response", usage);

    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("model-x");

    RedactionReport report = new RedactionReport(1, 1, 0, 0, 0, 0, 0);
    RedactionContext.setPrompt("raw");
    RedactionContext.setReport(report);

    LlmAuditLoggingClient client = new LlmAuditLoggingClient(delegate, auditLogger);

    String result = client.generateTest("redacted", llmConfig);

    assertEquals("response", result);
    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditLogger).logExchange(captor.capture());

    AuditEvent event = captor.getValue();
    assertEquals("mock", event.provider());
    assertEquals("model-x", event.model());
    assertEquals("redacted", event.prompt());
    assertEquals("raw", event.promptRaw());
    assertEquals("response", event.response());
    assertEquals("success", event.outcome());
    assertNull(event.errorType());
    assertEquals("redacted".length(), event.requestChars());
    assertEquals("response".length(), event.responseChars());
    assertEquals(sha256("redacted"), event.promptHash());
    assertEquals(sha256("response"), event.responseHash());
    assertEquals(report, event.redactionReport());
    assertEquals(usage, event.tokenUsage());
  }

  @Test
  void generateTest_auditEnabled_logsFailureAndRethrows() throws Exception {
    AuditLogger auditLogger = mock(AuditLogger.class);
    when(auditLogger.isEnabled()).thenReturn(true);

    LlmClientPort delegate = mock(LlmClientPort.class);
    when(delegate.generateTest(
            org.mockito.Mockito.anyString(), org.mockito.Mockito.any(Config.LlmConfig.class)))
        .thenThrow(new IllegalStateException("boom"));

    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    llmConfig.setModelName("model-x");

    RedactionReport report = new RedactionReport(0, 1, 0, 0, 0, 0, 0);
    RedactionContext.setPrompt("raw");
    RedactionContext.setReport(report);

    LlmAuditLoggingClient client = new LlmAuditLoggingClient(delegate, auditLogger);

    assertThrows(IllegalStateException.class, () -> client.generateTest("redacted", llmConfig));

    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditLogger).logExchange(captor.capture());

    AuditEvent event = captor.getValue();
    assertEquals("failure", event.outcome());
    assertEquals("IllegalStateException", event.errorType());
    assertEquals("redacted", event.prompt());
    assertEquals("raw", event.promptRaw());
    assertNull(event.response());
    assertNull(event.responseHash());
    assertEquals(0L, event.responseChars());
    assertEquals(sha256("redacted"), event.promptHash());
    assertEquals(report, event.redactionReport());
  }

  @Test
  void generateTest_auditEnabled_withNullConfigAndPrompt_usesEmptyProviderAndModel() {
    AuditLogger auditLogger = mock(AuditLogger.class);
    when(auditLogger.isEnabled()).thenReturn(true);

    LlmClientPort delegate = mock(LlmClientPort.class);
    when(delegate.generateTest((String) null, (Config.LlmConfig) null)).thenReturn("response");

    LlmAuditLoggingClient client = new LlmAuditLoggingClient(delegate, auditLogger);
    client.generateTest((String) null, (Config.LlmConfig) null);

    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditLogger).logExchange(captor.capture());

    AuditEvent event = captor.getValue();
    assertEquals("", event.provider());
    assertEquals("", event.model());
    assertNull(event.promptHash());
    assertEquals(0L, event.requestChars());
    assertEquals(sha256Unchecked("response"), event.responseHash());
  }

  @Test
  void getLastUsage_withoutUsageAwareDelegate_returnsEmpty() {
    AuditLogger auditLogger = mock(AuditLogger.class);
    LlmClientPort delegate = mock(LlmClientPort.class);

    LlmAuditLoggingClient client = new LlmAuditLoggingClient(delegate, auditLogger);

    assertTrue(client.getLastUsage().isEmpty());
  }

  @Test
  void delegatesHealthProfileAndClearContext() {
    AuditLogger auditLogger = mock(AuditLogger.class);
    LlmClientPort delegate = mock(LlmClientPort.class);
    ProviderProfile profile = new ProviderProfile("p", Set.of(), Optional.empty());
    when(delegate.isHealthy()).thenReturn(false);
    when(delegate.profile()).thenReturn(profile);

    LlmAuditLoggingClient client = new LlmAuditLoggingClient(delegate, auditLogger);

    assertFalse(client.isHealthy());
    assertEquals(profile, client.profile());
    client.clearContext();

    verify(delegate).clearContext();
  }

  private static String sha256(String value) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash);
  }

  private static String sha256Unchecked(String value) {
    try {
      return sha256(value);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static final class UsageAwareClient implements LlmClientPort, TokenUsageAware {
    private final String response;
    private final TokenUsage usage;

    private UsageAwareClient(String response, TokenUsage usage) {
      this.response = response;
      this.usage = usage;
    }

    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      return response;
    }

    @Override
    public boolean isHealthy() {
      return true;
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile("audit", Set.of(), Optional.empty());
    }

    @Override
    public void clearContext() {}

    @Override
    public Optional<TokenUsage> getLastUsage() {
      return Optional.ofNullable(usage);
    }
  }
}
