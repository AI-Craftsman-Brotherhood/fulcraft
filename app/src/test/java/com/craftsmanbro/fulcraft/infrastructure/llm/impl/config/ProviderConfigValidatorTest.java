package com.craftsmanbro.fulcraft.infrastructure.llm.impl.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.config.ProviderConfigValidator.Level;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.config.ProviderConfigValidator.ValidationMessage;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderConfigValidatorTest {

  @Test
  void validate_deterministicWithSeed_noWarnings() {
    Config config = new Config();
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setDeterministic(true);
    config.setLlm(llmConfig);

    ProviderProfile profile =
        new ProviderProfile("openai", Set.of(Capability.SEED), Optional.empty());

    List<ValidationMessage> messages = ProviderConfigValidator.validate(config, profile);

    assertTrue(messages.isEmpty(), "Expected no validation messages");
  }

  @Test
  void validate_deterministicWithoutSeed_producesWarning() {
    Config config = new Config();
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setDeterministic(true);
    config.setLlm(llmConfig);

    ProviderProfile profile = new ProviderProfile("anthropic", Set.of(), Optional.empty());

    List<ValidationMessage> messages = ProviderConfigValidator.validate(config, profile);

    assertEquals(1, messages.size(), "Expected exactly one validation message");
    ValidationMessage msg = messages.get(0);
    assertEquals(Level.WARN, msg.level());
    assertTrue(msg.message().contains("anthropic"));
    assertTrue(msg.message().contains("SEED"));
  }

  @Test
  void validate_deterministicFalse_noWarnings() {
    Config config = new Config();
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setDeterministic(false);
    config.setLlm(llmConfig);

    ProviderProfile profile = new ProviderProfile("anthropic", Set.of(), Optional.empty());

    List<ValidationMessage> messages = ProviderConfigValidator.validate(config, profile);

    assertTrue(messages.isEmpty(), "Expected no validation messages when deterministic=false");
  }

  @Test
  void validate_deterministicWithTemperature_warnsAboutTemperature() {
    Config config = new Config();
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setDeterministic(true);
    llmConfig.setTemperature(0.7);
    config.setLlm(llmConfig);

    ProviderProfile profile =
        new ProviderProfile("openai", Set.of(Capability.SEED), Optional.empty());

    List<ValidationMessage> messages = ProviderConfigValidator.validate(config, profile);

    assertEquals(1, messages.size(), "Expected one warning about temperature");
    assertEquals(Level.WARN, messages.get(0).level());
    assertTrue(messages.get(0).message().toLowerCase(Locale.ROOT).contains("temperature"));
  }

  @Test
  void validate_deterministicWithTemperatureAndNoSeed_warnsTwice() {
    Config config = new Config();
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setDeterministic(true);
    llmConfig.setTemperature(0.5);
    config.setLlm(llmConfig);

    ProviderProfile profile = new ProviderProfile("anthropic", Set.of(), Optional.empty());

    List<ValidationMessage> messages = ProviderConfigValidator.validate(config, profile);

    assertEquals(2, messages.size(), "Expected two validation messages");
    assertTrue(messages.stream().anyMatch(msg -> msg.message().contains("temperature")));
    assertTrue(messages.stream().anyMatch(msg -> msg.message().contains("SEED")));
  }

  @Test
  void validate_systemMessageUnsupported_warns() {
    Config config = new Config();
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setDeterministic(false);
    llmConfig.setSystemMessage("You are helpful.");
    config.setLlm(llmConfig);

    ProviderProfile profile = new ProviderProfile("bedrock", Set.of(), Optional.empty());

    List<ValidationMessage> messages = ProviderConfigValidator.validate(config, profile);

    assertEquals(1, messages.size(), "Expected one validation message");
    assertEquals(Level.WARN, messages.get(0).level());
    assertTrue(messages.get(0).message().contains("SYSTEM_MESSAGE"));
  }

  @Test
  void validate_systemMessageSupported_noWarnings() {
    Config config = new Config();
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setDeterministic(false);
    llmConfig.setSystemMessage("You are helpful.");
    config.setLlm(llmConfig);

    ProviderProfile profile =
        new ProviderProfile("openai", Set.of(Capability.SYSTEM_MESSAGE), Optional.empty());

    List<ValidationMessage> messages = ProviderConfigValidator.validate(config, profile);

    assertTrue(messages.isEmpty(), "Expected no validation messages when supported");
  }

  @Test
  void validate_nullConfig_noMessages() {
    List<ValidationMessage> messages = ProviderConfigValidator.validate(null, null);
    assertTrue(messages.isEmpty());
  }

  @Test
  void validate_nullProfile_noMessages() {
    Config config = new Config();
    config.setLlm(new Config.LlmConfig());

    List<ValidationMessage> messages = ProviderConfigValidator.validate(config, null);

    assertTrue(messages.isEmpty());
  }

  @Test
  void validate_blankSystemMessage_noWarnings() {
    Config config = new Config();
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setDeterministic(false);
    llmConfig.setSystemMessage("   ");
    config.setLlm(llmConfig);

    ProviderProfile profile = new ProviderProfile("bedrock", Set.of(), Optional.empty());
    List<ValidationMessage> messages = ProviderConfigValidator.validate(config, profile);

    assertTrue(messages.isEmpty());
  }

  @Test
  void validate_nullLlmConfig_noMessages() {
    Config config = new Config(); // llm is null
    ProviderProfile profile = new ProviderProfile("test", Set.of(), Optional.empty());

    List<ValidationMessage> messages = ProviderConfigValidator.validate(config, profile);

    assertTrue(messages.isEmpty());
  }
}
