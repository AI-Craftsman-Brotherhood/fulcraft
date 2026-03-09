package com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience;

import com.craftsmanbro.fulcraft.config.Config;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Facade for applying resilience policies.
 *
 * <p>This class provides a central place to execute operations with configured resilience
 * components (retry, circuit breaker, rate limiter).
 */
public class ResiliencePolicies {

  private final ResilienceManager resilienceManager;

  /**
   * Create resilience policies from LLM configuration.
   *
   * @param llmConfig The LLM configuration
   */
  public ResiliencePolicies(final Config.LlmConfig llmConfig) {
    Objects.requireNonNull(
        llmConfig,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "llmConfig must not be null"));
    this.resilienceManager = new ResilienceManager(llmConfig);
  }

  /**
   * Constructor for injecting a ResilienceManager instance.
   *
   * @param resilienceManager The resilience manager
   */
  public ResiliencePolicies(final ResilienceManager resilienceManager) {
    this.resilienceManager =
        Objects.requireNonNull(
            resilienceManager,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "resilienceManager must not be null"));
  }

  /**
   * Execute a task with LLM resilience policies applied.
   *
   * <p>This includes retry with exponential backoff, circuit breaker, and optional rate limiting.
   *
   * @param task The task to execute
   * @param <T> The result type
   * @return The result
   */
  public <T> T executeLlmCall(final Callable<T> task) {
    Objects.requireNonNull(
        task,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "task must not be null"));
    return resilienceManager.execute(task);
  }
}
