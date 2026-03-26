package com.craftsmanbro.fulcraft.infrastructure.usage.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

public class UsageSnapshot {

  @JsonProperty("scopes")
  private Map<String, ScopeUsage> scopes = new LinkedHashMap<>();

  public Map<String, ScopeUsage> getScopes() {
    scopes = ensureMap(scopes);
    return scopes;
  }

  public void setScopes(final Map<String, ScopeUsage> scopes) {
    this.scopes = scopes;
  }

  public ScopeUsage getOrCreateScope(final String scopeKey) {
    return getScopes().computeIfAbsent(scopeKey, ignored -> new ScopeUsage());
  }

  private static <K, V> Map<K, V> ensureMap(final Map<K, V> map) {
    if (map == null) {
      return new LinkedHashMap<>();
    }
    return map;
  }

  public static class ScopeUsage {

    @JsonProperty("day")
    private Map<String, UsageRecord> day = new LinkedHashMap<>();

    @JsonProperty("month")
    private Map<String, UsageRecord> month = new LinkedHashMap<>();

    public Map<String, UsageRecord> getDay() {
      day = ensureMap(day);
      return day;
    }

    public void setDay(final Map<String, UsageRecord> day) {
      this.day = day;
    }

    public Map<String, UsageRecord> getMonth() {
      month = ensureMap(month);
      return month;
    }

    public void setMonth(final Map<String, UsageRecord> month) {
      this.month = month;
    }

    public UsageRecord getOrCreateDay(final String key) {
      return getOrCreateUsageRecord(getDay(), key);
    }

    public UsageRecord getOrCreateMonth(final String key) {
      return getOrCreateUsageRecord(getMonth(), key);
    }

    private UsageRecord getOrCreateUsageRecord(
        final Map<String, UsageRecord> usage, final String key) {
      return usage.computeIfAbsent(key, ignored -> new UsageRecord());
    }
  }
}
