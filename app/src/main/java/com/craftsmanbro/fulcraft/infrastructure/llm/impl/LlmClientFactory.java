package com.craftsmanbro.fulcraft.infrastructure.llm.impl;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.Config.LlmConfig;
import com.craftsmanbro.fulcraft.infrastructure.audit.contract.AuditLogPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmAuditLoggingClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmQuotaEnforcingClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmUsageTrackingClient;
import com.craftsmanbro.fulcraft.infrastructure.usage.contract.UsageTrackerPort;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.LocalFileUsageStore;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.TokenUsageEstimator;
import java.util.List;
import java.util.Objects;

/**
 * Factory class for creating LlmClientPort instances based on configuration.
 *
 * <p>This factory supports a plugin-based architecture for LLM providers and automatically applies
 * decorator chains for:
 *
 * <ul>
 *   <li>Token usage tracking
 *   <li>Quota enforcement
 *   <li>Audit logging
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // Simple usage - creates base client only
 * LlmClientPort client = LlmClientFactory.create(llmConfig);
 *
 * // Full configuration - creates decorated client
 * LlmClientPort client = LlmClientFactory.builder()
 *     .llmConfig(llmConfig)
 *     .quotaConfig(config.getQuota())
 *     .auditLogger(auditLogger)
 *     .usageTracker(usageTracker)
 *     .build();
 * }</pre>
 *
 * @see LlmProviderRegistry
 * @see LlmClientProvider
 */
public final class LlmClientFactory {

  private LlmClientFactory() {
    // Private constructor to hide implicit public one
  }

