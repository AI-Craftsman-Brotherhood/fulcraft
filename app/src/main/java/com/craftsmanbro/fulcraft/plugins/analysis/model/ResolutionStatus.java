package com.craftsmanbro.fulcraft.plugins.analysis.model;

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
