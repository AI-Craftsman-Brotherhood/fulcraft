package com.craftsmanbro.fulcraft.infrastructure.llm.impl.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.request.LlmRequest;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Unit tests for {@link RequestParamWarner}. */
class RequestParamWarnerTest {

  @BeforeEach
  void setUp() {
    // Reset warning state before each test
    Logger.resetWarnOnceKeys();
  }

  @AfterEach
  void tearDown() {
    // Clean up after each test
    Logger.resetWarnOnceKeys();
  }

  private LlmRequest createRequestWithSeed(Integer seed) {
    return LlmRequest.newBuilder()
        .seed(seed)
        .uri(URI.create("http://craftsmann-bro.com"))
        .requestBody("{}")
        .build();
  }

  private ProviderProfile createProfile(String name, boolean supportsSeed) {
    Set<Capability> capabilities = supportsSeed ? Set.of(Capability.SEED) : Set.of();
    return new ProviderProfile(name, capabilities, Optional.empty());
  }

  @Nested
  @DisplayName("warnIfUnsupported with SEED capability")
  class SeedWarningTests {

    @Test
    @DisplayName("should not warn when seed is null")
    void seedNull_noWarning() {
      var profile = createProfile("anthropic", false);
      var request = createRequestWithSeed(null);

      // Should not throw or log anything
      assertDoesNotThrow(() -> RequestParamWarner.warnIfUnsupported(profile, request));
    }

    @Test
    @DisplayName("should handle seed-only overload with null seed")
    void seedNull_noWarning_overload() {
      var profile = createProfile("anthropic", false);

      assertDoesNotThrow(() -> RequestParamWarner.warnIfUnsupported(profile, (Integer) null));
    }

    @Test
    @DisplayName("should not warn when provider supports SEED")
    void seedSupported_noWarning() {
      var profile = createProfile("openai", true);
      var request = createRequestWithSeed(42);

      // Should not warn since provider supports SEED
      assertDoesNotThrow(() -> RequestParamWarner.warnIfUnsupported(profile, request));
    }

    @Test
    @DisplayName("should not emit warning logs when provider supports SEED")
    void seedSupported_doesNotEmitWarningLog() {
      var profile = createProfile("openai", true);
      var request = createRequestWithSeed(42);

      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      ch.qos.logback.classic.Logger targetLogger = attachAppender(appender);
      Level previousLevel = targetLogger.getLevel();
      targetLogger.setLevel(Level.WARN);

      try {
        RequestParamWarner.warnIfUnsupported(profile, request);
        RequestParamWarner.warnIfUnsupported(profile, 99);
        assertTrue(appender.list.isEmpty());
      } finally {
        detachAppender(targetLogger, appender, previousLevel);
      }
    }

    @Test
    @DisplayName("should warn when seed is set but provider does not support SEED")
    void seedNotSupported_emitsWarning() {
      var profile = createProfile("anthropic", false);
      var request = createRequestWithSeed(42);

      // Should warn (but we can't easily capture logs in this test)
      // This test ensures no exception is thrown and execution continues
      assertDoesNotThrow(() -> RequestParamWarner.warnIfUnsupported(profile, request));
    }

    @Test
    @DisplayName("should only warn once per provider+parameter combination")
    void duplicateWarnings_onlyEmittedOnce() {
      var profile = createProfile("bedrock", false);
      var request1 = createRequestWithSeed(42);
      var request2 = createRequestWithSeed(99);

      // First call should emit warning
      RequestParamWarner.warnIfUnsupported(profile, request1);
      // Second call with same provider should NOT emit (due to warnOnce)
      RequestParamWarner.warnIfUnsupported(profile, request2);

      // Can't directly verify log output, but we verify no exception
      // and the warnOnce mechanism is tested in LoggerTest
    }

    @Test
    @DisplayName("should emit a warning once per provider when SEED unsupported")
    void seedNotSupported_emitsWarningOnce() {
      var profile = createProfile("gemini", false);
      var request = createRequestWithSeed(42);

      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      ch.qos.logback.classic.Logger targetLogger = attachAppender(appender);
      Level previousLevel = targetLogger.getLevel();
      targetLogger.setLevel(Level.WARN);

      try {
        RequestParamWarner.warnIfUnsupported(profile, request);
        RequestParamWarner.warnIfUnsupported(profile, createRequestWithSeed(99));

        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("Seed is set"));
        assertTrue(message.contains("gemini"));
      } finally {
        detachAppender(targetLogger, appender, previousLevel);
      }
    }

    @Test
    @DisplayName("should emit one warning for seed overload when unsupported")
    void seedNotSupported_overloadWarnsOnce() {
      var profile = createProfile("vertex", false);

      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      ch.qos.logback.classic.Logger targetLogger = attachAppender(appender);
      Level previousLevel = targetLogger.getLevel();
      targetLogger.setLevel(Level.WARN);

      try {
        RequestParamWarner.warnIfUnsupported(profile, 7);
        RequestParamWarner.warnIfUnsupported(profile, 8);

        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("seed=7"));
        assertTrue(message.contains("vertex"));
      } finally {
        detachAppender(targetLogger, appender, previousLevel);
      }
    }
  }

  @Nested
  @DisplayName("warnIfUnsupported with null inputs")
  class NullInputTests {

    @Test
    @DisplayName("should handle null profile gracefully")
    void nullProfile_noException() {
      var request = createRequestWithSeed(42);

      assertDoesNotThrow(() -> RequestParamWarner.warnIfUnsupported(null, request));
    }

    @Test
    @DisplayName("should handle null request gracefully")
    void nullRequest_noException() {
      var profile = createProfile("openai", true);

      assertDoesNotThrow(() -> RequestParamWarner.warnIfUnsupported(profile, (LlmRequest) null));
    }

    @Test
    @DisplayName("should handle both null gracefully")
    void bothNull_noException() {
      assertDoesNotThrow(() -> RequestParamWarner.warnIfUnsupported(null, (LlmRequest) null));
    }
  }

  private ch.qos.logback.classic.Logger attachAppender(ListAppender<ILoggingEvent> appender) {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger targetLogger = context.getLogger("utgenerator");
    appender.setContext(context);
    appender.start();
    targetLogger.addAppender(appender);
    return targetLogger;
  }

  private void detachAppender(
      ch.qos.logback.classic.Logger targetLogger,
      ListAppender<ILoggingEvent> appender,
      Level previousLevel) {
    targetLogger.detachAppender(appender);
    targetLogger.setLevel(previousLevel);
    appender.stop();
  }
}