  /**
   * Creates an LlmClientPort instance based on the provided configuration.
   *
   * <p>This method creates a base client without decorators. For full functionality including usage
   * tracking, quota enforcement, and audit logging, use {@link #builder()}.
   *
   * @param config The LLM configuration.
   * @return An instance of LlmClientPort.
   * @throws IllegalStateException if the provider is unsupported or invalid.
   * @throws NullPointerException if the configuration is null.
   */
  public static LlmClientPort create(final LlmConfig config) {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "LlmConfig must not be null"));
    final String providerName = config.getProvider();
    if (providerName == null) {
      throw new IllegalStateException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Unsupported llm.provider: null"));
    }
    return LlmProviderRegistry.getProvider(providerName).create(config);
  }

  /**
   * Creates a builder for configuring and creating decorated LlmClientPort instances.
   *
   * @return A new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for creating decorated LlmClientPort instances with full functionality.
   *
   * <p>The builder allows configuration of:
   *
   * <ul>
   *   <li>Base LLM configuration (required)
   *   <li>Usage tracking with custom tracker and estimator
   *   <li>Quota enforcement with custom store and config
   *   <li>Audit logging with custom logger
   * </ul>
   */
  public static final class Builder {

    private LlmConfig llmConfig;

    private Config.QuotaConfig quotaConfig;

    private UsageTrackerPort usageTracker;

    private TokenUsageEstimator tokenEstimator;

    private LocalFileUsageStore usageStore;

    private AuditLogPort auditLogger;

    private boolean enableUsageTracking = true;

    private boolean enableQuotaEnforcement = true;

    private boolean enableAuditLogging = true;

    private Builder() {}

    /**
     * Sets the LLM configuration (required).
     *
     * @param config The LLM configuration
     * @return This builder
     */
    public Builder llmConfig(final LlmConfig config) {
      this.llmConfig = config;
      return this;
    }

    /**
     * Sets the quota configuration for enforcement.
     *
     * @param config The quota configuration (null to disable)
     * @return This builder
     */
    public Builder quotaConfig(final Config.QuotaConfig config) {
      this.quotaConfig = config;
      return this;
    }

    /**
     * Sets the usage tracker for token tracking.
     *
     * @param tracker The usage tracker
     * @return This builder
     */
    public Builder usageTracker(final UsageTrackerPort tracker) {
      this.usageTracker = tracker;
      return this;
    }

    /**
     * Sets the token usage estimator.
     *
     * @param estimator The token estimator
     * @return This builder
     */
    public Builder tokenEstimator(final TokenUsageEstimator estimator) {
      this.tokenEstimator = estimator;
      return this;
    }

    /**
     * Sets the usage store for quota enforcement.
     *
     * @param store The usage store
     * @return This builder
     */
    public Builder usageStore(final LocalFileUsageStore store) {
      this.usageStore = store;
      return this;
    }

    /**
     * Sets the audit logger for request/response logging.
     *
     * @param logger The audit logger
     * @return This builder
     */
    public Builder auditLogger(final AuditLogPort logger) {
      this.auditLogger = logger;
      return this;
    }

    /**
     * Enables or disables usage tracking.
     *
     * @param enable true to enable (default), false to disable
     * @return This builder
     */
    public Builder enableUsageTracking(final boolean enable) {
      this.enableUsageTracking = enable;
      return this;
    }

    /**
     * Enables or disables quota enforcement.
     *
     * @param enable true to enable (default), false to disable
     * @return This builder
     */
    public Builder enableQuotaEnforcement(final boolean enable) {
      this.enableQuotaEnforcement = enable;
      return this;
    }

    /**
     * Enables or disables audit logging.
     *
     * @param enable true to enable (default), false to disable
     * @return This builder
     */
    public Builder enableAuditLogging(final boolean enable) {
      this.enableAuditLogging = enable;
      return this;
    }

    /**
     * Builds the LlmClientPort with all configured decorators.
     *
     * <p>Decorators are applied in the following order (innermost to outermost):
     *
     * <ol>
     *   <li>Base provider client
     *   <li>Usage tracking (if enabled and tracker provided)
     *   <li>Quota enforcement (if enabled and store provided)
     *   <li>Audit logging (if enabled and logger provided)
     * </ol>
     *
     * @return The configured LlmClientPort
     * @throws NullPointerException if llmConfig is null
     * @throws IllegalStateException if provider is unsupported
     */
    public LlmClientPort build() {
      Objects.requireNonNull(
          llmConfig,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.argument_null", "LlmConfig must not be null"));
      // Create base client
      LlmClientPort client = create(llmConfig);
      // Apply decorators in order (innermost first)
      client = applyUsageTracking(client);
      client = applyQuotaEnforcement(client);
      client = applyAuditLogging(client);
      return client;
    }

    private LlmClientPort applyUsageTracking(final LlmClientPort client) {
      if (!enableUsageTracking || (usageTracker == null && usageStore == null)) {
        return client;
      }
      final UsageTrackerPort tracker = usageTracker != null ? usageTracker : usageStore;
      final TokenUsageEstimator estimator =
          tokenEstimator != null ? tokenEstimator : new TokenUsageEstimator();
      return new LlmUsageTrackingClient(client, tracker, estimator);
    }

    private LlmClientPort applyQuotaEnforcement(final LlmClientPort client) {
      if (!enableQuotaEnforcement || usageStore == null) {
        return client;
      }
      return new LlmQuotaEnforcingClient(client, usageStore, quotaConfig);
    }

    private LlmClientPort applyAuditLogging(final LlmClientPort client) {
      if (!enableAuditLogging || auditLogger == null) {
        return client;
      }
      return new LlmAuditLoggingClient(client, auditLogger);
    }
  }

  /**
   * Checks if a provider is registered and available.
   *
   * @param providerName The provider name
   * @return true if the provider is available
   */
  public static boolean isProviderAvailable(final String providerName) {
    return LlmProviderRegistry.findProvider(providerName).isPresent();
  }

  /**
   * Returns a list of all available provider names.
   *
   * @return List of provider names
   */
  public static List<String> getAvailableProviders() {
    return LlmProviderRegistry.getProviderNames();
  }
}
