package com.craftsmanbro.fulcraft.infrastructure.llm.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderException;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience.ResilienceExecutionException;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience.ResilienceManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilienceManagerTest {

  @Test
  void shouldRejectNullConfig() {
    NullPointerException thrown =
        assertThrows(NullPointerException.class, () -> new ResilienceManager(null));

    assertThat(thrown).hasMessageEndingWith("Config must not be null");
  }

  @Test
  void shouldRejectNullTask() {
    ResilienceManager manager = new ResilienceManager(baseConfig(0));

    NullPointerException thrown =
        assertThrows(NullPointerException.class, () -> manager.execute(null));

    assertThat(thrown).hasMessageEndingWith("Task must not be null");
  }

  @Test
  void shouldRetryOnRetryableProviderException() {
    ResilienceManager manager = new ResilienceManager(baseConfig(1));
    AtomicInteger attempts = new AtomicInteger();

    String result =
        manager.execute(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw new LlmProviderException("retry", true);
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void shouldRetryOnIOException() throws Exception {
    ResilienceManager manager = new ResilienceManager(baseConfig(1));
    AtomicInteger attempts = new AtomicInteger();

    String result =
        manager.execute(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw new IOException("io");
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void shouldNotRetryOnNonRetryableProviderException() {
    ResilienceManager manager = new ResilienceManager(baseConfig(2));
    AtomicInteger attempts = new AtomicInteger();

    LlmProviderException thrown =
        assertThrows(
            LlmProviderException.class,
            () ->
                manager.execute(
                    () -> {
                      attempts.incrementAndGet();
                      throw new LlmProviderException("no", false);
                    }));

    assertThat(thrown).hasMessage("no");
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void shouldWrapCheckedException() {
    ResilienceManager manager = new ResilienceManager(baseConfig(0));

    ResilienceExecutionException thrown =
        assertThrows(
            ResilienceExecutionException.class,
            () ->
                manager.execute(
                    () -> {
                      throw new Exception("boom");
                    }));

    assertThat(thrown).hasMessage("Resilience execution failed");
    assertThat(thrown.getCause()).isInstanceOf(Exception.class).hasMessage("boom");
  }

  @Test
  void shouldRethrowRuntimeExceptionAsIs() {
    ResilienceManager manager = new ResilienceManager(baseConfig(0));
    IllegalStateException expected = new IllegalStateException("runtime");

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                manager.execute(
                    () -> {
                      throw expected;
                    }));

    assertThat(thrown).isSameAs(expected);
  }

  @Test
  void shouldMarkThreadInterruptedOnInterruptedException() {
    ResilienceManager manager = new ResilienceManager(baseConfig(0));

    try {
      ResilienceExecutionException thrown =
          assertThrows(
              ResilienceExecutionException.class,
              () ->
                  manager.execute(
                      () -> {
                        throw new InterruptedException("stop");
                      }));

      assertThat(thrown).hasMessage("Resilience execution failed");
      assertThat(thrown.getCause()).isInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void shouldConfigureRateLimiterWhenQpsPositive() throws Exception {
    Config.LlmConfig config = baseConfig(0);
    config.setRateLimitQps(2.0);
    ResilienceManager manager = new ResilienceManager(config);

    RateLimiter rateLimiter = rateLimiterFor(manager);

    assertThat(rateLimiter).isNotNull();
  }

  @Test
  void shouldDisableRateLimiterWhenQpsMissingOrNonPositive() throws Exception {
    Config.LlmConfig nullConfig = baseConfig(0);
    nullConfig.setRateLimitQps(null);
    ResilienceManager nullManager = new ResilienceManager(nullConfig);

    Config.LlmConfig zeroConfig = baseConfig(0);
    zeroConfig.setRateLimitQps(0.0);
    ResilienceManager zeroManager = new ResilienceManager(zeroConfig);

    assertThat(rateLimiterFor(nullManager)).isNull();
    assertThat(rateLimiterFor(zeroManager)).isNull();
  }

  @Test
  void shouldCoerceInvalidRetryAndCircuitBreakerConfigValues() throws Exception {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setMaxRetries(-1);
    config.setRetryInitialDelayMs(0L);
    config.setRetryBackoffMultiplier(0.5);
    config.setCircuitBreakerThreshold(0);
    config.setCircuitBreakerResetMs(0L);

    ResilienceManager manager = new ResilienceManager(config);
    Retry retry = retryFor(manager);
    CircuitBreaker circuitBreaker = circuitBreakerFor(manager);

    assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(4);
    assertThat(retry.getRetryConfig().getIntervalBiFunction().apply(1, null)).isEqualTo(2000L);
    assertThat(retry.getRetryConfig().getIntervalBiFunction().apply(2, null)).isEqualTo(2000L);
    assertThat(circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(5);
    assertThat(circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
  }

  @Test
  void shouldCoerceFractionalRateLimitToMinimumPermit() throws Exception {
    Config.LlmConfig config = baseConfig(0);
    config.setRateLimitQps(0.5);

    ResilienceManager manager = new ResilienceManager(config);
    RateLimiter rateLimiter = rateLimiterFor(manager);

    assertThat(rateLimiter).isNotNull();
    assertThat(rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(1);
  }

  private static Config.LlmConfig baseConfig(int maxRetries) {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setMaxRetries(maxRetries);
    config.setRetryInitialDelayMs(1L);
    config.setRetryBackoffMultiplier(1.0);
    config.setCircuitBreakerThreshold(10);
    config.setCircuitBreakerResetMs(1000L);
    return config;
  }

  private static RateLimiter rateLimiterFor(ResilienceManager manager) throws Exception {
    Field field = ResilienceManager.class.getDeclaredField("rateLimiter");
    field.setAccessible(true);
    return (RateLimiter) field.get(manager);
  }

  private static Retry retryFor(ResilienceManager manager) throws Exception {
    Field field = ResilienceManager.class.getDeclaredField("retry");
    field.setAccessible(true);
    return (Retry) field.get(manager);
  }

  private static CircuitBreaker circuitBreakerFor(ResilienceManager manager) throws Exception {
    Field field = ResilienceManager.class.getDeclaredField("circuitBreaker");
    field.setAccessible(true);
    return (CircuitBreaker) field.get(manager);
  }
}
