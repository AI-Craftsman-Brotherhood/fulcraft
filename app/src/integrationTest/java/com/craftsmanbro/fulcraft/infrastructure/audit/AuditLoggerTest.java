package com.craftsmanbro.fulcraft.infrastructure.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.audit.impl.AuditLogger;
import com.craftsmanbro.fulcraft.infrastructure.audit.model.AuditEvent;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.RedactionReport;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests for {@link AuditLogger}.
 *
 * <p>Verifies audit logging behavior including:
 *
 * <ul>
 *   <li>Disabled state when config is disabled
 *   <li>JSONL file writing when enabled
 *   <li>Proper field serialization
 *   <li>Null event handling
 *   <li>Raw content inclusion when configured
 * </ul>
 */
class AuditLoggerTest {

  @TempDir Path tempDir;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void constructor_withNullConfig_disablesLogging() {
    AuditLogger logger = new AuditLogger(null, tempDir);
    assertFalse(logger.isEnabled());
  }

  @Test
  void constructor_withDisabledAuditConfig_disablesLogging() {
    Config config = new Config();
    Config.AuditConfig auditConfig = new Config.AuditConfig();
    auditConfig.setEnabled(false);
    config.setAudit(auditConfig);

    AuditLogger logger = new AuditLogger(config, tempDir);
    assertFalse(logger.isEnabled());
  }

  @Test
  void constructor_withEnabledAuditConfig_enablesLogging() {
    Config config = createEnabledConfig(null, false);

    AuditLogger logger = new AuditLogger(config, tempDir);
    assertTrue(logger.isEnabled());
  }

  @Test
  void logExchange_withNullEvent_doesNotWriteAnything() throws IOException {
    Config config = createEnabledConfig("audit.jsonl", false);
    AuditLogger logger = new AuditLogger(config, tempDir);

    logger.logExchange(null);

    Path logFile = tempDir.resolve("audit.jsonl");
    assertFalse(Files.exists(logFile), "Log file should not be created for null event");
  }

  @Test
  void logExchange_whenDisabled_doesNotWriteAnything() throws IOException {
    Config config = new Config();
    AuditLogger logger = new AuditLogger(config, tempDir);

    AuditEvent event = createBasicEvent();
    logger.logExchange(event);

    List<Path> files = Files.list(tempDir).toList();
    assertTrue(files.isEmpty(), "No files should be created when disabled");
  }

  @Test
  void logExchange_writesValidJsonl() throws IOException {
    Config config = createEnabledConfig("audit.jsonl", false);
    AuditLogger logger = new AuditLogger(config, tempDir);

    AuditEvent event = createBasicEvent();
    logger.logExchange(event);

    Path logFile = tempDir.resolve("audit.jsonl");
    assertTrue(Files.exists(logFile), "Log file should exist");

    String content = Files.readString(logFile);
    assertFalse(content.isBlank(), "Log file should not be empty");

    JsonNode json = objectMapper.readTree(content.trim());
    assertNotNull(json.get("timestamp"));
    assertNotNull(json.get("session_id"));
    assertEquals("test-provider", json.get("provider").asText());
    assertEquals("test-model", json.get("model").asText());
    assertEquals(50, json.get("request_chars").asInt());
    assertEquals(80, json.get("response_chars").asInt());
    assertEquals("success", json.get("outcome").asText());
    assertTrue(json.get("error_type").isNull());
    assertFalse(json.has("token_usage"));
    assertFalse(json.has("prompt_raw"));
    assertFalse(json.has("response_raw"));
    assertFalse(json.has("prompt_redacted"));
  }

  @Test
  void logExchange_includesTokenUsage() throws IOException {
    Config config = createEnabledConfig("audit.jsonl", false);
    AuditLogger logger = new AuditLogger(config, tempDir);

    TokenUsage tokenUsage = new TokenUsage(100, 200, 300);
    AuditEvent event =
        new AuditEvent(
            "provider",
            "model",
            "prompt",
            "promptRaw",
            "response",
            "hash1",
            "hash2",
            10,
            20,
            tokenUsage,
            null,
            "success",
            null);
    logger.logExchange(event);

    Path logFile = tempDir.resolve("audit.jsonl");
    String content = Files.readString(logFile);
    JsonNode json = objectMapper.readTree(content.trim());

    JsonNode usageNode = json.get("token_usage");
    assertNotNull(usageNode);
    assertEquals(100, usageNode.get("prompt_tokens").asLong());
    assertEquals(200, usageNode.get("completion_tokens").asLong());
    assertEquals(300, usageNode.get("total_tokens").asLong());
  }

