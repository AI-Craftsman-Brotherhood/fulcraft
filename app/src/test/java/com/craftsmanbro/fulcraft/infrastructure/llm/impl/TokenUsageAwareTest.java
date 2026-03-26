package com.craftsmanbro.fulcraft.infrastructure.llm.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TokenUsageAwareTest {

  @Test
  void lambdaImplementation_returnsUsage() {
    TokenUsage usage = new TokenUsage(10, 5, 15);
    TokenUsageAware aware = () -> Optional.of(usage);

    assertEquals(usage, aware.getLastUsage().orElseThrow());
  }

  @Test
  void emptyOptional_representsUnknownUsage() {
    TokenUsageAware aware = () -> Optional.empty();

    assertTrue(aware.getLastUsage().isEmpty());
  }
}
