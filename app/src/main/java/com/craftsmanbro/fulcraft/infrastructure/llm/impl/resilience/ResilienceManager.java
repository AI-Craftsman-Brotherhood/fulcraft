package com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

/** Manages resilience patterns for LLM API calls using Resilience4j. */
public class ResilienceManager {

  private static final int DEFAULT_MAX_RETRIES = 3;

  private static final long DEFAULT_RETRY_INITIAL_DELAY_MS = 2_000L;

  private static final double DEFAULT_RETRY_BACKOFF_MULTIPLIER = 2.0;

  private static final int DEFAULT_CB_THRESHOLD = 5;

  private static final long DEFAULT_CB_RESET_MS = 30_000L;

  private static final float CB_FAILURE_RATE_THRESHOLD = 50.0f;

  private static final int RL_LIMIT_REFRESH_PERIOD_SECONDS = 1;

  private static final int RL_TIMEOUT_DURATION_SECONDS = 10;

  private static final String RETRY_NAME = "llmRetry";

  private static final String CB_NAME = "llmCircuitBreaker";

  private static final String RL_NAME = "llmRateLimiter";

  private final Retry retry;

  private final CircuitBreaker circuitBreaker;

  private final RateLimiter rateLimiter;

  public ResilienceManager(final Config.LlmConfig config) {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "Config must not be null"));
    final var initialDelay =
        coercePositiveLong(config.getRetryInitialDelayMs(), DEFAULT_RETRY_INITIAL_DELAY_MS);
    final var multiplier =
        coerceMinDouble(config.getRetryBackoffMultiplier(), DEFAULT_RETRY_BACKOFF_MULTIPLIER, 1.0);
    final var maxRetries = coerceNonNegativeInt(config.getMaxRetries(), DEFAULT_MAX_RETRIES);
    final var retryConfig =
        RetryConfig.custom()
            .maxAttempts( // +1 because maxAttempts includes the initial call
                maxRetries + 1)
            .intervalFunction(
                IntervalFunction.ofExponentialBackoff(Duration.ofMillis(initialDelay), multiplier))
            .retryOnException(
                exception -> {
                  if (exception instanceof LlmProviderException providerException) {
                    return providerException.isRetryable();
                  }
                  return exception instanceof IOException;
                })
            .ignoreExceptions(CallNotPermittedException.class)
            .build();
    this.retry = Retry.of(RETRY_NAME, retryConfig);
    final var threshold =
        coercePositiveInt(config.getCircuitBreakerThreshold(), DEFAULT_CB_THRESHOLD);
    final var resetMs = coercePositiveLong(config.getCircuitBreakerResetMs(), DEFAULT_CB_RESET_MS);
    final var cbConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold( // 50% failure rate triggers open
                CB_FAILURE_RATE_THRESHOLD)
            .slidingWindowSize(threshold)
            .minimumNumberOfCalls(threshold)
            .waitDurationInOpenState(Duration.ofMillis(resetMs))
            .build();
    this.circuitBreaker = CircuitBreaker.of(CB_NAME, cbConfig);
    if (config.getRateLimitQps() != null && config.getRateLimitQps() > 0) {
      final var limitForPeriod = coercePositiveInt(config.getRateLimitQps().intValue(), 1);
      final var rlConfig =
          RateLimiterConfig.custom()
              .limitForPeriod(limitForPeriod)
              .limitRefreshPeriod(Duration.ofSeconds(RL_LIMIT_REFRESH_PERIOD_SECONDS))
              .timeoutDuration( // Wait up to 10s for permission
                  Duration.ofSeconds(RL_TIMEOUT_DURATION_SECONDS))
              .build();
      this.rateLimiter = RateLimiter.of(RL_NAME, rlConfig);
    } else {
      this.rateLimiter = null;
    }
  }

  /**
   * Executes a task with Retry, CircuitBreaker, and RateLimiter applied.
   *
   * @param task The task to execute
   * @param <T> The result type
   * @return The result
   */
  public <T> T execute(final Callable<T> task) {
    Objects.requireNonNull(
        task,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "Task must not be null"));
    var decorated = CircuitBreaker.decorateCallable(circuitBreaker, task);
    if (rateLimiter != null) {
      decorated = RateLimiter.decorateCallable(rateLimiter, decorated);
    }
    decorated = Retry.decorateCallable(retry, decorated);
    try {
      return decorated.call();
    } catch (Exception exception) {
      throw translateExecutionException(exception);
    }
  }

  private RuntimeException translateExecutionException(final Exception exception) {
    if (exception instanceof InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      return new ResilienceExecutionException(interruptedException);
    }
    if (exception instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    return new ResilienceExecutionException(exception);
  }

  private static int coerceNonNegativeInt(final Integer value, final int defaultValue) {
    if (value == null || value < 0) {
      return defaultValue;
    }
    return value;
  }

  private static int coercePositiveInt(final Integer value, final int defaultValue) {
    if (value == null || value <= 0) {
      return defaultValue;
    }
    return value;
  }

  private static long coercePositiveLong(final Long value, final long defaultValue) {
    if (value == null || value <= 0) {
      return defaultValue;
    }
    return value;
  }

  private static double coerceMinDouble(
      final Double value, final double defaultValue, final double minimum) {
    if (value == null) {
      return defaultValue;
    }
    if (value < minimum) {
      return minimum;
    }
    return value;
  }
}
