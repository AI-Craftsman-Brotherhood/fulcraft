package com.craftsmanbro.fulcraft.infrastructure.llm.impl.config;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves per-task LLM configuration overrides. */
public final class LlmConfigResolver {

  private LlmConfigResolver() {
    // Utility class
  }

  public static Config.LlmConfig resolveForTaskWithGeneration(
      final Config config, final TaskRecord task) {
    if (config == null || config.getLlm() == null) {
      return null;
    }
    final Config.LlmConfig resolved = copy(config.getLlm());
    applyGenerationOverrides(resolved, config.getGeneration());
    applyTaskOverrides(resolved, task);
    return resolved;
  }

  public static Config.LlmConfig resolveForTask(
      final Config.LlmConfig base, final TaskRecord task) {
    if (base == null) {
      return null;
    }
    if (task == null || !hasOverride(task)) {
      return base;
    }
    final Config.LlmConfig resolved = copy(base);
    applyTaskOverrides(resolved, task);
    return resolved;
  }

  private static void applyGenerationOverrides(
      final Config.LlmConfig target, final Config.GenerationConfig generation) {
    if (target == null || generation == null) {
      return;
    }
    if (generation.getDefaultModel() != null && !generation.getDefaultModel().isBlank()) {
      target.setModelName(generation.getDefaultModel());
    }
    if (generation.getTemperature() != null) {
      target.setTemperature(generation.getTemperature());
    }
    if (generation.getMaxTokens() != null) {
      target.setMaxTokens(generation.getMaxTokens());
    }
  }

  private static void applyTaskOverrides(final Config.LlmConfig target, final TaskRecord task) {
    if (target == null || task == null) {
      return;
    }
    if (task.getUtGenProvider() != null && !task.getUtGenProvider().isBlank()) {
      target.setProvider(task.getUtGenProvider());
    }
    if (task.getUtGenModel() != null && !task.getUtGenModel().isBlank()) {
      target.setModelName(task.getUtGenModel());
    }
    if (task.getUtGenTimeoutSeconds() != null && task.getUtGenTimeoutSeconds() > 0) {
      target.setRequestTimeout(task.getUtGenTimeoutSeconds());
    }
  }

  private static boolean hasOverride(final TaskRecord task) {
    return (task.getUtGenProvider() != null && !task.getUtGenProvider().isBlank())
        || (task.getUtGenModel() != null && !task.getUtGenModel().isBlank())
        || (task.getUtGenTimeoutSeconds() != null && task.getUtGenTimeoutSeconds() > 0);
  }

  private static Config.LlmConfig copy(final Config.LlmConfig source) {
    final Config.LlmConfig target = new Config.LlmConfig();
    target.setProvider(source.getProvider());
    target.setMaxRetries(source.getMaxRetries());
    target.setFixRetries(source.getFixRetries());
    target.setUrl(source.getUrl());
    target.setModelName(source.getModelName());
    target.setApiKey(source.getApiKey());
    target.setAzureDeployment(source.getAzureDeployment());
    target.setAzureApiVersion(source.getAzureApiVersion());
    target.setVertexProject(source.getVertexProject());
    target.setVertexLocation(source.getVertexLocation());
    target.setVertexPublisher(source.getVertexPublisher());
    target.setVertexModel(source.getVertexModel());
    target.setAwsAccessKeyId(source.getAwsAccessKeyId());
    target.setAwsSecretAccessKey(source.getAwsSecretAccessKey());
    target.setAwsSessionToken(source.getAwsSessionToken());
    target.setAwsRegion(source.getAwsRegion());
    target.setConnectTimeout(source.getConnectTimeout());
    target.setRequestTimeout(source.getRequestTimeout());
    target.setMaxResponseLength(source.getMaxResponseLength());
    final Map<String, String> headers = source.getCustomHeaders();
    target.setCustomHeaders(headers == null ? new HashMap<>() : new HashMap<>(headers));
    target.setFallbackStubEnabled(source.getFallbackStubEnabled());
    target.setJavacValidation(source.getJavacValidation());
    target.setRetryInitialDelayMs(source.getRetryInitialDelayMs());
    target.setRetryBackoffMultiplier(source.getRetryBackoffMultiplier());
    target.setRateLimitQps(source.getRateLimitQps());
    target.setCircuitBreakerThreshold(source.getCircuitBreakerThreshold());
    target.setCircuitBreakerResetMs(source.getCircuitBreakerResetMs());
    target.setDeterministic(source.getDeterministic());
    target.setSeed(source.getSeed());
    target.setTemperature(source.getTemperature());
    target.setMaxTokens(source.getMaxTokens());
    target.setSystemMessage(source.getSystemMessage());
    target.setSmartRetry(source.getSmartRetry());
    if (source.getAllowedProviders() != null) {
      target.setAllowedProviders(new ArrayList<>(source.getAllowedProviders()));
    }
    final Map<String, List<String>> allowedModels = source.getAllowedModels();
    if (allowedModels != null) {
      final Map<String, List<String>> modelCopy = new HashMap<>();
      for (final var entry : allowedModels.entrySet()) {
        modelCopy.put(
            entry.getKey(), entry.getValue() == null ? null : new ArrayList<>(entry.getValue()));
      }
      target.setAllowedModels(modelCopy);
    }
    return target;
  }
}
