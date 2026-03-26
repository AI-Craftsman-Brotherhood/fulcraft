package com.craftsmanbro.fulcraft.infrastructure.logging.impl;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;
import com.craftsmanbro.fulcraft.infrastructure.security.impl.SecretMasker;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Logback layout that outputs logs in JSON format with MDC context.
 *
 * <p>This layout produces structured JSON log entries that include:
 *
 * <ul>
 *   <li>timestamp - ISO-8601 format timestamp
 *   <li>level - log level (INFO, WARN, ERROR, DEBUG)
 *   <li>thread - thread name
 *   <li>logger - logger name
 *   <li>message - the log message (masked for secrets)
 *   <li>runId - run ID from MDC (if present)
 *   <li>traceId - trace ID from MDC (if present)
 *   <li>subsystem - subsystem from MDC (if present)
 *   <li>stage - stage from MDC (if present)
 *   <li>targetClass - target class from MDC (if present)
 *   <li>taskId - task ID from MDC (if present)
 *   <li>stack_trace - exception stack trace (if present, masked)
 * </ul>
 */
public class JsonLayout extends LayoutBase<ILoggingEvent> {

  private final ObjectMapper mapper = new ObjectMapper();

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

  /**
   * Formats the logging event as a JSON string.
   *
   * @param event The logging event to format
   * @return The JSON string representation of the event
   */
  @Override
  public String doLayout(final ILoggingEvent event) {
    final var node = mapper.createObjectNode();
    node.put("timestamp", FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
    node.put("level", event.getLevel().toString());
    node.put("thread", event.getThreadName());
    node.put("logger", event.getLoggerName());
    node.put("message", SecretMasker.mask(event.getFormattedMessage()));
    // Include MDC context if available
    final Map<String, String> mdc = event.getMDCPropertyMap();
    if (mdc != null && !mdc.isEmpty()) {
      addMdcField(node, mdc, Logger.MDC_RUN_ID, "runId");
      addMdcField(node, mdc, Logger.MDC_TRACE_ID, "traceId");
      addMdcField(node, mdc, Logger.MDC_SUBSYSTEM, "subsystem");
      addMdcField(node, mdc, Logger.MDC_STAGE, "stage");
      addMdcField(node, mdc, Logger.MDC_TARGET_CLASS, "targetClass");
      addMdcField(node, mdc, Logger.MDC_TASK_ID, "taskId");
    }
    if (event.getThrowableProxy() != null) {
      node.put(
          "stack_trace", SecretMasker.mask(ThrowableProxyUtil.asString(event.getThrowableProxy())));
    }
    try {
      return mapper.writeValueAsString(node) + "\n";
    } catch (JacksonException e) {
      return "{\"error\": \"Failed to serialize log event\"}\n";
    }
  }

  private void addMdcField(
      final tools.jackson.databind.node.ObjectNode node,
      final Map<String, String> mdc,
      final String mdcKey,
      final String jsonKey) {
    final String value = mdc.get(mdcKey);
    if (value != null && !value.isEmpty()) {
      node.put(jsonKey, SecretMasker.mask(value));
    }
  }
}
