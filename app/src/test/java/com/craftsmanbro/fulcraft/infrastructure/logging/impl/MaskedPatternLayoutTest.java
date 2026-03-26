package com.craftsmanbro.fulcraft.infrastructure.logging.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

class MaskedPatternLayoutTest {

  @Test
  void doLayout_masksSecrets() {
    LoggerContext context = new LoggerContext();
    MaskedPatternLayout layout = new MaskedPatternLayout();
    layout.setContext(context);
    layout.setPattern("%msg");
    layout.start();

    ILoggingEvent event = createEvent(context, "token=abc123");
    String output = layout.doLayout(event);

    assertTrue(output.contains("token=****"));
    assertFalse(output.contains("abc123"));
  }

  @Test
  void doLayout_keepsNonSecretContent() {
    LoggerContext context = new LoggerContext();
    MaskedPatternLayout layout = new MaskedPatternLayout();
    layout.setContext(context);
    layout.setPattern("%msg");
    layout.start();

    ILoggingEvent event = createEvent(context, "hello world");
    String output = layout.doLayout(event);

    assertTrue(output.contains("hello world"));
  }

  private ILoggingEvent createEvent(LoggerContext context, String message) {
    LoggingEvent event = new LoggingEvent();
    event.setLoggerContext(context);
    event.setLevel(Level.INFO);
    event.setLoggerName("test");
    event.setThreadName("main");
    event.setMessage(message);
    event.setTimeStamp(System.currentTimeMillis());
    return event;
  }
}
