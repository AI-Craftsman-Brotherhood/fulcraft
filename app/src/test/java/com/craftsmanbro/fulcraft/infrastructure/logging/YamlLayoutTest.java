package com.craftsmanbro.fulcraft.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.YamlLayout;
import java.lang.reflect.Field;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;

/** Tests for YamlLayout structured logging output. */
class YamlLayoutTest {

  private YamlLayout layout;

  @BeforeEach
  void setUp() {
    layout = new YamlLayout();
    MDC.clear();
  }

  @Test
  void testBasicOutput() {
    // YamlLayout outputs YAML documents with --- delimiter
    String output = layout.doLayout(createEvent("Test message", "INFO"));

    assertNotNull(output);
    assertTrue(output.startsWith("---"), "YAML output should start with document delimiter");
    assertTrue(output.contains("message"), "Should contain message field");
    assertTrue(output.contains("Test message"), "Should contain message content");
  }

  @Test
  void testContainsTimestamp() {
    String output = layout.doLayout(createEvent("Test", "INFO"));

    assertTrue(output.contains("timestamp"), "Should contain timestamp field");
  }

  @Test
  void testContainsLevel() {
    String output = layout.doLayout(createEvent("Test", "WARN"));

    assertTrue(output.contains("level"), "Should contain level field");
    assertTrue(output.contains("WARN"), "Should contain level value");
  }

  @Test
  void testEndsWithNewline() {
    String output = layout.doLayout(createEvent("Test", "INFO"));

    assertTrue(output.endsWith("\n"), "YAML output should end with a newline");
  }

  @Test
  void testMdcTraceId() {
    MDC.put(Logger.MDC_TRACE_ID, "abc12345");

    String output = layout.doLayout(createEvent("Test", "INFO"));

    assertTrue(output.contains("traceId"), "Should contain traceId from MDC");
    assertTrue(output.contains("abc12345"), "Should contain trace ID value");
  }

  @Test
  void testMdcSubsystem() {
    MDC.put(Logger.MDC_SUBSYSTEM, "analysis");

    String output = layout.doLayout(createEvent("Test", "INFO"));

    assertTrue(output.contains("subsystem"), "Should contain subsystem from MDC");
    assertTrue(output.contains("analysis"), "Should contain subsystem value");
  }

  @Test
  void testMdcStage() {
    MDC.put(Logger.MDC_STAGE, "AnalyzeStage");

    String output = layout.doLayout(createEvent("Test", "INFO"));

    assertTrue(output.contains("stage"), "Should contain stage from MDC");
    assertTrue(output.contains("AnalyzeStage"), "Should contain stage value");
  }

  @Test
  void testMdcTargetClass() {
    MDC.put(Logger.MDC_TARGET_CLASS, "com.example.MyClass");

    String output = layout.doLayout(createEvent("Test", "INFO"));

    assertTrue(output.contains("targetClass"), "Should contain targetClass from MDC");
    assertTrue(output.contains("com.example.MyClass"), "Should contain target class value");
  }

  @Test
  void testMdcTaskId() {
    MDC.put(Logger.MDC_TASK_ID, "task-001");

    String output = layout.doLayout(createEvent("Test", "INFO"));

    assertTrue(output.contains("taskId"), "Should contain taskId from MDC");
    assertTrue(output.contains("task-001"), "Should contain task ID value");
  }

  @Test
  void testNoMdcWhenEmpty() {
    // Clear MDC
    MDC.clear();

    String output = layout.doLayout(createEvent("Test", "INFO"));

    // Should not contain MDC-specific fields when not set
    assertFalse(output.contains("traceId:"), "Should not contain traceId when not set");
  }

  @Test
  void testSkipsEmptyMdcValues() {
    MDC.put(Logger.MDC_TRACE_ID, "");

    String output = layout.doLayout(createEvent("Test", "INFO"));

    assertFalse(output.contains("traceId:"), "Should skip empty MDC values");
  }

  @Test
  void testIncludesStackTraceWhenThrowableExists() {
    var event = (LoggingEvent) createEvent("Test", "ERROR");
    event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("boom")));

    String output = layout.doLayout(event);

    assertTrue(output.contains("stack_trace"), "Should contain stack trace field");
    assertTrue(output.contains("IllegalStateException"), "Should include throwable type");
  }

  @Test
  void testFallbackWhenSerializationFails() {
    YamlLayout fallbackLayout = new YamlLayout();
    if (!forceMapperToFail(fallbackLayout)) {
      return;
    }

    String output = fallbackLayout.doLayout(createEvent("Test", "INFO"));

    assertEquals("---\nerror: \"Failed to serialize log event\"\n", output);
  }

  @Test
  void testAddsTrailingNewlineWhenMapperOmitsIt() {
    YamlLayout newlineLayout = new YamlLayout();
    if (!forceMapperToSkipTrailingNewline(newlineLayout)) {
      return;
    }

    String output = newlineLayout.doLayout(createEvent("Test", "INFO"));

    assertTrue(output.endsWith("\n"), "Layout should add a trailing newline when missing");
  }

  private ILoggingEvent createEvent(String message, String level) {
    var loggerContext = new ch.qos.logback.classic.LoggerContext();
    var event = new LoggingEvent();
    event.setLoggerContext(loggerContext);
    event.setLoggerName("test");
    event.setMessage(message);
    event.setLevel(ch.qos.logback.classic.Level.toLevel(level));
    event.setTimeStamp(System.currentTimeMillis());
    event.setThreadName(Thread.currentThread().getName());
    var mdcMap = MDC.getCopyOfContextMap();
    event.setMDCPropertyMap(mdcMap != null ? mdcMap : Collections.emptyMap());
    return event;
  }

  private static boolean forceMapperToFail(YamlLayout layout) {
    ObjectMapper failing =
        new ObjectMapper() {
          @Override
          public String writeValueAsString(Object value)
              throws tools.jackson.core.JacksonException {
            throw org.mockito.Mockito.mock(tools.jackson.core.JacksonException.class);
          }
        };
    return replaceMapper(layout, failing);
  }

  private static boolean forceMapperToSkipTrailingNewline(YamlLayout layout) {
    ObjectMapper mapper =
        new ObjectMapper() {
          @Override
          public String writeValueAsString(Object value) {
            return "message: test";
          }
        };
    return replaceMapper(layout, mapper);
  }

  private static boolean replaceMapper(YamlLayout layout, ObjectMapper mapperValue) {
    try {
      Field mapper = YamlLayout.class.getDeclaredField("mapper");
      mapper.setAccessible(true);
      try {
        mapper.set(layout, mapperValue);
      } catch (IllegalAccessException ignored) {
        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(mapper, mapper.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
        mapper.set(layout, mapperValue);
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
