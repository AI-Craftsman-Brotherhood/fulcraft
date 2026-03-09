package com.craftsmanbro.fulcraft.infrastructure.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageScope;
import org.junit.jupiter.api.Test;

class UsageTrackerTest {

  @Test
  void usageScopeProjectKeyIsStable() {
    assertEquals("project", UsageScope.PROJECT.key());
  }

  @Test
  void usageScopeUserKeyIsStable() {
    assertEquals("user", UsageScope.USER.key());
  }
}
