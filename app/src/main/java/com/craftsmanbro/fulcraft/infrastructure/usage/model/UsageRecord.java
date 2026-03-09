package com.craftsmanbro.fulcraft.infrastructure.usage.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UsageRecord {

  @JsonProperty("request_count")
  private long requestCount;

  @JsonProperty("token_count")
  private long tokenCount;

  public UsageRecord() {}

  public UsageRecord(final long requestCount, final long tokenCount) {
    this.requestCount = requestCount;
    this.tokenCount = tokenCount;
  }

  public long getRequestCount() {
    return requestCount;
  }

  public void setRequestCount(final long requestCount) {
    this.requestCount = requestCount;
  }

  public long getTokenCount() {
    return tokenCount;
  }

  public void setTokenCount(final long tokenCount) {
    this.tokenCount = tokenCount;
  }

  public void add(final long requestDelta, final long tokenDelta) {
    this.requestCount += clampNonNegative(requestDelta);
    this.tokenCount += clampNonNegative(tokenDelta);
  }

  private static long clampNonNegative(final long value) {
    return Math.max(0L, value);
  }
}
