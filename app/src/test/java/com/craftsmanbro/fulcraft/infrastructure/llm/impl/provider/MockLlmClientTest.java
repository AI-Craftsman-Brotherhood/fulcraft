package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import org.junit.jupiter.api.Test;

class MockLlmClientTest {

  @Test
  void generateTest_includesPackageClassAndModelTag() {
    String prompt = "package com.example.demo;\n\nclass SampleService {}";
    LlmClientPort client = new MockLlmClient();

    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setModelName("custom-model");
    String generated = client.generateTest(prompt, llmConfig);

    assertTrue(generated.startsWith("package com.example.demo;"));
    assertTrue(generated.contains("public class SampleService {"));
    assertTrue(generated.contains("model=custom-model"));
  }

  @Test
  void generateTest_usesFallbackNameWhenClassMissing() {
    String prompt = "No cls declaration here, only context.";
    String expectedClassName = "GeneratedTest_" + Integer.toHexString(prompt.hashCode());
    LlmClientPort client = new MockLlmClient();

    String generated = client.generateTest(prompt, (Config.LlmConfig) null);

    assertTrue(generated.contains("public class " + expectedClassName + " {"));
    assertTrue(generated.contains("model=mock"));
  }

  @Test
  void generateTest_prefersExplicitTestNameHint() {
    String prompt = "Test class name: CustomHintTest";
    LlmClientPort client = new MockLlmClient();

    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setModelName("");
    String generated = client.generateTest(prompt, llmConfig);

    assertTrue(generated.contains("public class CustomHintTest {"));
    assertFalse(generated.startsWith("package"));
  }

  @Test
  void generateTest_prefersRequiredTestClassNameRule() {
    String prompt =
        """
        OUTPUT RULES:
        - Test class name MUST be exactly: RequiredNameTest
        class SampleService {}
        """;
    LlmClientPort client = new MockLlmClient();

    String generated = client.generateTest(prompt, (Config.LlmConfig) null);

    assertTrue(generated.contains("public class RequiredNameTest {"));
  }

  @Test
  void generateTest_usesProviderAsModelTagWhenModelNameIsBlank() {
    String prompt = "class SampleService {}";
    LlmClientPort client = new MockLlmClient();

    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setModelName(" ");
    llmConfig.setProvider("vertexai");

    String generated = client.generateTest(prompt, llmConfig);

    assertTrue(generated.contains("model=vertexai"));
  }

  @Test
  void generateTest_handlesNullPromptWithDefaultClassAndModel() {
    LlmClientPort client = new MockLlmClient();

    String generated = client.generateTest(null, (Config.LlmConfig) null);

    assertTrue(generated.contains("public class GeneratedTest {"));
    assertTrue(generated.contains("model=mock"));
    assertFalse(generated.startsWith("package"));
  }

  @Test
  void profile_reportsMockProviderWithSeedCapability() {
    MockLlmClient client = new MockLlmClient();

    assertEquals("mock", client.profile().providerName());
    assertTrue(
        client
            .profile()
            .supports(com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability.SEED));
  }

  @Test
  void generateTest_fallsBackToMockModelWhenConfigHasNoModelAndBlankProvider() {
    LlmClientPort client = new MockLlmClient();
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setModelName(null);
    llmConfig.setProvider(" ");

    String generated = client.generateTest("class Sample {}", llmConfig);

    assertTrue(generated.contains("model=mock"));
  }
}
