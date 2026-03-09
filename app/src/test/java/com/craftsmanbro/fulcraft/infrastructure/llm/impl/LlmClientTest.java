package com.craftsmanbro.fulcraft.infrastructure.llm.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import org.junit.jupiter.api.Test;

class LlmClientTest {

  @Test
  void isHealthy_defaultsToTrue() {
    LlmClientPort client =
        new LlmClientPort() {
          @Override
          public String generateTest(String prompt, Config.LlmConfig llmConfig) {
            return "";
          }

          @Override
          public ProviderProfile profile() {
            return new ProviderProfile(
                "test", java.util.Collections.emptySet(), java.util.Optional.empty());
          }
        };

    assertTrue(client.isHealthy());
  }

  @Test
  void generateTestLegacy_delegatesToConfigOverload() {
    var capturedConfig = new Config.LlmConfig[1];
    LlmClientPort client =
        new LlmClientPort() {
          @Override
          public String generateTest(String prompt, Config.LlmConfig llmConfig) {
            capturedConfig[0] = llmConfig;
            return "generated: " + prompt;
          }

          @Override
          public ProviderProfile profile() {
            return new ProviderProfile(
                "test", java.util.Collections.emptySet(), java.util.Optional.empty());
          }
        };

    String result = client.generateTest("test-prompt", "gpt-4");

    org.junit.jupiter.api.Assertions.assertEquals("generated: test-prompt", result);
    org.junit.jupiter.api.Assertions.assertNotNull(capturedConfig[0]);
    org.junit.jupiter.api.Assertions.assertEquals("gpt-4", capturedConfig[0].getModelName());
  }

  @Test
  void generateTestLegacy_throwsNPE_whenArgsNull() {
    LlmClientPort client =
        new LlmClientPort() {
          @Override
          public String generateTest(String prompt, Config.LlmConfig llmConfig) {
            return "";
          }

          @Override
          public ProviderProfile profile() {
            return new ProviderProfile(
                "test", java.util.Collections.emptySet(), java.util.Optional.empty());
          }
        };

    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class, () -> client.generateTest(null, "model"));
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class, () -> client.generateTest("prompt", (String) null));
  }

  @Test
  void generateTestLegacy_setsProviderFromProfile() {
    var capturedConfig = new Config.LlmConfig[1];
    LlmClientPort client =
        new LlmClientPort() {
          @Override
          public String generateTest(String prompt, Config.LlmConfig llmConfig) {
            capturedConfig[0] = llmConfig;
            return "ok";
          }

          @Override
          public ProviderProfile profile() {
            return new ProviderProfile(
                "provider-test", java.util.Collections.emptySet(), java.util.Optional.empty());
          }
        };

    client.generateTest("prompt", "model");

    org.junit.jupiter.api.Assertions.assertNotNull(capturedConfig[0]);
    org.junit.jupiter.api.Assertions.assertEquals("provider-test", capturedConfig[0].getProvider());
  }

  @Test
  void generateTestLegacy_keepsProviderNullWhenProfileMissing() {
    var capturedConfig = new Config.LlmConfig[1];
    LlmClientPort client =
        new LlmClientPort() {
          @Override
          public String generateTest(String prompt, Config.LlmConfig llmConfig) {
            capturedConfig[0] = llmConfig;
            return "ok";
          }

          @Override
          public ProviderProfile profile() {
            return null;
          }
        };

    client.generateTest("prompt", "model");

    org.junit.jupiter.api.Assertions.assertNotNull(capturedConfig[0]);
    org.junit.jupiter.api.Assertions.assertNull(capturedConfig[0].getProvider());
  }
}
