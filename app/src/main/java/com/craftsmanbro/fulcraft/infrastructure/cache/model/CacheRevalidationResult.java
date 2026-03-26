package com.craftsmanbro.fulcraft.infrastructure.cache.model;

/** Revalidation result for cached test code. */
public final class CacheRevalidationResult {

  private static final double INVALID_DURATION_MS = 0D;

  private final boolean valid;

  private final String reason;

  private final double durationMs;

  private CacheRevalidationResult(
      final boolean valid, final String reason, final double durationMs) {
    this.valid = valid;
    this.reason = reason;
    this.durationMs = durationMs;
  }

  public static CacheRevalidationResult valid(final double durationMs) {
    return new CacheRevalidationResult(true, null, durationMs);
  }

  public static CacheRevalidationResult invalid(final String reason) {
    return new CacheRevalidationResult(false, reason, INVALID_DURATION_MS);
  }

  public boolean isValid() {
    return valid;
  }

  public String getReason() {
    return reason;
  }

  public double getDurationMs() {
    return durationMs;
  }

  @Override
  public String toString() {
    if (!valid) {
      return formatInvalidResult();
    }
    return formatValidResult();
  }

  private String formatValidResult() {
    return "RevalidationResult{valid=true, durationMs=" + formatDurationMs() + "}";
  }

  private String formatInvalidResult() {
    return "RevalidationResult{valid=false, reason='" + reason + "'}";
  }

  private String formatDurationMs() {
    return String.format("%.2f", durationMs);
  }
}
