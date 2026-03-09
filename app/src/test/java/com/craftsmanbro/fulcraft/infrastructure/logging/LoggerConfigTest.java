package com.craftsmanbro.fulcraft.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LoggerConfigTest {

  @BeforeEach
  void setUp() {
    Logger.clearContext();
    Logger.setColorEnabled(true);
    Logger.setJsonMode(false);
  }

  @AfterEach
  void tearDown() {
    Logger.clearContext();
  }

  @Test
  void initialize_withNullConfig_doesNotThrow() {
    assertDoesNotThrow(() -> Logger.initialize(null));
  }

  @Test
  void initialize_withDefaultConfig_setsDefaults() {
    Config config = new Config();
    Logger.initialize(config);

    assertTrue(Logger.isInitialized());
    assertFalse(Logger.isJsonMode());
  }

  @Test
  void initialize_withJsonFormat_setsJsonMode() {
    Config config = new Config();
    Config.LogConfig logConfig = new Config.LogConfig();
    logConfig.setFormat("json");
    config.setLog(logConfig);

    Logger.initialize(config);

    assertTrue(Logger.isJsonMode());
  }

  @Test
  void initialize_withColorOff_disablesColor() {
    Config config = new Config();
    Config.LogConfig logConfig = new Config.LogConfig();
    logConfig.setColor("off");
    config.setLog(logConfig);

    Logger.initialize(config);

    assertFalse(Logger.isColorEnabled());
  }

  @Test
  void setColorEnabled_togglesColorOutput() {
    Logger.setColorEnabled(true);
    assertTrue(Logger.isColorEnabled());

    Logger.setColorEnabled(false);
    assertFalse(Logger.isColorEnabled());
  }

  @Test
  void colorMethods_whenDisabled_returnPlainText() {
    Logger.setColorEnabled(false);

    assertEquals("test", Logger.green("test"));
    assertEquals("test", Logger.red("test"));
    assertEquals("test", Logger.yellow("test"));
    assertEquals("test", Logger.cyan("test"));
    assertEquals("test", Logger.bold("test"));
  }

  @Test
  void colorMethods_whenEnabled_returnColoredText() {
    Logger.setColorEnabled(true);

    assertTrue(Logger.green("test").contains("\u001B[32m"));
    assertTrue(Logger.red("test").contains("\u001B[31m"));
    assertTrue(Logger.yellow("test").contains("\u001B[33m"));
    assertTrue(Logger.cyan("test").contains("\u001B[36m"));
    assertTrue(Logger.bold("test").contains("\u001B[1m"));
  }

  @Test
  void initializeTraceId_setsTraceIdInMdc() {
    Logger.initializeTraceId();

    String traceId = Logger.getTraceId();
    assertNotNull(traceId);
    assertEquals(8, traceId.length());
  }

  @Test
  void setTraceId_setsCustomTraceId() {
    Logger.setTraceId("custom-id");

    assertEquals("custom-id", Logger.getTraceId());
  }

  @Test
  void setSubsystem_setsSubsystemInMdc() {
    Logger.setSubsystem("generation");

    assertEquals("generation", MDC.get(Logger.MDC_SUBSYSTEM));
  }

  @Test
  void setStage_setsStageInMdc() {
    Logger.setStage("GenerateStage");

    assertEquals("GenerateStage", MDC.get(Logger.MDC_STAGE));
  }

  @Test
  void setTargetClass_setsTargetClassInMdc() {
    Logger.setTargetClass("com.example.MyClass");

    assertEquals("com.example.MyClass", MDC.get(Logger.MDC_TARGET_CLASS));
  }

  @Test
  void setTaskId_setsTaskIdInMdc() {
    Logger.setTaskId("task-123");

    assertEquals("task-123", MDC.get(Logger.MDC_TASK_ID));
  }

  @Test
  void clearContext_clearsAllMdcValues() {
    Logger.initializeTraceId();
    Logger.setSubsystem("test");
    Logger.setStage("TestStage");

    Logger.clearContext();

    assertNull(Logger.getTraceId());
    assertNull(MDC.get(Logger.MDC_SUBSYSTEM));
    assertNull(MDC.get(Logger.MDC_STAGE));
  }

  @Test
  void clearTaskContext_preservesTraceIdAndSubsystem() {
    Logger.initializeTraceId();
    String traceId = Logger.getTraceId();
    Logger.setSubsystem("generation");
    Logger.setStage("GenerateStage");
    Logger.setTaskId("task-1");
    Logger.setTargetClass("com.example.MyClass");

    Logger.clearTaskContext();

    assertEquals(traceId, Logger.getTraceId());
    assertEquals("generation", MDC.get(Logger.MDC_SUBSYSTEM));
    assertNull(MDC.get(Logger.MDC_STAGE));
    assertNull(MDC.get(Logger.MDC_TASK_ID));
    assertNull(MDC.get(Logger.MDC_TARGET_CLASS));
  }

  @Test
  void logConfig_toLogbackLevel_returnsCorrectLevels() {
    Config.LogConfig config = new Config.LogConfig();

    config.setLevel("debug");
    assertEquals(Level.DEBUG, config.toLogbackLevel());

    config.setLevel("info");
    assertEquals(Level.INFO, config.toLogbackLevel());

    config.setLevel("warn");
    assertEquals(Level.WARN, config.toLogbackLevel());

    config.setLevel("error");
    assertEquals(Level.ERROR, config.toLogbackLevel());

    config.setLevel("invalid");
    assertEquals(Level.INFO, config.toLogbackLevel());
  }

  @Test
  void logConfig_isJsonFormat_detectsCorrectFormat() {
    Config.LogConfig config = new Config.LogConfig();

    config.setFormat("json");
    assertTrue(config.isJsonFormat());

    config.setFormat("JSON");
    assertTrue(config.isJsonFormat());

    config.setFormat("human");
    assertFalse(config.isJsonFormat());
  }

  @Test
  void logConfig_isHumanFormat_detectsCorrectFormat() {
    Config.LogConfig config = new Config.LogConfig();

    config.setFormat("human");
    assertTrue(config.isHumanFormat());

    config.setFormat("HUMAN");
    assertTrue(config.isHumanFormat());

    config.setFormat("json");
    assertFalse(config.isHumanFormat());
  }

  @Test
  void logConfig_isDebugEnabled_detectsDebugLevel() {
    Config.LogConfig config = new Config.LogConfig();

    config.setLevel("debug");
    assertTrue(config.isDebugEnabled());

    config.setLevel("info");
    assertFalse(config.isDebugEnabled());
  }
}
