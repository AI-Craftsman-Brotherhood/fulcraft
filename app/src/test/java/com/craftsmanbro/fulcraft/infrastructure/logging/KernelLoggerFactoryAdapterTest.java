package com.craftsmanbro.fulcraft.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.KernelLoggerFactoryAdapter;
import com.craftsmanbro.fulcraft.logging.LoggerPort;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class KernelLoggerFactoryAdapterTest {

  @Test
  void loggerFormatsAndPrefixesMessages() {
    KernelLoggerFactoryAdapter adapter = new KernelLoggerFactoryAdapter();
    LoggerPort logger = adapter.getLogger(KernelLoggerFactoryAdapterTest.class);

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger targetLogger = attachAppender(appender);
    Level previousLevel = targetLogger.getLevel();
    targetLogger.setLevel(Level.DEBUG);

    try {
      logger.info("hello {}, {}", "world", 42);

      ILoggingEvent event = lastEvent(appender);
      assertTrue(event.getFormattedMessage().contains("hello world, 42"));
      assertTrue(
          event
              .getFormattedMessage()
              .startsWith(
                  "[com.craftsmanbro.fulcraft.infrastructure.logging.KernelLoggerFactoryAdapterTest]"));
    } finally {
      detachAppender(targetLogger, appender, previousLevel);
    }
  }

  @Test
  void loggerUsesUnknownNameWhenTypeIsNull() {
    KernelLoggerFactoryAdapter adapter = new KernelLoggerFactoryAdapter();
    LoggerPort logger = adapter.getLogger(null);

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger targetLogger = attachAppender(appender);
    Level previousLevel = targetLogger.getLevel();
    targetLogger.setLevel(Level.INFO);

    try {
      logger.info("hello");

      ILoggingEvent event = lastEvent(appender);
      assertEquals("[unknown] hello", event.getFormattedMessage());
    } finally {
      detachAppender(targetLogger, appender, previousLevel);
    }
  }

  @Test
  void loggerHandlesNullMessageWithThrowable() {
    KernelLoggerFactoryAdapter adapter = new KernelLoggerFactoryAdapter();
    LoggerPort logger = adapter.getLogger(KernelLoggerFactoryAdapterTest.class);

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger targetLogger = attachAppender(appender);
    Level previousLevel = targetLogger.getLevel();
    targetLogger.setLevel(Level.WARN);

    try {
      logger.warn(null, new RuntimeException("boom"));

      ILoggingEvent event = lastEvent(appender);
      assertTrue(event.getFormattedMessage().endsWith("] null"));
      assertFalse(event.getFormattedMessage().contains("boom"));
      assertTrue(event.getThrowableProxy() != null);
    } finally {
      detachAppender(targetLogger, appender, previousLevel);
    }
  }

  @Test
  void loggerCoversFormattingEdgeCases() {
    KernelLoggerFactoryAdapter adapter = new KernelLoggerFactoryAdapter();
    LoggerPort logger = adapter.getLogger(KernelLoggerFactoryAdapterTest.class);

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger targetLogger = attachAppender(appender);
    Level previousLevel = targetLogger.getLevel();
    targetLogger.setLevel(Level.DEBUG);

    try {
      logger.debug("debug {}", "value");
      logger.info("plain template", (Object[]) null);
      logger.warn("remaining placeholder {} {}", "only-one");
      logger.error("no placeholder", 123);
      logger.error((String) null, (Object[]) null);

      var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertTrue(messages.stream().anyMatch(m -> m.contains("debug value")));
      assertTrue(messages.stream().anyMatch(m -> m.contains("plain template")));
      assertTrue(messages.stream().anyMatch(m -> m.contains("remaining placeholder only-one {}")));
      assertTrue(messages.stream().anyMatch(m -> m.contains("no placeholder")));
      assertTrue(messages.stream().anyMatch(m -> m.endsWith("] null")));
    } finally {
      detachAppender(targetLogger, appender, previousLevel);
    }
  }

  @Test
  void loggerErrorWithThrowableAttachesThrowableProxy() {
    KernelLoggerFactoryAdapter adapter = new KernelLoggerFactoryAdapter();
    LoggerPort logger = adapter.getLogger(KernelLoggerFactoryAdapterTest.class);

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger targetLogger = attachAppender(appender);
    Level previousLevel = targetLogger.getLevel();
    targetLogger.setLevel(Level.ERROR);

    try {
      logger.error("failed", new IllegalStateException("boom"));

      ILoggingEvent event = lastEvent(appender);
      assertTrue(event.getFormattedMessage().contains("failed"));
      assertTrue(event.getThrowableProxy() != null);
    } finally {
      detachAppender(targetLogger, appender, previousLevel);
    }
  }

  private Logger attachAppender(ListAppender<ILoggingEvent> appender) {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger targetLogger = context.getLogger("utgenerator");
    appender.setContext(context);
    appender.start();
    targetLogger.addAppender(appender);
    return targetLogger;
  }

  private void detachAppender(
      Logger targetLogger, ListAppender<ILoggingEvent> appender, Level previousLevel) {
    targetLogger.detachAppender(appender);
    targetLogger.setLevel(previousLevel);
    appender.stop();
  }

  private ILoggingEvent lastEvent(ListAppender<ILoggingEvent> appender) {
    assertFalse(appender.list.isEmpty());
    return appender.list.get(appender.list.size() - 1);
  }
}
