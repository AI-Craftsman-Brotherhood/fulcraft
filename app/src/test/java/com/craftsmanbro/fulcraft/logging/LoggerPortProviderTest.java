package com.craftsmanbro.fulcraft.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class LoggerPortProviderTest {

  @BeforeEach
  void setUp() {
    resetFactory();
  }

  @AfterEach
  void tearDown() {
    resetFactory();
  }

  private void resetFactory() {
    try {
      Field field = LoggerPortProvider.class.getDeclaredField("FACTORY");
      field.setAccessible(true);
      AtomicReference<LoggerPortFactory> ref = (AtomicReference<LoggerPortFactory>) field.get(null);
      ref.set(null);
    } catch (Exception e) {
      throw new RuntimeException("Failed to reset LoggerPortProvider factory", e);
    }
  }

  @Test
  void getLogger_returnsNonNullLogger() {
    LoggerPort logger = LoggerPortProvider.getLogger(getClass());
    assertThat(logger).isNotNull();
  }

  @Test
  void setFactory_throwsNPE_whenFactoryIsNull() {
    assertThatThrownBy(() -> LoggerPortProvider.setFactory(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("factory must not be null");
  }

  @Test
  void logger_throwsIllegalStateException_whenFactoryNotSet() {
    LoggerPort logger = LoggerPortProvider.getLogger(getClass());

    assertThatThrownBy(() -> logger.info("test"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("LoggerPortFactory is not configured");
  }

  @Test
  void logger_delegatesToConfiguredFactory() {
    // Arrange
    LoggerPortFactory mockFactory = mock(LoggerPortFactory.class);
    LoggerPort mockLogger = mock(LoggerPort.class);
    when(mockFactory.getLogger(any(Class.class))).thenReturn(mockLogger);

    LoggerPortProvider.setFactory(mockFactory);
    LoggerPort logger = LoggerPortProvider.getLogger(getClass());

    // Act & Assert
    logger.debug("debug message", "arg1");
    verify(mockLogger).debug("debug message", "arg1");

    logger.info("info message", "arg1");
    verify(mockLogger).info("info message", "arg1");

    logger.warn("warn message", "arg1");
    verify(mockLogger).warn("warn message", "arg1");

    Throwable t = new RuntimeException("oops");
    logger.warn("warn error", t);
    verify(mockLogger).warn("warn error", t);

    logger.error("error message", "arg1");
    verify(mockLogger).error("error message", "arg1");

    logger.error("error error", t);
    verify(mockLogger).error("error error", t);
  }
}
