package com.craftsmanbro.fulcraft.infrastructure.telemetry.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.telemetry.contract.TelemetryPort;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TelemetryTest {

  @Test
  void getTracer_returnsTracerInstance() {
    TelemetryPort telemetry = Telemetry.getInstance();
    assertNotNull(telemetry.getTracer());
  }

  @Test
  void getInstance_initializesSdk() {
    // Just verifying no exception is thrown
    Telemetry.getInstance();
  }

  @Test
  void getInstance_returnsSingleton() {
    TelemetryPort first = Telemetry.getInstance();
    TelemetryPort second = Telemetry.getInstance();
    assertSame(first, second);
    assertSame(first.getTracer(), second.getTracer());
  }

  @Test
  void constructor_reusesGlobalOpenTelemetryWhenAlreadyRegistered() {
    Telemetry.getInstance();

    Telemetry telemetry = assertDoesNotThrow(TelemetryTest::newTelemetryThroughConstructor);
    try {
      assertNotNull(telemetry.getTracer());
    } finally {
      telemetry.close();
    }
  }

  @Test
  void shutdown_andClose_doNotThrow() {
    Telemetry telemetry = newTelemetryThroughConstructor();
    try {
      assertDoesNotThrow(telemetry::shutdown);
    } finally {
      assertDoesNotThrow(telemetry::close);
    }
  }

  @Test
  void isLoggingExporterEnabled_usesSystemPropertyValues() {
    String envValue = System.getenv("FUL_TELEMETRY_EXPORTER");
    Assumptions.assumeTrue(envValue == null || envValue.isBlank());

    String key = "ful.telemetry.exporter";
    String original = System.getProperty(key);
    try {
      System.setProperty(key, " logging ");
      assertTrue(isLoggingExporterEnabled());

      System.setProperty(key, "console");
      assertTrue(isLoggingExporterEnabled());

      System.setProperty(key, "stdout");
      assertTrue(isLoggingExporterEnabled());

      System.setProperty(key, "true");
      assertTrue(isLoggingExporterEnabled());

      System.setProperty(key, " TrUe ");
      assertTrue(isLoggingExporterEnabled());

      System.setProperty(key, "false");
      assertFalse(isLoggingExporterEnabled());

      System.setProperty(key, "disabled");
      assertFalse(isLoggingExporterEnabled());

      System.setProperty(key, "   ");
      assertFalse(isLoggingExporterEnabled());
    } finally {
      if (original == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, original);
      }
    }
  }

  @Test
  void isLoggingExporterEnabled_defaultsToFalseWhenPropertyMissing() {
    String envValue = System.getenv("FUL_TELEMETRY_EXPORTER");
    Assumptions.assumeTrue(envValue == null || envValue.isBlank());

    String key = "ful.telemetry.exporter";
    String original = System.getProperty(key);
    try {
      System.clearProperty(key);
      assertFalse(isLoggingExporterEnabled());
    } finally {
      if (original == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, original);
      }
    }
  }

  private static Telemetry newTelemetryThroughConstructor() {
    try {
      var constructor = Telemetry.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to construct telemetry", e);
    }
  }

  private static boolean isLoggingExporterEnabled() {
    try {
      Method method = Telemetry.class.getDeclaredMethod("isLoggingExporterEnabled");
      method.setAccessible(true);
      return (boolean) method.invoke(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to invoke telemetry check", e);
    }
  }
}
