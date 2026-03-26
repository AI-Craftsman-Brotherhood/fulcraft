package com.craftsmanbro.fulcraft.plugins.analysis.adapter.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TokenUsageAwareFacadeTest {

  @Test
  void lambdaImplementation_returnsUsage() {
    TokenUsage usage = new TokenUsage(10, 5, 15);
    TokenUsageAwareFacade aware = () -> Optional.of(usage);

    assertEquals(usage, aware.getLastUsage().orElseThrow());
  }

  @Test
  void emptyOptional_representsUnknownUsage() {
    TokenUsageAwareFacade aware = () -> Optional.empty();

    assertTrue(aware.getLastUsage().isEmpty());
  }
}
