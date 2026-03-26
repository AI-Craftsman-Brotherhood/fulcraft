package com.craftsmanbro.fulcraft.infrastructure.llm.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.request.LlmRequestFactory;
import org.junit.jupiter.api.Test;

class LlmRequestFactoryTest {

  @Test
  void resolveParams_defaultsToDeterministicWhenConfigNull() {
    LlmRequestFactory.GenerationParams params = LlmRequestFactory.resolveParams(null);

    assertEquals(LlmRequestFactory.DETERMINISTIC_TEMPERATURE, params.temperature());
    assertNull(params.topP());
    assertEquals(LlmRequestFactory.DEFAULT_SEED, params.seed());
    assertNull(params.maxTokens());
  }

  @Test
  void resolveParams_deterministicForcesTemperatureAndDefaultSeed() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setDeterministic(true);
    config.setTemperature(0.9);
    config.setMaxTokens(256);

    LlmRequestFactory.GenerationParams params = LlmRequestFactory.resolveParams(config);

    assertEquals(LlmRequestFactory.DETERMINISTIC_TEMPERATURE, params.temperature());
    assertNull(params.topP());
    assertEquals(LlmRequestFactory.DEFAULT_SEED, params.seed());
    assertEquals(256, params.maxTokens());
  }

  @Test
  void resolveParams_nondeterministicUsesDefaultsWhenUnset() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setDeterministic(false);

    LlmRequestFactory.GenerationParams params = LlmRequestFactory.resolveParams(config);

    assertEquals(LlmRequestFactory.DEFAULT_TEMPERATURE, params.temperature());
    assertNull(params.topP());
    assertNull(params.seed());
    assertNull(params.maxTokens());
  }

  @Test
  void resolveParams_nondeterministicUsesConfiguredValues() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setDeterministic(false);
    config.setTemperature(0.7);
    config.setSeed(7);
    config.setMaxTokens(512);

    LlmRequestFactory.GenerationParams params = LlmRequestFactory.resolveParams(config);

    assertEquals(0.7, params.temperature());
    assertNull(params.topP());
    assertEquals(7, params.seed());
    assertEquals(512, params.maxTokens());
  }

  @Test
  void resolveParams_deterministicUsesConfiguredSeedWhenPresent() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setDeterministic(true);
    config.setSeed(99);
    config.setTemperature(0.8);

    LlmRequestFactory.GenerationParams params = LlmRequestFactory.resolveParams(config);

    assertEquals(LlmRequestFactory.DETERMINISTIC_TEMPERATURE, params.temperature());
    assertNull(params.topP());
    assertEquals(99, params.seed());
    assertNull(params.maxTokens());
  }

  @Test
  void resolveParams_treatsNullDeterministicAsNondeterministic() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setDeterministic(null);
    config.setSeed(13);

    LlmRequestFactory.GenerationParams params = LlmRequestFactory.resolveParams(config);

    assertEquals(LlmRequestFactory.DEFAULT_TEMPERATURE, params.temperature());
    assertNull(params.topP());
    assertEquals(13, params.seed());
    assertNull(params.maxTokens());
  }
}
