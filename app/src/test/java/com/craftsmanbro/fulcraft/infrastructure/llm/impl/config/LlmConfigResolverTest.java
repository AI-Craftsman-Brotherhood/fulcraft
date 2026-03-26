package com.craftsmanbro.fulcraft.infrastructure.llm.impl.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmConfigResolverTest {

  @Test
  void resolveForTask_prefersAnnotationOverrides() {
    Config.LlmConfig base = new Config.LlmConfig();
    base.setProvider("openai");
    base.setModelName("base-model");
    base.setRequestTimeout(60);

    TaskRecord task = new TaskRecord();
    task.setUtGenProvider("anthropic");
    task.setUtGenModel("override-model");
    task.setUtGenTimeoutSeconds(12);

    Config.LlmConfig resolved = LlmConfigResolver.resolveForTask(base, task);

    assertEquals("anthropic", resolved.getProvider());
    assertEquals("override-model", resolved.getModelName());
    assertEquals(12, resolved.getRequestTimeout());
    assertEquals("openai", base.getProvider());
    assertEquals("base-model", base.getModelName());
    assertEquals(60, base.getRequestTimeout());
  }

  @Test
  void resolveForTask_usesBaseWhenNoOverrides() {
    Config.LlmConfig base = new Config.LlmConfig();
    base.setProvider("openai");
    base.setModelName("base-model");
    base.setRequestTimeout(60);

    TaskRecord task = new TaskRecord();

    Config.LlmConfig resolved = LlmConfigResolver.resolveForTask(base, task);

    assertSame(base, resolved);
  }

  @Test
  void resolveForTaskWithGeneration_appliesGenerationThenTaskOverrides() {
    Config config = new Config();
    Config.LlmConfig base = new Config.LlmConfig();
    base.setProvider("openai");
    base.setModelName("base-model");
    base.setTemperature(0.2);
    base.setMaxTokens(100);
    base.setRequestTimeout(60);
    config.setLlm(base);

    Config.GenerationConfig generation = new Config.GenerationConfig();
    generation.setDefaultModel("gen-model");
    generation.setTemperature(0.7);
    generation.setMaxTokens(200);
    config.setGeneration(generation);

    TaskRecord task = new TaskRecord();
    task.setUtGenProvider("anthropic");
    task.setUtGenModel("task-model");
    task.setUtGenTimeoutSeconds(30);

    Config.LlmConfig resolved = LlmConfigResolver.resolveForTaskWithGeneration(config, task);

    assertNotSame(base, resolved);
    assertEquals("anthropic", resolved.getProvider());
    assertEquals("task-model", resolved.getModelName());
    assertEquals(30, resolved.getRequestTimeout());
    assertEquals(0.7, resolved.getTemperature(), 0.0001);
    assertEquals(200, resolved.getMaxTokens());

    assertEquals("openai", base.getProvider());
    assertEquals("base-model", base.getModelName());
    assertEquals(0.2, base.getTemperature(), 0.0001);
    assertEquals(100, base.getMaxTokens());
    assertEquals(60, base.getRequestTimeout());
  }

  @Test
  void resolveForTaskWithGeneration_returnsNullWhenConfigOrLlmIsMissing() {
    TaskRecord task = new TaskRecord();

    assertNull(LlmConfigResolver.resolveForTaskWithGeneration(null, task));

    Config config = new Config();
    assertNull(LlmConfigResolver.resolveForTaskWithGeneration(config, task));
  }

  @Test
  void resolveForTask_ignoresBlankOverrides() {
    Config.LlmConfig base = new Config.LlmConfig();
    base.setProvider("openai");
    base.setModelName("base-model");
    base.setRequestTimeout(60);

    TaskRecord task = new TaskRecord();
    task.setUtGenProvider(" ");
    task.setUtGenModel("");
    task.setUtGenTimeoutSeconds(0);

    Config.LlmConfig resolved = LlmConfigResolver.resolveForTask(base, task);

    assertSame(base, resolved);
  }

  @Test
  void resolveForTask_returnsNullWhenBaseIsNull() {
    TaskRecord task = new TaskRecord();
    task.setUtGenProvider("openai");

    assertNull(LlmConfigResolver.resolveForTask(null, task));
  }

  @Test
  void resolveForTask_usesBaseWhenTaskIsNull() {
    Config.LlmConfig base = new Config.LlmConfig();
    base.setProvider("openai");
    base.setModelName("base-model");
    base.setRequestTimeout(60);

    Config.LlmConfig resolved = LlmConfigResolver.resolveForTask(base, null);

    assertSame(base, resolved);
  }

  @Test
  void resolveForTaskWithGeneration_ignoresBlankDefaultModel() {
    Config config = new Config();
    Config.LlmConfig base = new Config.LlmConfig();
    base.setModelName("base-model");
    config.setLlm(base);

    Config.GenerationConfig generation = new Config.GenerationConfig();
    generation.setDefaultModel("  ");
    generation.setTemperature(0.9);
    generation.setMaxTokens(320);
    config.setGeneration(generation);

    Config.LlmConfig resolved = LlmConfigResolver.resolveForTaskWithGeneration(config, null);

    assertNotSame(base, resolved);
    assertEquals("base-model", resolved.getModelName());
    assertEquals(0.9, resolved.getTemperature(), 0.0001);
    assertEquals(320, resolved.getMaxTokens());
  }

  @Test
  void resolveForTaskWithGeneration_copiesMutableCollectionsDeeply() {
    Config config = new Config();
    Config.LlmConfig base = new Config.LlmConfig();
    base.setProvider("openai");

    List<String> allowedProviders = new ArrayList<>(List.of("openai"));
    base.setAllowedProviders(allowedProviders);

    List<String> openAiModels = new ArrayList<>(List.of("gpt-4o"));
    Map<String, List<String>> allowedModels = new HashMap<>();
    allowedModels.put("openai", openAiModels);
    base.setAllowedModels(allowedModels);

    base.setCustomHeaders(Map.of("Authorization", "Bearer token-a"));
    config.setLlm(base);

    Config.LlmConfig resolved = LlmConfigResolver.resolveForTaskWithGeneration(config, null);

    allowedProviders.add("anthropic");
    openAiModels.add("gpt-5");
    allowedModels.put("anthropic", new ArrayList<>(List.of("claude-3")));
    base.setCustomHeaders(
        Map.of("Authorization", "Bearer token-b", "X-Request-Id", "request-id-1"));

    assertEquals(List.of("openai"), resolved.getAllowedProviders());
    assertEquals(List.of("gpt-4o"), resolved.getAllowedModels().get("openai"));
    assertEquals(1, resolved.getAllowedModels().size());
    assertEquals("Bearer token-a", resolved.getCustomHeaders().get("Authorization"));
    assertNull(resolved.getCustomHeaders().get("X-Request-Id"));
  }
}
