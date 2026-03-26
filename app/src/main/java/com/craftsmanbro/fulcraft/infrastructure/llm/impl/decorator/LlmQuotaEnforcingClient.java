package com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.LocalFileUsageStore;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageRecord;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageScope;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageSnapshot;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

public class LlmQuotaEnforcingClient implements LlmClientPort, TokenUsageAware {

  private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

  private final LlmClientPort delegate;

  private final LocalFileUsageStore usageStore;

  private final Config.QuotaConfig quotaConfig;

  private final Clock clock;

  public LlmQuotaEnforcingClient(
      final LlmClientPort delegate,
      final LocalFileUsageStore usageStore,
      final Config.QuotaConfig quotaConfig) {
    this(delegate, usageStore, quotaConfig, Clock.systemDefaultZone());
  }

  public LlmQuotaEnforcingClient(
      final LlmClientPort delegate,
      final LocalFileUsageStore usageStore,
      final Config.QuotaConfig quotaConfig,
      final Clock clock) {
    this.delegate =
        Objects.requireNonNull(
            delegate,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "delegate must not be null"));
    this.usageStore =
        Objects.requireNonNull(
            usageStore,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "usageStore must not be null"));
    this.quotaConfig = quotaConfig;
    this.clock =
        Objects.requireNonNull(
            clock,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "clock must not be null"));
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
    final QuotaDecision decision = evaluateQuota();
    if (!decision.exceeded()) {
      return delegate.generateTest(prompt, llmConfig);
    }
    if (decision.block()) {
      Logger.error(decision.message());
      throw new IllegalStateException(decision.message());
    }
    Logger.warn(decision.message());

    return delegate.generateTest(prompt, llmConfig);
  }

  @Override
  public boolean isHealthy() {
    return delegate.isHealthy();
  }

  @Override
  public ProviderProfile profile() {
    return delegate.profile();
  }

  @Override
  public void clearContext() {
    delegate.clearContext();
  }

  @Override
  public Optional<TokenUsage> getLastUsage() {
    if (!(delegate instanceof TokenUsageAware aware)) {
      return Optional.empty();
    }
    return aware.getLastUsage();
  }

  private QuotaDecision evaluateQuota() {
    if (quotaConfig == null) {
      return QuotaDecision.ok();
    }
    final Integer maxLlmCalls = quotaConfig.getMaxLlmCalls();
    if (maxLlmCalls == null) {
      return QuotaDecision.ok();
    }
    final long currentCount = resolveCurrentMonthCount();
    final long plannedCount = currentCount + 1;
    final long limit = maxLlmCalls;
    if (plannedCount <= limit) {
      return QuotaDecision.ok();
    }
    final long exceededBy = Math.max(0L, plannedCount - limit);
    final String onExceed = quotaConfig.resolveOnExceed();
    final String message =
        String.format(
            "LLMクォータ超過: quota.max_llm_calls=%,d, 現在値=%,d (project/月), 予定値=%,d, 超過量=%,d。"
                + " quota.on_exceed=%s。対処方法: quota.max_llm_calls を増やす、"
                + "または .ful/usage.json をリセットする、"
                + "または quota.on_exceed を warn|block に設定してください。",
            limit, currentCount, plannedCount, exceededBy, onExceed);
    return new QuotaDecision(true, "block".equals(onExceed), message);
  }

  private long resolveCurrentMonthCount() {
    final UsageSnapshot snapshot = usageStore.getSnapshot();
    if (snapshot == null) {
      return 0L;
    }
    final String scopeKey = UsageScope.PROJECT.key();
    final UsageSnapshot.ScopeUsage scopeUsage = snapshot.getScopes().get(scopeKey);
    if (scopeUsage == null) {
      return 0L;
    }
    final String monthKey = MONTH_FORMAT.format(LocalDate.now(clock));
    final UsageRecord record = scopeUsage.getMonth().get(monthKey);
    if (record == null) {
      return 0L;
    }
    return Math.max(0L, record.getRequestCount());
  }

  private record QuotaDecision(boolean exceeded, boolean block, String message) {

    private static QuotaDecision ok() {
      return new QuotaDecision(false, false, "");
    }
  }
}