  @Test
  void logExchange_includesRedactionSummary() throws IOException {
    Config config = createEnabledConfig("audit.jsonl", false);
    AuditLogger logger = new AuditLogger(config, tempDir);

    RedactionReport report = new RedactionReport(1, 2, 0, 1, 0, 3, 2);
    AuditEvent event =
        new AuditEvent(
            "provider",
            "model",
            "prompt",
            "promptRaw",
            "response",
            "hash1",
            "hash2",
            10,
            20,
            null,
            report,
            "success",
            null);
    logger.logExchange(event);

    Path logFile = tempDir.resolve("audit.jsonl");
    String content = Files.readString(logFile);
    JsonNode json = objectMapper.readTree(content.trim());

    JsonNode redaction = json.get("redaction_summary");
    assertNotNull(redaction);
    assertEquals(1, redaction.get("email_count").asInt());
    assertEquals(2, redaction.get("credit_card_count").asInt());
    assertEquals(0, redaction.get("pem_key_count").asInt());
    assertEquals(1, redaction.get("auth_token_count").asInt());
    assertEquals(0, redaction.get("jwt_count").asInt());
    assertEquals(3, redaction.get("dictionary_count").asInt());
    assertEquals(2, redaction.get("ml_entity_count").asInt());
  }

  @Test
  void logExchange_usesEmptyRedactionIfNull() throws IOException {
    Config config = createEnabledConfig("audit.jsonl", false);
    AuditLogger logger = new AuditLogger(config, tempDir);

    AuditEvent event =
        new AuditEvent(
            "provider",
            "model",
            "prompt",
            "promptRaw",
            "response",
            "hash1",
            "hash2",
            10,
            20,
            null,
            null,
            "success",
            null);
    logger.logExchange(event);

    Path logFile = tempDir.resolve("audit.jsonl");
    String content = Files.readString(logFile);
    JsonNode json = objectMapper.readTree(content.trim());

    JsonNode redaction = json.get("redaction_summary");
    assertNotNull(redaction, "Redaction summary should always be present");
    assertEquals(0, redaction.get("email_count").asInt());
  }

  @Test
  void logExchange_includesErrorTypeWhenPresent() throws IOException {
    Config config = createEnabledConfig("audit.jsonl", false);
    AuditLogger logger = new AuditLogger(config, tempDir);

    AuditEvent event =
        new AuditEvent(
            "provider",
            "model",
            "prompt",
            "promptRaw",
            "response",
            "hash1",
            "hash2",
            10,
            20,
            null,
            null,
            "error",
            "rate_limit_exceeded");
    logger.logExchange(event);

    Path logFile = tempDir.resolve("audit.jsonl");
    String content = Files.readString(logFile);
    JsonNode json = objectMapper.readTree(content.trim());

    assertEquals("error", json.get("outcome").asText());
    assertEquals("rate_limit_exceeded", json.get("error_type").asText());
  }

  @Test
  void logExchange_withIncludeRaw_writesRawContent() throws IOException {
    Config config = createEnabledConfig("audit.jsonl", true);
    AuditLogger logger = new AuditLogger(config, tempDir);

    AuditEvent event =
        new AuditEvent(
            "provider",
            "model",
            "redacted prompt",
            "original prompt with PII",
            "response text",
            "hash1",
            "hash2",
            10,
            20,
            null,
            null,
            "success",
            null);
    logger.logExchange(event);

    Path logFile = tempDir.resolve("audit.jsonl");
    String content = Files.readString(logFile);
    JsonNode json = objectMapper.readTree(content.trim());

    assertEquals("original prompt with PII", json.get("prompt_raw").asText());
    assertEquals("response text", json.get("response_raw").asText());
    assertEquals("redacted prompt", json.get("prompt_redacted").asText());
  }

  @Test
  void logExchange_withIncludeRaw_usesPromptWhenRawIsNull() throws IOException {
    Config config = createEnabledConfig("audit.jsonl", true);
    AuditLogger logger = new AuditLogger(config, tempDir);

    AuditEvent event =
        new AuditEvent(
            "provider",
            "model",
            "prompt text",
            null,
            "response",
            "hash1",
            "hash2",
            10,
            20,
            null,
            null,
            "success",
            null);
    logger.logExchange(event);

    Path logFile = tempDir.resolve("audit.jsonl");
    String content = Files.readString(logFile);
    JsonNode json = objectMapper.readTree(content.trim());

    assertEquals("prompt text", json.get("prompt_raw").asText());
    assertFalse(
        json.has("prompt_redacted"), "Should not have redacted field when raw equals prompt");
  }

