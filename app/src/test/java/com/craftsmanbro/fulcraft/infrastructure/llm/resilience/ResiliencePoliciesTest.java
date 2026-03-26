package com.craftsmanbro.fulcraft.infrastructure.llm.resilience;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience.ResilienceManager;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience.ResiliencePolicies;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

class ResiliencePoliciesTest {

  @Test
  void constructors_requireNonNull() {
    NullPointerException llmConfigThrown =
        assertThrows(
            NullPointerException.class, () -> new ResiliencePolicies((Config.LlmConfig) null));
    NullPointerException managerThrown =
        assertThrows(
            NullPointerException.class, () -> new ResiliencePolicies((ResilienceManager) null));

    assertTrue(
        llmConfigThrown.getMessage().endsWith("llmConfig must not be null"),
        llmConfigThrown.getMessage());
    assertTrue(
        managerThrown.getMessage().endsWith("resilienceManager must not be null"),
        managerThrown.getMessage());
  }

  @Test
  void llmConfigConstructor_createsExecutablePolicies() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setMaxRetries(0);
    config.setRetryInitialDelayMs(1L);
    config.setRetryBackoffMultiplier(1.0);
    config.setCircuitBreakerThreshold(10);
    config.setCircuitBreakerResetMs(1000L);

    ResiliencePolicies policies = new ResiliencePolicies(config);
    String result = policies.executeLlmCall(() -> "ok");

    assertEquals("ok", result);
  }

  @Test
  void executeLlmCall_delegatesToResilienceManager() {
    StubResilienceManager manager = new StubResilienceManager();
    ResiliencePolicies policies = new ResiliencePolicies(manager);

    String result = policies.executeLlmCall(() -> "ok");

    assertEquals("ok", result);
    assertTrue(manager.called);
  }

  @Test
  void executeLlmCall_requiresNonNullTask() {
    ResiliencePolicies policies = new ResiliencePolicies(new StubResilienceManager());

    NullPointerException thrown =
        assertThrows(NullPointerException.class, () -> policies.executeLlmCall(null));

    assertTrue(thrown.getMessage().endsWith("task must not be null"), thrown.getMessage());
  }

  @Test
  void executeLlmCall_propagatesRuntimeExceptionFromManager() {
    ResiliencePolicies policies = new ResiliencePolicies(new ThrowingStubResilienceManager());

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> policies.executeLlmCall(() -> "unused"));

    assertEquals("manager failure", thrown.getMessage());
  }

  private static final class StubResilienceManager extends ResilienceManager {
    private boolean called;

    private StubResilienceManager() {
      super(new Config.LlmConfig());
    }

    @Override
    public <T> T execute(Callable<T> task) {
      called = true;
      try {
        return task.call();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static final class ThrowingStubResilienceManager extends ResilienceManager {
    private ThrowingStubResilienceManager() {
      super(new Config.LlmConfig());
    }

    @Override
    public <T> T execute(Callable<T> task) {
      throw new IllegalStateException("manager failure");
    }
  }
}
