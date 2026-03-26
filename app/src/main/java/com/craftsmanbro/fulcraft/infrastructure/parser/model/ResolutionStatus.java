package com.craftsmanbro.fulcraft.infrastructure.parser.model;

public enum ResolutionStatus {
  RESOLVED,
  UNRESOLVED,
  AMBIGUOUS;

  public static boolean isResolved(final ResolutionStatus status) {
    return status == RESOLVED;
  }

  public static boolean isUnresolved(final ResolutionStatus status) {
    return status == UNRESOLVED || status == AMBIGUOUS;
  }
}