  @Test
  void constructor_withNullIncludeRaw_treatsRawLoggingAsDisabled() throws IOException {
    Config config = new Config();
    Config.AuditConfig auditConfig = new Config.AuditConfig();
    auditConfig.setEnabled(true);
    auditConfig.setLogPath("audit.jsonl");
    auditConfig.setIncludeRaw(null);
    config.setAudit(auditConfig);

    AuditLogger logger = new AuditLogger(config, tempDir);
    logger.logExchange(
        new AuditEvent(
            "provider",
            "model",
            "redacted prompt",
            "raw prompt with pii",
            "response",
            "hash1",
            "hash2",
            10,
            20,
            null,
            null,
            "success",
            null));

    String content = Files.readString(tempDir.resolve("audit.jsonl"));
    JsonNode json = objectMapper.readTree(content.trim());
    assertFalse(json.has("prompt_raw"));
    assertFalse(json.has("response_raw"));
    assertFalse(json.has("prompt_redacted"));
  }

  @Test
  void logExchange_appendsToExistingFile() throws IOException {
    Config config = createEnabledConfig("audit.jsonl", false);
    AuditLogger logger = new AuditLogger(config, tempDir);

    logger.logExchange(createBasicEvent());
    logger.logExchange(createBasicEvent());

    Path logFile = tempDir.resolve("audit.jsonl");
    List<String> lines = Files.readAllLines(logFile);
    assertEquals(2, lines.size(), "Should have two log entries");
  }

  @Test
  void constructor_createsSubdirectoryIfNeeded() throws IOException {
    Config config = createEnabledConfig("subdir/audit.jsonl", false);
    AuditLogger logger = new AuditLogger(config, tempDir);

    logger.logExchange(createBasicEvent());

    Path logFile = tempDir.resolve("subdir/audit.jsonl");
    assertTrue(Files.exists(logFile), "Log file in subdirectory should exist");
  }

  @Test
  void constructor_usesDefaultPathWhenNotConfigured() throws IOException {
    Config config = new Config();
    Config.AuditConfig auditConfig = new Config.AuditConfig();
    auditConfig.setEnabled(true);
    config.setAudit(auditConfig);

    AuditLogger logger = new AuditLogger(config, tempDir);
    logger.logExchange(createBasicEvent());

    Path defaultLogFile = tempDir.resolve(".ful/audit.jsonl");
    assertTrue(Files.exists(defaultLogFile), "Default log path should be used");
  }

  @Test
  void constructor_withNullProjectRoot_usesConfiguredLogPath() throws IOException {
    Path configuredLogFile = tempDir.resolve("custom/audit.jsonl");
    Config config = createEnabledConfig(configuredLogFile.toString(), false);

    AuditLogger logger = new AuditLogger(config, null);
    logger.logExchange(createBasicEvent());

    assertTrue(Files.exists(configuredLogFile), "Configured log path should be used");
  }

  @Test
  void constructor_includesProjectIdWhenConfigured() throws IOException {
    Config config = new Config();
    Config.AuditConfig auditConfig = new Config.AuditConfig();
    auditConfig.setEnabled(true);
    auditConfig.setLogPath("audit.jsonl");
    config.setAudit(auditConfig);
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setId("test-project-123");
    config.setProject(projectConfig);

    AuditLogger logger = new AuditLogger(config, tempDir);
    logger.logExchange(createBasicEvent());

    Path logFile = tempDir.resolve("audit.jsonl");
    String content = Files.readString(logFile);
    JsonNode json = objectMapper.readTree(content.trim());

    assertEquals("test-project-123", json.get("project_id").asText());
  }

  @Test
  void constructor_withoutProjectConfig_usesEmptyProjectId() throws IOException {
    Config config = createEnabledConfig("audit.jsonl", false);
    AuditLogger logger = new AuditLogger(config, tempDir);

    logger.logExchange(createBasicEvent());

    Path logFile = tempDir.resolve("audit.jsonl");
    JsonNode json = objectMapper.readTree(Files.readString(logFile).trim());

    assertEquals("", json.get("project_id").asText());
  }

  private Config createEnabledConfig(String logPath, boolean includeRaw) {
    Config config = new Config();
    Config.AuditConfig auditConfig = new Config.AuditConfig();
    auditConfig.setEnabled(true);
    if (logPath != null) {
      auditConfig.setLogPath(logPath);
    }
    auditConfig.setIncludeRaw(includeRaw);
    config.setAudit(auditConfig);
    return config;
  }

  private AuditEvent createBasicEvent() {
    return new AuditEvent(
        "test-provider",
        "test-model",
        "test prompt",
        "test prompt raw",
        "test response",
        "hash1",
        "hash2",
        50,
        80,
        null,
        null,
        "success",
        null);
  }
}
