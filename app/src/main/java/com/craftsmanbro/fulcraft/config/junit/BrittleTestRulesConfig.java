package com.craftsmanbro.fulcraft.config.junit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class BrittleTestRulesConfig {
  @JsonProperty("enabled")
  private Boolean enabled = true;

  @JsonProperty("fail_on_reflection")
  private Boolean failOnReflection = true;

  @JsonProperty("fail_on_sleep")
  private Boolean failOnSleep = true;

  @JsonProperty("warn_on_time")
  private Boolean warnOnTime = true;

  @JsonProperty("warn_on_random")
  private Boolean warnOnRandom = true;

  @JsonProperty("max_mocks_warn")
  private Integer maxMocksWarn = 3;

  @JsonProperty("max_mocks_fail")
  private Integer maxMocksFail = 6;

  @JsonProperty("allowlist_patterns")
  private List<String> allowlistPatterns = new ArrayList<>();

  @JsonProperty("count_static_mocks")
  private Boolean countStaticMocks = false;

  @JsonProperty("count_stubs")
  private Boolean countStubs = false;

  public boolean isEnabled() {
    return enabled == null || enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isFailOnReflection() {
    return failOnReflection == null || failOnReflection;
  }

  public void setFailOnReflection(final Boolean failOnReflection) {
    this.failOnReflection = failOnReflection;
  }

  public boolean isFailOnSleep() {
    return failOnSleep == null || failOnSleep;
  }

  public void setFailOnSleep(final Boolean failOnSleep) {
    this.failOnSleep = failOnSleep;
  }

  public boolean isWarnOnTime() {
    return warnOnTime == null || warnOnTime;
  }

  public void setWarnOnTime(final Boolean warnOnTime) {
    this.warnOnTime = warnOnTime;
  }

  public boolean isWarnOnRandom() {
    return warnOnRandom == null || warnOnRandom;
  }

  public void setWarnOnRandom(final Boolean warnOnRandom) {
    this.warnOnRandom = warnOnRandom;
  }

  public int getMaxMocksWarn() {
    return maxMocksWarn != null ? maxMocksWarn : 3;
  }

  public void setMaxMocksWarn(final Integer maxMocksWarn) {
    this.maxMocksWarn = maxMocksWarn;
  }

  public int getMaxMocksFail() {
    return maxMocksFail != null ? maxMocksFail : 6;
  }

  public void setMaxMocksFail(final Integer maxMocksFail) {
    this.maxMocksFail = maxMocksFail;
  }

  public List<String> getAllowlistPatterns() {
    return allowlistPatterns != null ? allowlistPatterns : List.of();
  }

  public void setAllowlistPatterns(final List<String> allowlistPatterns) {
    this.allowlistPatterns = allowlistPatterns;
  }

  public boolean isCountStaticMocks() {
    return countStaticMocks != null && countStaticMocks;
  }

  public void setCountStaticMocks(final Boolean countStaticMocks) {
    this.countStaticMocks = countStaticMocks;
  }

  public boolean isCountStubs() {
    return countStubs != null && countStubs;
  }

  public void setCountStubs(final Boolean countStubs) {
    this.countStubs = countStubs;
  }
}
