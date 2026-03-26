package com.craftsmanbro.fulcraft.infrastructure.audit.impl;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.audit.contract.AuditLogPort;
import com.craftsmanbro.fulcraft.infrastructure.audit.model.AuditEvent;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.RedactionReport;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class AuditLogger implements AuditLogPort {

  private static final String DEFAULT_AUDIT_PATH = ".ful/audit.jsonl";

  private final boolean enabled;

  private final boolean includeRaw;

  private final String sessionId;

  private final String projectId;

  private final Path logPath;

  private final ObjectMapper mapper;

  public AuditLogger(final Config config, final Path projectRoot) {
    final Config.AuditConfig auditConfig = config != null ? config.getAudit() : null;
    this.enabled = auditConfig != null && auditConfig.isEnabled();
    this.includeRaw = auditConfig != null && Boolean.TRUE.equals(auditConfig.getIncludeRaw());
    this.sessionId = UUID.randomUUID().toString();
    this.projectId = resolveProjectId(config);
    this.logPath = resolveLogPath(auditConfig, projectRoot);
    this.mapper = new ObjectMapper();
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void logExchange(final AuditEvent event) {
    if (!enabled || event == null) {
      return;
    }
    try {
      writeLine(createLogEntry(event));
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage("infra.common.log.message", "Failed to write audit log entry."),
          e);
    }
  }

  private ObjectNode createLogEntry(final AuditEvent event) {
    final ObjectNode root = mapper.createObjectNode();
    root.put("timestamp", Instant.now().toString());
    root.put("session_id", sessionId);
    root.put("project_id", projectId);
    root.put("provider", event.provider());
    root.put("model", event.model());
    root.put("request_chars", event.requestChars());
    root.put("response_chars", event.responseChars());
    root.put("prompt_hash", event.promptHash());
    root.put("response_hash", event.responseHash());
    root.put("outcome", event.outcome());
    putNullableString(root, "error_type", event.errorType());
    appendTokenUsage(root, event.tokenUsage());
    appendRedactionSummary(root, event.redactionReport());
    appendRawContent(root, event);
    return root;
  }

  private void putNullableString(
      final ObjectNode root, final String fieldName, final String value) {
    if (value == null) {
      root.putNull(fieldName);
      return;
    }
    root.put(fieldName, value);
  }

  private void appendTokenUsage(final ObjectNode root, final TokenUsage tokenUsage) {
    if (tokenUsage == null) {
      return;
    }
    final ObjectNode tokenUsageNode = root.putObject("token_usage");
    tokenUsageNode.put("prompt_tokens", tokenUsage.getPromptTokens());
    tokenUsageNode.put("completion_tokens", tokenUsage.getCompletionTokens());
    tokenUsageNode.put("total_tokens", tokenUsage.getTotalTokens());
  }

  private void appendRedactionSummary(final ObjectNode root, final RedactionReport report) {
    final RedactionReport safeReport = report != null ? report : RedactionReport.EMPTY;
    final ObjectNode redactionSummaryNode = root.putObject("redaction_summary");
    redactionSummaryNode.put("email_count", safeReport.emailCount());
    redactionSummaryNode.put("credit_card_count", safeReport.creditCardCount());
    redactionSummaryNode.put("pem_key_count", safeReport.pemKeyCount());
    redactionSummaryNode.put("auth_token_count", safeReport.authTokenCount());
    redactionSummaryNode.put("jwt_count", safeReport.jwtCount());
    redactionSummaryNode.put("dictionary_count", safeReport.dictionaryCount());
    redactionSummaryNode.put("ml_entity_count", safeReport.mlEntityCount());
  }

  private void appendRawContent(final ObjectNode root, final AuditEvent event) {
    if (!includeRaw) {
      return;
    }
    final String rawPrompt = event.promptRaw() != null ? event.promptRaw() : event.prompt();
    final String redactedPrompt = event.prompt();
    root.put("prompt_raw", rawPrompt);
    root.put("response_raw", event.response());
    if (rawPrompt == null || redactedPrompt == null || rawPrompt.equals(redactedPrompt)) {
      return;
    }
    root.put("prompt_redacted", redactedPrompt);
  }

  private void writeLine(final ObjectNode logEntryNode) throws IOException {
    if (logPath == null) {
      return;
    }
    final Path parentDirectory = logPath.getParent();
    if (parentDirectory != null) {
      Files.createDirectories(parentDirectory);
    }
    final String jsonLine = mapper.writeValueAsString(logEntryNode) + System.lineSeparator();
    Files.writeString(logPath, jsonLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  private Path resolveLogPath(final Config.AuditConfig auditConfig, final Path projectRoot) {
    final String configuredLogPath = auditConfig != null ? auditConfig.getLogPath() : null;
    if (configuredLogPath == null) {
      return projectRoot == null
          ? Path.of(DEFAULT_AUDIT_PATH)
          : projectRoot.resolve(DEFAULT_AUDIT_PATH);
    }
    if (projectRoot == null) {
      return Path.of(configuredLogPath);
    }
    return projectRoot.resolve(configuredLogPath);
  }

  private String resolveProjectId(final Config config) {
    if (config == null || config.getProject() == null) {
      return "";
    }
    final String configuredProjectId = config.getProject().getId();
    return configuredProjectId != null ? configuredProjectId : "";
  }
}
