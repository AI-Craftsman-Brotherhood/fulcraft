package com.craftsmanbro.fulcraft.infrastructure.llm.decorator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.ExternalTransmissionDeniedException;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmGovernanceEnforcingClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for LlmGovernanceEnforcingClient governance gate functionality.
 *
 * <p>Verifies that external LLM transmission is blocked when governance.external_transmission is
 * set to 'deny'.
 */
class LlmGovernanceEnforcingClientTest {

  private LlmClientPort mockClient;

  @BeforeEach
  void setUp() {
    mockClient = mock(LlmClientPort.class);
    when(mockClient.profile()).thenReturn(new ProviderProfile("mock", Set.of(), Optional.empty()));
    when(mockClient.generateTest(anyString(), any(Config.LlmConfig.class)))
        .thenReturn("generated code");
  }

  @Nested
  @DisplayName("When governance.external_transmission = deny")
  class WhenExternalTransmissionDenied {

    private Config.GovernanceConfig createDenyConfig() {
      var config = new Config.GovernanceConfig();
      config.setExternalTransmission("deny");
      return config;
    }

    @ParameterizedTest
    @ValueSource(strings = {"gemini", "openai", "anthropic", "azure-openai", "vertex", "bedrock"})
    @DisplayName("Should block external providers")
    void shouldBlockExternalProviders(String provider) {
      // Arrange
      var adapter = new LlmGovernanceEnforcingClient(mockClient, createDenyConfig());
      var llmConfig = new Config.LlmConfig();
      llmConfig.setProvider(provider);

      // Act & Assert
      var exception =
          assertThrows(
              ExternalTransmissionDeniedException.class,
              () -> adapter.generateTest("test prompt", llmConfig));

      assertTrue(exception.getMessage().contains("deny"));
      assertTrue(exception.getMessage().contains(provider));

      // Verify NO interaction with the actual LLM client
      verify(mockClient, never()).generateTest(anyString(), any(Config.LlmConfig.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "mock", "ollama"})
    @DisplayName("Should allow local providers")
    void shouldAllowLocalProviders(String provider) {
      // Arrange
      var adapter = new LlmGovernanceEnforcingClient(mockClient, createDenyConfig());
      var llmConfig = new Config.LlmConfig();
      llmConfig.setProvider(provider);

      // Act
      String result = adapter.generateTest("test prompt", llmConfig);

      // Assert
      assertNotNull(result);
      verify(mockClient).generateTest(eq("test prompt"), any(Config.LlmConfig.class));
    }

    @Test
    @DisplayName("Should block when provider is null (assumes external)")
    void shouldBlockWhenProviderIsNull() {
      // Arrange
      var adapter = new LlmGovernanceEnforcingClient(mockClient, createDenyConfig());
      var llmConfig = new Config.LlmConfig();
      llmConfig.setProvider(null);

      // Act & Assert
      assertThrows(
          ExternalTransmissionDeniedException.class,
          () -> adapter.generateTest("test prompt", llmConfig));

      verify(mockClient, never()).generateTest(anyString(), any(Config.LlmConfig.class));
    }

    @Test
    @DisplayName("Should block unknown provider by default")
    void shouldBlockUnknownProvider() {
      var adapter = new LlmGovernanceEnforcingClient(mockClient, createDenyConfig());
      var llmConfig = new Config.LlmConfig();
      llmConfig.setProvider("unknown-provider");

      assertThrows(
          ExternalTransmissionDeniedException.class,
          () -> adapter.generateTest("test prompt", llmConfig));

      verify(mockClient, never()).generateTest(anyString(), any(Config.LlmConfig.class));
    }
  }

  @Nested
  @DisplayName("When governance.external_transmission = allow")
  class WhenExternalTransmissionAllowed {

    private Config.GovernanceConfig createAllowConfig() {
      var config = new Config.GovernanceConfig();
      config.setExternalTransmission("allow");
      return config;
    }

    @ParameterizedTest
    @ValueSource(strings = {"gemini", "openai", "anthropic", "azure-openai", "vertex", "bedrock"})
    @DisplayName("Should allow external providers")
    void shouldAllowExternalProviders(String provider) {
      // Arrange
      var adapter = new LlmGovernanceEnforcingClient(mockClient, createAllowConfig());
      var llmConfig = new Config.LlmConfig();
      llmConfig.setProvider(provider);

      // Act
      String result = adapter.generateTest("test prompt", llmConfig);

      // Assert
      assertNotNull(result);
      verify(mockClient).generateTest(eq("test prompt"), any(Config.LlmConfig.class));
    }
  }

  @Nested
  @DisplayName("When governance config is null (default behavior)")
  class WhenGovernanceConfigIsNull {

    @Test
    @DisplayName("Should allow all providers (default is allow)")
    void shouldAllowAllProviders() {
      // Arrange - using constructor without governance config
      var adapter = new LlmGovernanceEnforcingClient(mockClient, null);
      var llmConfig = new Config.LlmConfig();
      llmConfig.setProvider("gemini");

      // Act
      String result = adapter.generateTest("test prompt", llmConfig);

      // Assert
      assertNotNull(result);
      verify(mockClient).generateTest(anyString(), any(Config.LlmConfig.class));
    }
  }

  @Test
  void generateTest_throwsWhenPromptIsNull() {
    var adapter = new LlmGovernanceEnforcingClient(mockClient, null);
    var llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");

    assertThrows(NullPointerException.class, () -> adapter.generateTest(null, llmConfig));
  }

  @Test
  void generateTest_throwsWhenConfigIsNull() {
    var adapter = new LlmGovernanceEnforcingClient(mockClient, null);

    assertThrows(
        NullPointerException.class, () -> adapter.generateTest("prompt", (Config.LlmConfig) null));
  }

  @Test
  void delegatesHealthProfileAndClearContext() {
    when(mockClient.isHealthy()).thenReturn(false);
    ProviderProfile profile = new ProviderProfile("delegated", Set.of(), Optional.empty());
    when(mockClient.profile()).thenReturn(profile);
    var adapter = new LlmGovernanceEnforcingClient(mockClient, null);

    assertFalse(adapter.isHealthy());
    assertSame(profile, adapter.profile());

    adapter.clearContext();

    verify(mockClient).clearContext();
  }
}
