package com.craftsmanbro.fulcraft.infrastructure.usage.model;

public enum UsageScope {
  PROJECT("project"),
  USER("user");

  private final String key;

  UsageScope(final String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
