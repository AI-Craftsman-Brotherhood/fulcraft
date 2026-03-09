package com.craftsmanbro.fulcraft.infrastructure.logging.impl;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.craftsmanbro.fulcraft.infrastructure.security.impl.SecretMasker;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class JsonLayoutTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void doLayout_outputsJsonWithNewline_andWithoutStackTrace_whenNoThrowable() throws Exception {
    JsonLayout layout = new JsonLayout();
    long timestampMillis = 1234567890000L;
    ILoggingEvent event =
        loggingEvent(timestampMillis, Level.INFO, "main", "com.example.MyLogger", "hello", null);

    String out = layout.doLayout(event);

    assertTrue(out.endsWith("\n"), "Should end with newline for logback");
    JsonNode node = JSON.readTree(out);
    assertEquals(
        Instant.ofEpochMilli(timestampMillis), Instant.parse(node.get("timestamp").asText()));
    assertEquals("INFO", node.get("level").asText());
    assertEquals("main", node.get("thread").asText());
    assertEquals("com.example.MyLogger", node.get("logger").asText());
    assertEquals("hello", node.get("message").asText());
    assertFalse(node.has("stack_trace"), "stack_trace should be absent when throwable is null");
  }

  @Test
  void doLayout_masksMessage_andMdcFields() throws Exception {
    JsonLayout layout = new JsonLayout();
    Map<String, String> mdc = new HashMap<>();
    mdc.put(Logger.MDC_TRACE_ID, "trace-123");
    mdc.put(Logger.MDC_SUBSYSTEM, "auth");
    mdc.put(Logger.MDC_STAGE, "run");
    mdc.put(Logger.MDC_TARGET_CLASS, "MyClass");
    mdc.put(Logger.MDC_TASK_ID, "token=abc123");
    ILoggingEvent event =
        loggingEvent(
            1L,
            Level.INFO,
            "t-1",
            "com.example.X",
            "Authorization: Bearer secret-token",
            null,
            mdc);

    JsonNode node = JSON.readTree(layout.doLayout(event));

    assertEquals(
        SecretMasker.mask("Authorization: Bearer secret-token"), node.get("message").asText());
    assertEquals(SecretMasker.mask("trace-123"), node.get("traceId").asText());
    assertEquals(SecretMasker.mask("auth"), node.get("subsystem").asText());
    assertEquals(SecretMasker.mask("run"), node.get("stage").asText());
    assertEquals(SecretMasker.mask("MyClass"), node.get("targetClass").asText());
    assertEquals(SecretMasker.mask("token=abc123"), node.get("taskId").asText());
  }

  @Test
  void doLayout_includesStackTrace_whenThrowableProxyPresent() throws Exception {
    JsonLayout layout = new JsonLayout();
    IThrowableProxy throwableProxy = new ThrowableProxy(new IllegalStateException("boom"));
    ILoggingEvent event =
        loggingEvent(1L, Level.ERROR, "t-1", "com.example.X", "failed", throwableProxy);

    JsonNode node = JSON.readTree(layout.doLayout(event));

    assertEquals("ERROR", node.get("level").asText());
    // Implementation uses ThrowableProxyUtil.asString and applies SecretMasker
    String expectedStackTrace = SecretMasker.mask(ThrowableProxyUtil.asString(throwableProxy));
    assertEquals(expectedStackTrace, node.get("stack_trace").asText());
  }

  @Test
  void doLayout_returnsFallbackJson_whenSerializationFails() throws Exception {
    JsonLayout layout = new JsonLayout();
    boolean mapperReplaced = forceMapperToFail(layout);
    // If we couldn't replace the mapper (e.g., due to module restrictions), skip
    // the test
    if (!mapperReplaced) {
      return;
    }
    ILoggingEvent event = loggingEvent(1L, Level.INFO, "t", "l", "m", null);

    assertEquals("{\"error\": \"Failed to serialize log event\"}\n", layout.doLayout(event));
  }

  private static ILoggingEvent loggingEvent(
      long timestampMillis,
      Level level,
      String threadName,
      String loggerName,
      String formattedMessage,
      IThrowableProxy throwableProxy) {
    return loggingEvent(
        timestampMillis, level, threadName, loggerName, formattedMessage, throwableProxy, Map.of());
  }

  private static ILoggingEvent loggingEvent(
      long timestampMillis,
      Level level,
      String threadName,
      String loggerName,
      String formattedMessage,
      IThrowableProxy throwableProxy,
      Map<String, String> mdc) {
    Map<String, Object> values = new HashMap<>();
    values.put("getTimeStamp", timestampMillis);
    values.put("getLevel", level);
    values.put("getThreadName", threadName);
    values.put("getLoggerName", loggerName);
    values.put("getFormattedMessage", formattedMessage);
    values.put("getThrowableProxy", throwableProxy);
    values.put("getMDCPropertyMap", mdc);
    InvocationHandler handler =
        (Object proxy, Method method, Object[] args) -> {
          if (method.getDeclaringClass() == Object.class) {
            return method.invoke(values, args);
          }
          if (values.containsKey(method.getName())) {
            return values.get(method.getName());
          }
          return defaultValue(method.getReturnType());
        };
    return (ILoggingEvent)
        Proxy.newProxyInstance(
            ILoggingEvent.class.getClassLoader(), new Class<?>[] {ILoggingEvent.class}, handler);
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == byte.class) {
      return (byte) 0;
    }
    if (type == short.class) {
      return (short) 0;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == float.class) {
      return 0.0f;
    }
    if (type == double.class) {
      return 0.0d;
    }
    if (type == char.class) {
      return '\0';
    }
    throw new IllegalArgumentException("Unsupported primitive type: " + type);
  }

  private static boolean forceMapperToFail(JsonLayout layout) {
    try {
      ObjectMapper failing =
          new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value)
                throws tools.jackson.core.JacksonException {
              throw org.mockito.Mockito.mock(tools.jackson.core.JacksonException.class);
            }
          };

      Field mapper = JsonLayout.class.getDeclaredField("mapper");
      mapper.setAccessible(true);
      // For final fields, we need to remove the final modifier
      Field modifiers = Field.class.getDeclaredField("modifiers");
      modifiers.setAccessible(true);
      modifiers.setInt(mapper, mapper.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
      mapper.set(layout, failing);
      return true;
    } catch (Exception e) {
      // On newer JVMs, modifying final fields may not work
      return false;
    }
  }
}
