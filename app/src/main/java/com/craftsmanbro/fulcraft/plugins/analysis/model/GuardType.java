package com.craftsmanbro.fulcraft.plugins.analysis.model;

public enum GuardType {
  FAIL_GUARD,
  MESSAGE_GUARD,
  LOOP_GUARD_CONTINUE,
  LOOP_GUARD_BREAK,
  LEGACY;

  public boolean isRepresentativePathGuard() {
    return this == FAIL_GUARD
        || this == MESSAGE_GUARD
        || this == LOOP_GUARD_CONTINUE
        || this == LOOP_GUARD_BREAK;
  }
}
