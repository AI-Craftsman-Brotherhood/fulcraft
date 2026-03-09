package com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.LlmClientFactory;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** LLM client that routes requests based on per-call configuration overrides. */
public class OverrideAwareLlmClient implements LlmClientPort, TokenUsageAware {
  private static final String CACHE_KEY_DELIMITER = "|";
  private static final String HEADER_DELIMITER = ",";
  private static final String HEADER_KEY_VALUE_DELIMITER = "=";

  private final Config.LlmConfig baseConfig;

  private final LlmClientPort baseClient;

  private final Map<String, LlmClientPort> clientCache = new ConcurrentHashMap<>();

  private final ThreadLocal<TokenUsage> lastUsage = new ThreadLocal<>();

  private final ThreadLocal<LlmClientPort> lastDelegate = new ThreadLocal<>();

  public OverrideAwareLlmClient(final Config.LlmConfig baseConfig) {
    this.baseConfig =
        Objects.requireNonNull(
            baseConfig,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "baseConfig must not be null"));
    this.baseClient = LlmClientFactory.create(this.baseConfig);
    clientCache.put(buildCacheKey(this.baseConfig), this.baseClient);
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
    return generateWithDelegate(prompt, effectiveConfig(llmConfig));
  }

  @Override
  public boolean isHealthy() {
    return baseClient.isHealthy();
  }

  @Override
  public ProviderProfile profile() {
    final LlmClientPort delegate = currentDelegate();
    if (delegate == null) {
      return baseClient.profile();
    }
    return delegate.profile();
  }

  @Override
  public void clearContext() {
    final LlmClientPort delegate = currentDelegate();
    if (delegate != null) {
      delegate.clearContext();
    }
    lastDelegate.remove();
    lastUsage.remove();
  }

  @Override
  public Optional<TokenUsage> getLastUsage() {
    return Optional.ofNullable(lastUsage.get());
  }

  private Config.LlmConfig effectiveConfig(final Config.LlmConfig llmConfig) {
    return llmConfig != null ? llmConfig : baseConfig;
  }

  private String generateWithDelegate(final String prompt, final Config.LlmConfig effective) {
    final LlmClientPort delegate = resolveClient(effective);
    lastDelegate.set(delegate);
    final String result = delegate.generateTest(prompt, effective);
    captureUsage(delegate);
    return result;
  }

  private LlmClientPort currentDelegate() {
    return lastDelegate.get();
  }

  private LlmClientPort resolveClient(final Config.LlmConfig cfg) {
    final String key = buildCacheKey(cfg);
    return clientCache.computeIfAbsent(key, k -> LlmClientFactory.create(cfg));
  }

  private void captureUsage(final LlmClientPort delegate) {
    if (delegate instanceof TokenUsageAware aware) {
      lastUsage.set(aware.getLastUsage().orElse(null));
    } else {
      lastUsage.remove();
    }
  }

  private String buildCacheKey(final Config.LlmConfig cfg) {
    final StringJoiner joiner = new StringJoiner(CACHE_KEY_DELIMITER);
    joiner.add(nullToEmpty(cfg.getProvider()));
    joiner.add(nullToEmpty(cfg.getModelName()));
    joiner.add(nullToEmpty(cfg.getUrl()));
    joiner.add(nullToEmpty(cfg.getApiKey()));
    joiner.add(String.valueOf(cfg.getRequestTimeout()));
    joiner.add(String.valueOf(cfg.getConnectTimeout()));
    joiner.add(String.valueOf(cfg.getMaxResponseLength()));
    joiner.add(String.valueOf(cfg.getMaxRetries()));
    joiner.add(String.valueOf(cfg.getFixRetries()));
    joiner.add(String.valueOf(cfg.getDeterministic()));
    joiner.add(String.valueOf(cfg.getSeed()));
    joiner.add(normalizeHeaders(cfg.getCustomHeaders()));
    return joiner.toString();
  }

  private String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }

  private String normalizeHeaders(final Map<String, String> headers) {
    if (headers == null || headers.isEmpty()) {
      return "";
    }
    // Sort headers so equivalent maps produce identical cache keys.
    final Map<String, String> sorted = new TreeMap<>(headers);
    final StringJoiner joiner = new StringJoiner(HEADER_DELIMITER);
    for (final Map.Entry<String, String> entry : sorted.entrySet()) {
      joiner.add(
          nullToEmpty(entry.getKey()) + HEADER_KEY_VALUE_DELIMITER + nullToEmpty(entry.getValue()));
    }
    return joiner.toString();
  }
}
