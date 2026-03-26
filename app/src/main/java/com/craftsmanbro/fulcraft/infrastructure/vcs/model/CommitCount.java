package com.craftsmanbro.fulcraft.infrastructure.vcs.model;

/** Value object representing normalized commit count. */
public record CommitCount(int value) {
  private static final int MIN_COMMIT_COUNT = 0;

  public CommitCount {
    value = normalize(value);
  }

  public static CommitCount zero() {
    return new CommitCount(MIN_COMMIT_COUNT);
  }

  private static int normalize(final int value) {
    return Math.max(MIN_COMMIT_COUNT, value);
  }
}
