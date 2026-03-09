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
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Logback layout that outputs logs in YAML format with MDC context.
 *
 * <p>Produces structured YAML log entries separated by document delimiters.
 */
public class YamlLayout extends LayoutBase<ILoggingEvent> {

  private final ObjectMapper mapper;

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

  public YamlLayout() {
    this.mapper = YAMLMapper.builder().build();
  }

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
      // Use --- delimiter for YAML stream
      String yaml = mapper.writeValueAsString(node);
      if (!yaml.endsWith("\n")) {
        yaml = yaml + "\n";
      }
      return "---\n" + yaml;
    } catch (JacksonException e) {
      return "---\nerror: \"Failed to serialize log event\"\n";
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
